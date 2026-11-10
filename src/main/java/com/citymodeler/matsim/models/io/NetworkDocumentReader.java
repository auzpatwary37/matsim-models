package com.citymodeler.matsim.models.io;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.validation.NetworkValidator;
import com.citymodeler.matsim.models.validation.ValidationIssue;

/**
 * Reads a network document together with lane definitions embedded in the
 * same file, keeping network and embedded-lane diagnostics separate.
 *
 * <p>Accepted embedded containers (explicit list, not a recursive search):
 * {@code <network>/<laneDefinitions>},
 * {@code <network>/<lanes>/<laneDefinitions>}, and
 * {@code <network>/<lanes>/<assignment>} (historic models format). Multiple
 * competing lane blocks are INVALID; there is no first-block-wins behavior.
 * These are accepted containers, not a claim that embedded lanes are standard
 * MATSim network XML.</p>
 *
 * <p>Error model: unreadable file, malformed whole XML, or an unconstructible
 * network throws {@link BundleReadException} with structured issues;
 * recognizable-but-invalid lane content in well-formed XML returns as lane
 * ERROR issues. Semantic network defects are returned in
 * {@code NetworkBundle.networkIssues()} — the bundle is then not
 * installation-ready.</p>
 */
public final class NetworkDocumentReader {

    public NetworkBundle readBundle(Path path) {
        Document document;
        try {
            if (!Files.isReadable(path)) {
                throw new BundleReadException("Could not read " + path, null,
                        List.of(ioIssue(path, "File does not exist or is not readable")));
            }
            document = XmlSupport.parse(path);
        } catch (MatsimParseException exception) {
            throw new BundleReadException("Could not read bundle from " + path, exception,
                    List.of(ioIssue(path, rootMessage(exception))));
        }
        Element root = document.getDocumentElement();

        Network network;
        List<ValidationIssue> networkIssues = new ArrayList<>();
        try {
            network = new NetworkXmlReader().read(root);
        } catch (MatsimParseException | NumberFormatException exception) {
            throw new BundleReadException("Could not construct network from " + path,
                    exception, List.of(ioIssue(path, rootMessage(exception))));
        }
        networkIssues.addAll(NetworkValidator.validate(network).getIssues());

        LaneReadResult embedded = readEmbedded(root, path, network);
        return new NetworkBundle(path, network, networkIssues, embedded);
    }

    /** Reads an embedded lane block from an already-parsed document root. */
    static LaneReadResult readEmbedded(Element networkRoot, Path source, Network network) {
        List<Element> directEnvelopes = XmlSupport.children(networkRoot, "laneDefinitions");
        List<Element> wrappedBlocks = new ArrayList<>();
        for (Element lanesWrapper : XmlSupport.children(networkRoot, "lanes")) {
            List<Element> envelopes = XmlSupport.children(lanesWrapper, "laneDefinitions");
            if (envelopes.isEmpty() && !XmlSupport.children(lanesWrapper, "assignment").isEmpty()) {
                wrappedBlocks.add(lanesWrapper);
            } else {
                wrappedBlocks.addAll(envelopes);
            }
        }

        List<Element> laneBlocks = new ArrayList<>(directEnvelopes);
        laneBlocks.addAll(wrappedBlocks);

        if (laneBlocks.isEmpty()) {
            return LaneReadResult.absent();
        }
        if (laneBlocks.size() > 1) {
            String xmlPath = "/" + XmlSupport.localName(networkRoot) + "[1]";
            ValidationIssue issue = LaneEnvelopeReader.error("lanes.competing_blocks",
                    "Document contains multiple competing lane blocks; exactly one is allowed",
                    null, com.citymodeler.matsim.models.validation.SourceLocation
                            .of(source, xmlPath), null, null);
            return LaneReadResult.invalid(null,
                    com.citymodeler.matsim.models.validation.SourceLocation
                            .of(source, xmlPath), List.of(issue));
        }

        String rootPath = "/" + XmlSupport.localName(networkRoot) + "[1]";
        Element block = laneBlocks.get(0);
        String blockName = XmlSupport.localName(block);
        if ("laneDefinitions".equals(blockName)) {
            Element parentBlock = (Element) block.getParentNode();
            String parentPath = parentBlock == networkRoot
                    ? rootPath
                    : rootPath + "/lanes[" + occurrenceIndex(parentBlock, networkRoot) + "]";
            return LaneEnvelopeReader.readEnvelope(block, source, network,
                    parentPath + "/laneDefinitions[" + occurrenceIndex(block, parentBlock) + "]");
        }
        return LanesModelFormat.parse(block, source, network,
                rootPath + "/lanes[" + occurrenceIndex(block, networkRoot) + "]");
    }

    /** 1-based occurrence index of {@code element} among same-name siblings. */
    private static int occurrenceIndex(Element element, Element parent) {
        String name = XmlSupport.localName(element);
        int index = 0;
        for (Element sibling : XmlSupport.children(parent, name)) {
            index++;
            if (sibling == element) {
                return index;
            }
        }
        return Math.max(1, index);
    }

    private static ValidationIssue ioIssue(Path path, String message) {
        return new ValidationIssue(
                com.citymodeler.matsim.models.validation.ValidationSeverity.ERROR,
                "io", "io.read_failed", message, null, null,
                com.citymodeler.matsim.models.validation.SourceLocation.of(path, "/"),
                null, null);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable cause = throwable.getCause();
        String message = throwable.getMessage();
        return cause != null && cause.getMessage() != null
                ? message + ": " + cause.getMessage()
                : message;
    }
}

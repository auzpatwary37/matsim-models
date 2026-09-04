package com.citymodeler.matsim.models.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.citymodeler.matsim.models.config.Config;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.Node;

/**
 * Pins the compatibility decision: legacy MATSIM files that carry a DOCTYPE
 * declaration (e.g. {@code <network>} files shipped with
 * {@code <!DOCTYPE network SYSTEM "./dtd/network_v1.dtd">}) ARE supported.
 * The declaration is stripped before the hardened parser, while external DTD
 * loading and entity expansion remain disabled.
 */
class XmlSupportLegacyDoctypeTest {

    private static final String NETWORK_BODY = """
            <network name="legacy-net">
                <nodes>
                    <node id="n1" x="1.0" y="2.0"/>
                    <node id="n2" x="3.0" y="4.0"/>
                </nodes>
                <links>
                    <link id="l1" from="n1" to="n2" length="100.0" capacity="900.0" freespeed="13.9" permlanes="1.0"/>
                </links>
            </network>
            """;

    @TempDir
    Path tempDir;

    @Test
    void legacyNetworkWithSystemDoctypeParses() {
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<!DOCTYPE network SYSTEM \"./dtd/network_v1.dtd\">\n"
                + NETWORK_BODY;

        Network network = new NetworkXmlReader().read(xml);

        assertEquals(2, network.getNodes().size());
        assertEquals(1, network.getLinks().size());
        Link link = network.getLinks().values().iterator().next();
        assertEquals(100.0, link.getLength(), 1e-9);
    }

    @Test
    void legacyNetworkFileOnDiskWithDoctypeParses() throws IOException {
        Path file = tempDir.resolve("legacy-network.xml");
        Files.writeString(file,
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<!DOCTYPE network SYSTEM \"./dtd/network_v1.dtd\">\n"
                        + NETWORK_BODY);

        Network network = new NetworkXmlReader().read(file);

        assertEquals(2, network.getNodes().size());
        assertEquals(1, network.getLinks().size());
    }

    @Test
    void legacyConfigWithDoctypeParses() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE config SYSTEM \"http://www.matsim.org/files/dtd/config_v1.dtd\">\n"
                + "<config>\n"
                + "    <module name=\"timeAllocation\">\n"
                + "        <param name=\"maxiter\" value=\"3\"/>\n"
                + "    </module>\n"
                + "</config>\n";

        Config config = new ConfigXmlReader().readString(xml);

        assertEquals("3", config.getModule("timeAllocation")
                .orElseThrow()
                .getParam("maxiter")
                .orElseThrow());
    }

    @Test
    void doctypeAfterCommentAndProcessingInstructionParses() {
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<!-- legacy export -->\n"
                + "<?matsim exporter=\"1.0\"?>\n"
                + "<!DOCTYPE network SYSTEM \"./dtd/network_v1.dtd\">\n"
                + NETWORK_BODY;

        Network network = new NetworkXmlReader().read(xml);

        assertEquals(1, network.getLinks().size());
    }

    @Test
    void lowercaseDoctypeKeywordParses() {
        String xml = "<!doctype network system \"./dtd/network_v1.dtd\">\n" + NETWORK_BODY;

        Network network = new NetworkXmlReader().read(xml);

        assertEquals(1, network.getLinks().size());
    }

    @Test
    void doctypeWithPublicIdentifierParses() {
        String xml = "<!DOCTYPE network PUBLIC \"-//MATSim//DTD network 1.0//EN\" \"network_v1.dtd\">\n"
                + NETWORK_BODY;

        Network network = new NetworkXmlReader().read(xml);

        assertEquals(1, network.getLinks().size());
    }

    @Test
    void schemaValidationPassesForLegacyDoctype() {
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<!DOCTYPE network SYSTEM \"./dtd/network_v1.dtd\">\n"
                + NETWORK_BODY;

        Network network = new NetworkXmlReader(true).read(xml);

        assertEquals(2, network.getNodes().size());
    }

    @Test
    void xxeInternalSubsetDoctypeIsRejectedLoudly() {
        String xml = "<!DOCTYPE network [\n"
                + "    <!ENTITY xxe SYSTEM \"file:///etc/passwd\">\n"
                + "]>\n"
                + NETWORK_BODY.replace("name=\"legacy-net\"", "name=\"&xxe;\"");

        MatsimParseException exception = assertThrows(MatsimParseException.class,
                () -> new NetworkXmlReader().read(xml));

        String message = exception.getMessage().toLowerCase(java.util.Locale.ROOT);
        assertTrue(message.contains("dtd"), "rejection must name the DTD/DOCTYPE problem, was: " + message);
        org.junit.jupiter.api.Assertions.assertFalse(
                exception.getMessage().contains("root:x"),
                "must not leak external file contents");
    }

    @Test
    void unterminatedDoctypeDeclarationIsRejectedLoudly() {
        String xml = "<!DOCTYPE network SYSTEM \"./dtd/network_v1.dtd\n" + NETWORK_BODY;

        MatsimParseException exception = assertThrows(MatsimParseException.class,
                () -> new NetworkXmlReader().read(xml));

        String message = exception.getMessage().toLowerCase(java.util.Locale.ROOT);
        assertTrue(message.contains("doctype"), "rejection must mention the malformed DOCTYPE, was: " + message);
    }
}

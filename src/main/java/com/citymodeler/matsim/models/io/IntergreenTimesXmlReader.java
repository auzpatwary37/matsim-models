package com.citymodeler.matsim.models.io;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.w3c.dom.Element;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.signals.SignalGroup;
import com.citymodeler.matsim.models.signals.SignalSystem;
import com.citymodeler.matsim.models.signals.timings.IntergreenTimes;
import com.citymodeler.matsim.models.signals.timings.SystemIntergreens;

/**
 * Reads the published MATSim {@code intergreenTimes_v1.0} shape: an
 * {@code intergreenTimes} root holding {@code signalSystem} entries (by
 * {@code refId}), each with {@code endingSignalGroup} elements (by
 * {@code refId}) whose {@code beginningSignalGroup} children carry
 * {@code refId} and {@code timeSeconds}.
 *
 * <p>Unmodeled child elements are not preserved; they are reported through
 * {@link #getUnknownElements()} as
 * {@code intergreenTimes:<parentElement>:<childName>} so callers can surface
 * them as warnings instead of silently trusting them. The list is cleared at
 * the start of every read.</p>
 */
public final class IntergreenTimesXmlReader {
    private static final String SCHEMA = "/schemas/intergreenTimes.xsd";
    private static final String FAMILY = "intergreenTimes";

    private final boolean validateSchema;
    private final List<String> unknownElements = new ArrayList<>();

    public IntergreenTimesXmlReader() {
        this(false);
    }

    public IntergreenTimesXmlReader(boolean validateSchema) {
        this.validateSchema = validateSchema;
    }

    public IntergreenTimes read(Path path) {
        return read((validateSchema ? XmlSupport.parse(path, SCHEMA) : XmlSupport.parse(path)).getDocumentElement());
    }

    public IntergreenTimes read(InputStream inputStream) {
        return read((validateSchema ? XmlSupport.parse(inputStream, SCHEMA) : XmlSupport.parse(inputStream)).getDocumentElement());
    }

    public IntergreenTimes read(String xml) {
        return read((validateSchema ? XmlSupport.parse(xml, SCHEMA) : XmlSupport.parse(xml)).getDocumentElement());
    }

    public List<String> getUnknownElements() {
        return Collections.unmodifiableList(unknownElements);
    }

    private IntergreenTimes read(Element root) {
        unknownElements.clear();
        if (!FAMILY.equals(root.getTagName())) {
            throw new IllegalArgumentException("expected <" + FAMILY + "> root element, got <"
                    + root.getTagName() + ">");
        }
        IntergreenTimes times = new IntergreenTimes();
        recordUnknown(root, "intergreenTimes", List.of("signalSystem"));
        for (Element systemElement : XmlSupport.children(root, "signalSystem")) {
            recordUnknown(systemElement, "signalSystem",
                    List.of("attributes", "endingSignalGroup"));
            SystemIntergreens system = new SystemIntergreens(
                    Id.create(XmlSupport.attr(systemElement, "refId"), SignalSystem.class));
            XmlSupport.readAttributes(systemElement, system.getAttributes());
            for (Element endingElement : XmlSupport.children(systemElement, "endingSignalGroup")) {
                recordUnknown(endingElement, "endingSignalGroup", List.of("beginningSignalGroup"));
                Id<SignalGroup> ending =
                        Id.create(XmlSupport.attr(endingElement, "refId"), SignalGroup.class);
                for (Element beginningElement : XmlSupport.children(endingElement, "beginningSignalGroup")) {
                    recordUnknown(beginningElement, "beginningSignalGroup", List.of());
                    Id<SignalGroup> beginning =
                            Id.create(XmlSupport.attr(beginningElement, "refId"), SignalGroup.class);
                    int sec = Integer.parseInt(XmlSupport.attr(beginningElement, "timeSeconds"));
                    system.setEndingToBeginningSec(ending, beginning, sec);
                }
            }
            times.addSystem(system);
        }
        return times;
    }

    private void recordUnknown(Element parent, String parentName, List<String> knownChildren) {
        for (Element child : XmlSupport.children(parent, null)) {
            if (!knownChildren.contains(child.getTagName())) {
                unknownElements.add(FAMILY + ":" + parentName + ":" + child.getTagName());
            }
        }
    }
}

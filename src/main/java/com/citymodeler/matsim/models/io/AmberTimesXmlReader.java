package com.citymodeler.matsim.models.io;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.w3c.dom.Element;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.signals.Signal;
import com.citymodeler.matsim.models.signals.SignalSystem;
import com.citymodeler.matsim.models.signals.timings.AmberIntervals;
import com.citymodeler.matsim.models.signals.timings.AmberTimes;
import com.citymodeler.matsim.models.signals.timings.SystemAmberTimes;

/**
 * Reads the published MATSim {@code amberTimes_v1.0} shape: an
 * {@code amberTimes} root with an optional {@code globalDefaults}
 * matsimObjectType ({@code redAmber}/{@code amber} children with
 * {@code seconds} attributes) and per-system {@code signalSystem} entries (by
 * {@code refId}) carrying {@code systemDefaults} and per-SIGNAL
 * {@code signal} overrides.
 *
 * <p>Unmodeled child elements are not preserved; they are reported through
 * {@link #getUnknownElements()} as {@code amberTimes:<parentElement>:<childName>}
 * so callers can surface them as warnings instead of silently trusting them.
 * The list is cleared at the start of every read.</p>
 */
public final class AmberTimesXmlReader {
    private static final String SCHEMA = "/schemas/amberTimes.xsd";
    private static final String FAMILY = "amberTimes";

    private final boolean validateSchema;
    private final List<String> unknownElements = new ArrayList<>();

    public AmberTimesXmlReader() {
        this(false);
    }

    public AmberTimesXmlReader(boolean validateSchema) {
        this.validateSchema = validateSchema;
    }

    public AmberTimes read(Path path) {
        return read((validateSchema ? XmlSupport.parse(path, SCHEMA) : XmlSupport.parse(path)).getDocumentElement());
    }

    public AmberTimes read(InputStream inputStream) {
        return read((validateSchema ? XmlSupport.parse(inputStream, SCHEMA) : XmlSupport.parse(inputStream)).getDocumentElement());
    }

    public AmberTimes read(String xml) {
        return read((validateSchema ? XmlSupport.parse(xml, SCHEMA) : XmlSupport.parse(xml)).getDocumentElement());
    }

    public List<String> getUnknownElements() {
        return Collections.unmodifiableList(unknownElements);
    }

    private AmberTimes read(Element root) {
        unknownElements.clear();
        if (!FAMILY.equals(root.getTagName())) {
            throw new IllegalArgumentException("expected <" + FAMILY + "> root element, got <"
                    + root.getTagName() + ">");
        }
        AmberTimes times = new AmberTimes();
        recordUnknown(root, "amberTimes", List.of("attributes", "globalDefaults", "signalSystem"));
        XmlSupport.readAttributes(root, times.getAttributes());
        Element globalDefaults = XmlSupport.child(root, "globalDefaults");
        if (globalDefaults != null) {
            times.setGlobalDefaults(readIntervals(globalDefaults, "globalDefaults"));
        }
        for (Element systemElement : XmlSupport.children(root, "signalSystem")) {
            recordUnknown(systemElement, "signalSystem",
                    List.of("attributes", "systemDefaults", "signal"));
            SystemAmberTimes system = new SystemAmberTimes(
                    Id.create(XmlSupport.attr(systemElement, "refId"), SignalSystem.class));
            XmlSupport.readAttributes(systemElement, system.getAttributes());
            Element systemDefaults = XmlSupport.child(systemElement, "systemDefaults");
            if (systemDefaults != null) {
                system.setSystemDefaults(readIntervals(systemDefaults, "systemDefaults"));
            }
            for (Element signalElement : XmlSupport.children(systemElement, "signal")) {
                AmberIntervals override = readIntervals(signalElement, "signal");
                system.addSignalOverride(
                        Id.create(XmlSupport.attr(signalElement, "refId"), Signal.class), override);
            }
            times.addSystem(system);
        }
        return times;
    }

    private AmberIntervals readIntervals(Element parent, String parentName) {
        recordUnknown(parent, parentName, List.of("attributes", "redAmber", "amber"));
        AmberIntervals intervals = new AmberIntervals(
                readSeconds(parent, "redAmber"), readSeconds(parent, "amber"));
        XmlSupport.readAttributes(parent, intervals.getAttributes());
        return intervals;
    }

    private Integer readSeconds(Element parent, String name) {
        Element element = XmlSupport.child(parent, name);
        if (element == null) {
            return null;
        }
        return Integer.parseInt(XmlSupport.attr(element, "seconds"));
    }

    private void recordUnknown(Element parent, String parentName, List<String> knownChildren) {
        for (Element child : XmlSupport.children(parent, null)) {
            if (!knownChildren.contains(child.getTagName())) {
                unknownElements.add(FAMILY + ":" + parentName + ":" + child.getTagName());
            }
        }
    }
}

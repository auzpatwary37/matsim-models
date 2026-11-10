package com.citymodeler.matsim.models.io;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Element;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.lanes.Lane;
import com.citymodeler.matsim.models.signals.Signal;
import com.citymodeler.matsim.models.signals.SignalSystem;

/**
 * Reads the published MATSim {@code signalSystems_v2.0} shape: a
 * {@code signalSystems} root holding {@code signalSystem} entries, each with a
 * {@code signals} container of {@code signal} elements (required
 * {@code linkIdRef}) carrying optional {@code lane} references and a
 * {@code turningMoveRestrictions} block of {@code toLink} references.
 *
 * <p>Unmodeled child elements are not preserved; they are reported through
 * {@link #getUnknownElements()} as
 * {@code signalSystems:<parentElement>:<childName>} so callers can surface them
 * as warnings instead of silently trusting them. The list is cleared at the
 * start of every read, so it always reflects the most recent parse.</p>
 */
public final class SignalSystemsXmlReader {
    private static final String SCHEMA = "/schemas/signalSystems.xsd";
    private static final String FAMILY = "signalSystems";

    private final boolean validateSchema;
    private final List<String> unknownElements = new ArrayList<>();

    public SignalSystemsXmlReader() {
        this(false);
    }

    public SignalSystemsXmlReader(boolean validateSchema) {
        this.validateSchema = validateSchema;
    }

    public Map<Id<SignalSystem>, SignalSystem> read(Path path) {
        return read((validateSchema ? XmlSupport.parse(path, SCHEMA) : XmlSupport.parse(path)).getDocumentElement());
    }

    public Map<Id<SignalSystem>, SignalSystem> read(InputStream inputStream) {
        return read((validateSchema ? XmlSupport.parse(inputStream, SCHEMA) : XmlSupport.parse(inputStream)).getDocumentElement());
    }

    public Map<Id<SignalSystem>, SignalSystem> read(String xml) {
        return read((validateSchema ? XmlSupport.parse(xml, SCHEMA) : XmlSupport.parse(xml)).getDocumentElement());
    }

    public List<String> getUnknownElements() {
        return Collections.unmodifiableList(unknownElements);
    }

    private Map<Id<SignalSystem>, SignalSystem> read(Element root) {
        unknownElements.clear();
        Map<Id<SignalSystem>, SignalSystem> systems = new LinkedHashMap<>();
        for (Element systemElement : XmlSupport.children(root, "signalSystem")) {
            recordUnknown(systemElement, "signalSystem", List.of("attributes", "signals"));
            SignalSystem system = new SignalSystem(
                    Id.create(XmlSupport.attr(systemElement, "id"), SignalSystem.class));
            XmlSupport.readAttributes(systemElement, system.getAttributes());
            Element signalsElement = XmlSupport.child(systemElement, "signals");
            if (signalsElement != null) {
                recordUnknown(signalsElement, "signals", List.of("signal"));
                for (Element signalElement : XmlSupport.children(signalsElement, "signal")) {
                    recordUnknown(signalElement, "signal",
                            List.of("attributes", "lane", "turningMoveRestrictions"));
                    Signal signal = new Signal(
                            Id.create(XmlSupport.attr(signalElement, "id"), Signal.class),
                            Id.createLinkId(XmlSupport.attr(signalElement, "linkIdRef")));
                    for (Element laneElement : XmlSupport.children(signalElement, "lane")) {
                        signal.addLaneRef(Id.create(XmlSupport.attr(laneElement, "idRef"), Lane.class));
                    }
                    Element restrictions = XmlSupport.child(signalElement, "turningMoveRestrictions");
                    if (restrictions != null) {
                        recordUnknown(restrictions, "turningMoveRestrictions", List.of("toLink"));
                        for (Element toLink : XmlSupport.children(restrictions, "toLink")) {
                            signal.getRestrictions().addToLink(
                                    Id.createLinkId(XmlSupport.attr(toLink, "idRef")));
                        }
                    }
                    XmlSupport.readAttributes(signalElement, signal.getAttributes());
                    system.addSignal(signal);
                }
            }
            systems.put(system.getId(), system);
        }
        recordUnknown(root, "signalSystems", List.of("signalSystem"));
        return systems;
    }

    private void recordUnknown(Element parent, String parentName, List<String> knownChildren) {
        for (Element child : XmlSupport.children(parent, null)) {
            if (!knownChildren.contains(child.getTagName())) {
                unknownElements.add(FAMILY + ":" + parentName + ":" + child.getTagName());
            }
        }
    }
}

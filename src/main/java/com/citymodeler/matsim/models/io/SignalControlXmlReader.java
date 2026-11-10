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
import com.citymodeler.matsim.models.signals.SignalGroup;
import com.citymodeler.matsim.models.signals.SignalSystem;
import com.citymodeler.matsim.models.signals.control.ControlledSignalSystem;
import com.citymodeler.matsim.models.signals.control.SignalGroupSettings;
import com.citymodeler.matsim.models.signals.control.SignalPlan;

/**
 * Reads the published MATSim {@code signalControl_v2.0} shape: a
 * {@code signalControl} root holding {@code signalSystem} entries (by
 * {@code refId}), each with an optional {@code signalSystemController}
 * carrying a {@code controllerIdentifier} and file-ordered {@code signalPlan}
 * elements (optional {@code id}; {@code start}/{@code stop} daytime,
 * {@code cycleTime}/{@code offset} seconds; {@code signalGroupSettings} with
 * flat {@code onset}/{@code dropping} seconds — stage synthesis is
 * projection-side). The returned map IS the file root; plans may be id-less so
 * they stay a LIST.
 *
 * <p>Unmodeled child elements are not preserved; they are reported through
 * {@link #getUnknownElements()} as
 * {@code signalControl:<parentElement>:<childName>} so callers can surface
 * them as warnings instead of silently trusting them. The list is cleared at
 * the start of every read, so it always reflects the most recent parse.</p>
 */
public final class SignalControlXmlReader {
    private static final String SCHEMA = "/schemas/signalControl.xsd";
    private static final String FAMILY = "signalControl";

    private final boolean validateSchema;
    private final List<String> unknownElements = new ArrayList<>();

    public SignalControlXmlReader() {
        this(false);
    }

    public SignalControlXmlReader(boolean validateSchema) {
        this.validateSchema = validateSchema;
    }

    public Map<Id<SignalSystem>, ControlledSignalSystem> read(Path path) {
        return read((validateSchema ? XmlSupport.parse(path, SCHEMA) : XmlSupport.parse(path)).getDocumentElement());
    }

    public Map<Id<SignalSystem>, ControlledSignalSystem> read(InputStream inputStream) {
        return read((validateSchema ? XmlSupport.parse(inputStream, SCHEMA) : XmlSupport.parse(inputStream)).getDocumentElement());
    }

    public Map<Id<SignalSystem>, ControlledSignalSystem> read(String xml) {
        return read((validateSchema ? XmlSupport.parse(xml, SCHEMA) : XmlSupport.parse(xml)).getDocumentElement());
    }

    public List<String> getUnknownElements() {
        return Collections.unmodifiableList(unknownElements);
    }

    private Map<Id<SignalSystem>, ControlledSignalSystem> read(Element root) {
        unknownElements.clear();
        if (!FAMILY.equals(root.getTagName())) {
            throw new IllegalArgumentException("expected <" + FAMILY + "> root element, got <"
                    + root.getTagName() + ">");
        }
        Map<Id<SignalSystem>, ControlledSignalSystem> bySystem = new LinkedHashMap<>();
        for (Element systemElement : XmlSupport.children(root, "signalSystem")) {
            recordUnknown(systemElement, "signalSystem", List.of("attributes", "signalSystemController"));
            ControlledSignalSystem system = new ControlledSignalSystem(
                    Id.create(XmlSupport.attr(systemElement, "refId"), SignalSystem.class));
            XmlSupport.readAttributes(systemElement, system.getAttributes());
            Element controller = XmlSupport.child(systemElement, "signalSystemController");
            if (controller != null) {
                system.setControllerPresent(true);
                recordUnknown(controller, "signalSystemController",
                        List.of("controllerIdentifier", "signalPlan"));
                Element identifier = XmlSupport.child(controller, "controllerIdentifier");
                if (identifier != null) {
                    system.setControllerIdentifier(identifier.getTextContent().trim());
                }
                for (Element planElement : XmlSupport.children(controller, "signalPlan")) {
                    recordUnknown(planElement, "signalPlan",
                            List.of("attributes", "start", "stop", "cycleTime", "offset", "signalGroupSettings"));
                    SignalPlan plan = new SignalPlan();
                    plan.setId(XmlSupport.attr(planElement, "id"));
                    Element start = XmlSupport.child(planElement, "start");
                    if (start != null) {
                        plan.setStartDaytime(XmlSupport.attr(start, "daytime"));
                    }
                    Element stop = XmlSupport.child(planElement, "stop");
                    if (stop != null) {
                        plan.setStopDaytime(XmlSupport.attr(stop, "daytime"));
                    }
                    Element cycleTime = XmlSupport.child(planElement, "cycleTime");
                    if (cycleTime != null) {
                        plan.setCycleTimeSec(Integer.parseInt(XmlSupport.attr(cycleTime, "sec")));
                    }
                    Element offset = XmlSupport.child(planElement, "offset");
                    if (offset != null) {
                        plan.setOffsetSec(Integer.parseInt(XmlSupport.attr(offset, "sec")));
                    }
                    XmlSupport.readAttributes(planElement, plan.getAttributes());
                    for (Element settingsElement : XmlSupport.children(planElement, "signalGroupSettings")) {
                        recordUnknown(settingsElement, "signalGroupSettings",
                                List.of("attributes", "onset", "dropping"));
                        int onset = Integer.parseInt(
                                XmlSupport.attr(XmlSupport.child(settingsElement, "onset"), "sec"));
                        int dropping = Integer.parseInt(
                                XmlSupport.attr(XmlSupport.child(settingsElement, "dropping"), "sec"));
                        SignalGroupSettings settings = new SignalGroupSettings(
                                Id.create(XmlSupport.attr(settingsElement, "refId"), SignalGroup.class),
                                onset, dropping);
                        XmlSupport.readAttributes(settingsElement, settings.getAttributes());
                        plan.addSetting(settings);
                    }
                    system.addPlan(plan);
                }
            }
            bySystem.put(system.getId(), system);
        }
        recordUnknown(root, "signalControl", List.of("signalSystem"));
        return bySystem;
    }

    private void recordUnknown(Element parent, String parentName, List<String> knownChildren) {
        for (Element child : XmlSupport.children(parent, null)) {
            if (!knownChildren.contains(child.getTagName())) {
                unknownElements.add(FAMILY + ":" + parentName + ":" + child.getTagName());
            }
        }
    }
}

package com.citymodeler.matsim.models.io;

import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.signals.SignalGroup;
import com.citymodeler.matsim.models.signals.SignalSystem;
import com.citymodeler.matsim.models.signals.control.ControlledSignalSystem;
import com.citymodeler.matsim.models.signals.control.SignalGroupSettings;
import com.citymodeler.matsim.models.signals.control.SignalPlan;

/**
 * Writes signal control in the published MATSim {@code signalControl_v2.0}
 * wire shape: a {@code signalControl} root in the
 * {@code http://www.matsim.org/files/dtd} namespace declaring the
 * {@code signalControl_v2.0.xsd} schema location, holding
 * {@code signalSystem} elements (attribute {@code refId}) with an optional
 * {@code signalSystemController} whose {@code controllerIdentifier} is written
 * as element text followed by file-ordered {@code signalPlan} elements.
 * Optional plan children ({@code start}/{@code stop} daytime,
 * {@code cycleTime}/{@code offset} seconds) are written only when present, and
 * each {@code signalGroupSettings} carries flat {@code onset}/{@code
 * dropping} second children.
 *
 * <p>Map and plan-list iteration order is preserved for lossless
 * round-tripping; the element names and structure follow the published schema
 * and no MATSim code is used.</p>
 */
public final class SignalControlXmlWriter {
    static final String MATSIM_NAMESPACE = "http://www.matsim.org/files/dtd";
    static final String SCHEMA_LOCATION =
            MATSIM_NAMESPACE + " " + MATSIM_NAMESPACE + "/signalControl_v2.0.xsd";

    public void write(Map<Id<SignalSystem>, ControlledSignalSystem> control, Path path) {
        XmlSupport.write(document(control), path);
    }

    public void write(Map<Id<SignalSystem>, ControlledSignalSystem> control, OutputStream outputStream) {
        XmlSupport.write(document(control), outputStream);
    }

    public String writeToString(Map<Id<SignalSystem>, ControlledSignalSystem> control) {
        return XmlSupport.writeToString(document(control));
    }

    private Document document(Map<Id<SignalSystem>, ControlledSignalSystem> control) {
        Document document = XmlSupport.newDocument();
        Element root = document.createElementNS(MATSIM_NAMESPACE, "signalControl");
        root.setAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "xsi:schemaLocation",
                SCHEMA_LOCATION);
        document.appendChild(root);
        for (ControlledSignalSystem system : control.values()) {
            Element systemElement = document.createElementNS(MATSIM_NAMESPACE, "signalSystem");
            systemElement.setAttribute("refId", system.getId().toString());
            XmlSupport.appendAttributes(document, systemElement, system.getAttributes(), MATSIM_NAMESPACE);
            root.appendChild(systemElement);
            if (system.getControllerIdentifier() != null || !system.getPlans().isEmpty()) {
                Element controller = document.createElementNS(MATSIM_NAMESPACE, "signalSystemController");
                if (system.getControllerIdentifier() != null) {
                    Element identifier = document.createElementNS(MATSIM_NAMESPACE, "controllerIdentifier");
                    identifier.setTextContent(system.getControllerIdentifier());
                    controller.appendChild(identifier);
                }
                for (SignalPlan plan : system.getPlans()) {
                    controller.appendChild(planElement(document, plan));
                }
                systemElement.appendChild(controller);
            }
        }
        return document;
    }

    private Element planElement(Document document, SignalPlan plan) {
        Element planElement = document.createElementNS(MATSIM_NAMESPACE, "signalPlan");
        XmlSupport.setIfPresent(planElement, "id", plan.getId());
        if (plan.getStartDaytime() != null) {
            Element start = document.createElementNS(MATSIM_NAMESPACE, "start");
            start.setAttribute("daytime", plan.getStartDaytime());
            planElement.appendChild(start);
        }
        if (plan.getStopDaytime() != null) {
            Element stop = document.createElementNS(MATSIM_NAMESPACE, "stop");
            stop.setAttribute("daytime", plan.getStopDaytime());
            planElement.appendChild(stop);
        }
        if (plan.getCycleTimeSec() != null) {
            Element cycleTime = document.createElementNS(MATSIM_NAMESPACE, "cycleTime");
            cycleTime.setAttribute("sec", String.valueOf(plan.getCycleTimeSec()));
            planElement.appendChild(cycleTime);
        }
        if (plan.getOffsetSec() != null) {
            Element offset = document.createElementNS(MATSIM_NAMESPACE, "offset");
            offset.setAttribute("sec", String.valueOf(plan.getOffsetSec()));
            planElement.appendChild(offset);
        }
        for (SignalGroupSettings settings : plan.getSettings().values()) {
            Element settingsElement = document.createElementNS(MATSIM_NAMESPACE, "signalGroupSettings");
            settingsElement.setAttribute("refId", settings.getSignalGroupRef().toString());
            Element onset = document.createElementNS(MATSIM_NAMESPACE, "onset");
            onset.setAttribute("sec", String.valueOf(settings.getOnsetSec()));
            settingsElement.appendChild(onset);
            Element dropping = document.createElementNS(MATSIM_NAMESPACE, "dropping");
            dropping.setAttribute("sec", String.valueOf(settings.getDroppingSec()));
            settingsElement.appendChild(dropping);
            XmlSupport.appendAttributes(document, settingsElement, settings.getAttributes(), MATSIM_NAMESPACE);
            planElement.appendChild(settingsElement);
        }
        XmlSupport.appendAttributes(document, planElement, plan.getAttributes(), MATSIM_NAMESPACE);
        return planElement;
    }
}

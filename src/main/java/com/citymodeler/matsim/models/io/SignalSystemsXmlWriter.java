package com.citymodeler.matsim.models.io;

import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.lanes.Lane;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.signals.Signal;
import com.citymodeler.matsim.models.signals.SignalSystem;

/**
 * Writes signal systems in the published MATSim {@code signalSystems_v2.0}
 * wire shape: a {@code signalSystems} root in the
 * {@code http://www.matsim.org/files/dtd} namespace declaring the
 * {@code signalSystems_v2.0.xsd} schema location, containing
 * {@code signalSystem} elements. Each system holds a {@code signals} container
 * of {@code signal} elements (attributes {@code id} and required
 * {@code linkIdRef}), optional {@code lane} references ({@code idRef}), an
 * optional non-empty {@code turningMoveRestrictions} block of {@code toLink}
 * references ({@code idRef}), and per-level {@code <attributes>} containers.
 *
 * <p>Map iteration order is preserved for lossless round-tripping; the element
 * names and structure follow the published schema and no MATSim code is
 * used.</p>
 */
public final class SignalSystemsXmlWriter {
    static final String MATSIM_NAMESPACE = "http://www.matsim.org/files/dtd";
    static final String SCHEMA_LOCATION =
            MATSIM_NAMESPACE + " " + MATSIM_NAMESPACE + "/signalSystems_v2.0.xsd";

    public void write(Map<Id<SignalSystem>, SignalSystem> systems, Path path) {
        XmlSupport.write(document(systems), path);
    }

    public void write(Map<Id<SignalSystem>, SignalSystem> systems, OutputStream outputStream) {
        XmlSupport.write(document(systems), outputStream);
    }

    public String writeToString(Map<Id<SignalSystem>, SignalSystem> systems) {
        return XmlSupport.writeToString(document(systems));
    }

    private Document document(Map<Id<SignalSystem>, SignalSystem> systems) {
        Document document = XmlSupport.newDocument();
        Element root = document.createElementNS(MATSIM_NAMESPACE, "signalSystems");
        root.setAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "xsi:schemaLocation",
                SCHEMA_LOCATION);
        document.appendChild(root);
        for (SignalSystem system : systems.values()) {
            Element systemElement = document.createElementNS(MATSIM_NAMESPACE, "signalSystem");
            systemElement.setAttribute("id", system.getId().toString());
            XmlSupport.appendAttributes(document, systemElement, system.getAttributes(), MATSIM_NAMESPACE);
            root.appendChild(systemElement);

            Element signalsElement = document.createElementNS(MATSIM_NAMESPACE, "signals");
            systemElement.appendChild(signalsElement);
            for (Signal signal : system.getSignals().values()) {
                Element signalElement = document.createElementNS(MATSIM_NAMESPACE, "signal");
                signalElement.setAttribute("id", signal.getId().toString());
                signalElement.setAttribute("linkIdRef", signal.getLinkIdRef().toString());
                for (Id<Lane> laneRef : signal.getLaneRefs()) {
                    Element laneElement = document.createElementNS(MATSIM_NAMESPACE, "lane");
                    laneElement.setAttribute("idRef", laneRef.toString());
                    signalElement.appendChild(laneElement);
                }
                if (!signal.getRestrictions().getToLinks().isEmpty()) {
                    Element restrictions = document.createElementNS(MATSIM_NAMESPACE, "turningMoveRestrictions");
                    for (Id<Link> toLink : signal.getRestrictions().getToLinks()) {
                        Element toLinkElement = document.createElementNS(MATSIM_NAMESPACE, "toLink");
                        toLinkElement.setAttribute("idRef", toLink.toString());
                        restrictions.appendChild(toLinkElement);
                    }
                    signalElement.appendChild(restrictions);
                }
                XmlSupport.appendAttributes(document, signalElement, signal.getAttributes(), MATSIM_NAMESPACE);
                signalsElement.appendChild(signalElement);
            }
        }
        return document;
    }
}

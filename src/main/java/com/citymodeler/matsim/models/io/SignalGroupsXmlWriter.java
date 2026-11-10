package com.citymodeler.matsim.models.io;

import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.signals.Signal;
import com.citymodeler.matsim.models.signals.SignalGroup;
import com.citymodeler.matsim.models.signals.SignalSystem;
import com.citymodeler.matsim.models.signals.SignalSystemGroups;

/**
 * Writes signal groups in the published MATSim {@code signalGroups_v2.0} wire
 * shape: a {@code signalGroups} root in the
 * {@code http://www.matsim.org/files/dtd} namespace declaring the
 * {@code signalGroups_v2.0.xsd} schema location, containing
 * {@code signalSystem} elements (attribute {@code refId}) holding
 * {@code signalGroup} elements (attribute {@code id}) whose members are
 * {@code signal} reference elements (attribute {@code refId}) — membership is
 * signal refs only, no lanes. Per-level {@code <attributes>} containers are
 * preserved for system and group elements.
 *
 * <p>Map iteration order is preserved for lossless round-tripping; the element
 * names and structure follow the published schema and no MATSim code is
 * used.</p>
 */
public final class SignalGroupsXmlWriter {
    static final String MATSIM_NAMESPACE = "http://www.matsim.org/files/dtd";
    static final String SCHEMA_LOCATION =
            MATSIM_NAMESPACE + " " + MATSIM_NAMESPACE + "/signalGroups_v2.0.xsd";

    public void write(Map<Id<SignalSystem>, SignalSystemGroups> groups, Path path) {
        XmlSupport.write(document(groups), path);
    }

    public void write(Map<Id<SignalSystem>, SignalSystemGroups> groups, OutputStream outputStream) {
        XmlSupport.write(document(groups), outputStream);
    }

    public String writeToString(Map<Id<SignalSystem>, SignalSystemGroups> groups) {
        return XmlSupport.writeToString(document(groups));
    }

    private Document document(Map<Id<SignalSystem>, SignalSystemGroups> groups) {
        Document document = XmlSupport.newDocument();
        Element root = document.createElementNS(MATSIM_NAMESPACE, "signalGroups");
        root.setAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "xsi:schemaLocation",
                SCHEMA_LOCATION);
        document.appendChild(root);
        for (SignalSystemGroups systemGroups : groups.values()) {
            Element systemElement = document.createElementNS(MATSIM_NAMESPACE, "signalSystem");
            systemElement.setAttribute("refId", systemGroups.getId().toString());
            XmlSupport.appendAttributes(document, systemElement, systemGroups.getAttributes(), MATSIM_NAMESPACE);
            root.appendChild(systemElement);
            for (SignalGroup group : systemGroups.getGroups().values()) {
                Element groupElement = document.createElementNS(MATSIM_NAMESPACE, "signalGroup");
                groupElement.setAttribute("id", group.getId().toString());
                for (Id<Signal> signalRef : group.getSignalRefIds()) {
                    Element signalElement = document.createElementNS(MATSIM_NAMESPACE, "signal");
                    signalElement.setAttribute("refId", signalRef.toString());
                    groupElement.appendChild(signalElement);
                }
                XmlSupport.appendAttributes(document, groupElement, group.getAttributes(), MATSIM_NAMESPACE);
                systemElement.appendChild(groupElement);
            }
        }
        return document;
    }
}

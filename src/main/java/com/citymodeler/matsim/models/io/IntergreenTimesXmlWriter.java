package com.citymodeler.matsim.models.io;

import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.signals.SignalGroup;
import com.citymodeler.matsim.models.signals.timings.IntergreenTimes;
import com.citymodeler.matsim.models.signals.timings.SystemIntergreens;

/**
 * Writes intergreen times in the published MATSim {@code intergreenTimes_v1.0}
 * wire shape: an {@code intergreenTimes} root in the
 * {@code http://www.matsim.org/files/dtd} namespace declaring the
 * {@code intergreenTimes_v1.0.xsd} schema location, holding
 * {@code signalSystem} elements (attribute {@code refId}) whose
 * {@code endingSignalGroup} elements (attribute {@code refId}) contain
 * {@code beginningSignalGroup} elements (attributes {@code refId} and
 * {@code timeSeconds}). System-level matsimObjectType {@code <attributes>} are
 * preserved.
 *
 * <p>System, row, and column iteration order is preserved for lossless
 * round-tripping; the element names and structure follow the published schema
 * and no MATSim code is used.</p>
 */
public final class IntergreenTimesXmlWriter {
    static final String MATSIM_NAMESPACE = "http://www.matsim.org/files/dtd";
    static final String SCHEMA_LOCATION =
            MATSIM_NAMESPACE + " " + MATSIM_NAMESPACE + "/intergreenTimes_v1.0.xsd";

    public void write(IntergreenTimes times, Path path) {
        XmlSupport.write(document(times), path);
    }

    public void write(IntergreenTimes times, OutputStream outputStream) {
        XmlSupport.write(document(times), outputStream);
    }

    public String writeToString(IntergreenTimes times) {
        return XmlSupport.writeToString(document(times));
    }

    private Document document(IntergreenTimes times) {
        Document document = XmlSupport.newDocument();
        Element root = document.createElementNS(MATSIM_NAMESPACE, "intergreenTimes");
        root.setAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "xsi:schemaLocation",
                SCHEMA_LOCATION);
        document.appendChild(root);
        for (SystemIntergreens system : times.getSystems().values()) {
            Element systemElement = document.createElementNS(MATSIM_NAMESPACE, "signalSystem");
            systemElement.setAttribute("refId", system.getId().toString());
            XmlSupport.appendAttributes(document, systemElement, system.getAttributes(), MATSIM_NAMESPACE);
            for (Map.Entry<Id<SignalGroup>, Map<Id<SignalGroup>, Integer>> row
                    : system.getEndingToBeginningSec().entrySet()) {
                Element endingElement = document.createElementNS(MATSIM_NAMESPACE, "endingSignalGroup");
                endingElement.setAttribute("refId", row.getKey().toString());
                for (Map.Entry<Id<SignalGroup>, Integer> cell : row.getValue().entrySet()) {
                    Element beginningElement =
                            document.createElementNS(MATSIM_NAMESPACE, "beginningSignalGroup");
                    beginningElement.setAttribute("refId", cell.getKey().toString());
                    beginningElement.setAttribute("timeSeconds", String.valueOf(cell.getValue()));
                    endingElement.appendChild(beginningElement);
                }
                systemElement.appendChild(endingElement);
            }
            root.appendChild(systemElement);
        }
        return document;
    }
}

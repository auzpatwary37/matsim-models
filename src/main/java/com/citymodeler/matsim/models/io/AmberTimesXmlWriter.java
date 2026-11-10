package com.citymodeler.matsim.models.io;

import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.signals.Signal;
import com.citymodeler.matsim.models.signals.timings.AmberIntervals;
import com.citymodeler.matsim.models.signals.timings.AmberTimes;
import com.citymodeler.matsim.models.signals.timings.SystemAmberTimes;

/**
 * Writes amber times in the published MATSim {@code amberTimes_v1.0} wire
 * shape: an {@code amberTimes} root in the
 * {@code http://www.matsim.org/files/dtd} namespace declaring the
 * {@code amberTimes_v1.0.xsd} schema location, with {@code globalDefaults}
 * written first when present, then per-system {@code signalSystem} elements
 * (attribute {@code refId}) holding {@code systemDefaults} and per-signal
 * {@code signal} elements. Interval children are written {@code redAmber}
 * then {@code amber} (attribute {@code seconds}), only when the value is
 * present, followed by matsimObjectType {@code <attributes>} containers at
 * every level.
 *
 * <p>System and override iteration order is preserved for lossless
 * round-tripping; the element names and structure follow the published schema
 * and no MATSim code is used.</p>
 */
public final class AmberTimesXmlWriter {
    static final String MATSIM_NAMESPACE = "http://www.matsim.org/files/dtd";
    static final String SCHEMA_LOCATION =
            MATSIM_NAMESPACE + " " + MATSIM_NAMESPACE + "/amberTimes_v1.0.xsd";

    public void write(AmberTimes times, Path path) {
        XmlSupport.write(document(times), path);
    }

    public void write(AmberTimes times, OutputStream outputStream) {
        XmlSupport.write(document(times), outputStream);
    }

    public String writeToString(AmberTimes times) {
        return XmlSupport.writeToString(document(times));
    }

    private Document document(AmberTimes times) {
        Document document = XmlSupport.newDocument();
        Element root = document.createElementNS(MATSIM_NAMESPACE, "amberTimes");
        root.setAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "xsi:schemaLocation",
                SCHEMA_LOCATION);
        document.appendChild(root);
        XmlSupport.appendAttributes(document, root, times.getAttributes(), MATSIM_NAMESPACE);
        if (times.getGlobalDefaults() != null) {
            root.appendChild(intervalsElement(document, "globalDefaults", times.getGlobalDefaults()));
        }
        for (SystemAmberTimes system : times.getSystems().values()) {
            Element systemElement = document.createElementNS(MATSIM_NAMESPACE, "signalSystem");
            systemElement.setAttribute("refId", system.getId().toString());
            XmlSupport.appendAttributes(document, systemElement, system.getAttributes(), MATSIM_NAMESPACE);
            if (system.getSystemDefaults() != null) {
                systemElement.appendChild(
                        intervalsElement(document, "systemDefaults", system.getSystemDefaults()));
            }
            for (Map.Entry<Id<Signal>, AmberIntervals> entry : system.getSignalOverrides().entrySet()) {
                Element signalElement = intervalsElement(document, "signal", entry.getValue());
                signalElement.setAttribute("refId", entry.getKey().toString());
                systemElement.appendChild(signalElement);
            }
            root.appendChild(systemElement);
        }
        return document;
    }

    private Element intervalsElement(Document document, String name, AmberIntervals intervals) {
        Element element = document.createElementNS(MATSIM_NAMESPACE, name);
        appendSeconds(document, element, "redAmber", intervals.getRedAmberSec());
        appendSeconds(document, element, "amber", intervals.getAmberSec());
        XmlSupport.appendAttributes(document, element, intervals.getAttributes(), MATSIM_NAMESPACE);
        return element;
    }

    private void appendSeconds(Document document, Element parent, String name, Integer seconds) {
        if (seconds == null) {
            return;
        }
        Element child = document.createElementNS(MATSIM_NAMESPACE, name);
        child.setAttribute("seconds", String.valueOf(seconds));
        parent.appendChild(child);
    }
}

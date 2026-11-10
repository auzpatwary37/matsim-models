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
import com.citymodeler.matsim.models.signals.Signal;
import com.citymodeler.matsim.models.signals.SignalGroup;
import com.citymodeler.matsim.models.signals.SignalSystem;
import com.citymodeler.matsim.models.signals.SignalSystemGroups;

/**
 * Reads the published MATSim {@code signalGroups_v2.0} shape: a
 * {@code signalGroups} root holding {@code signalSystem} entries (by
 * {@code refId}), each containing {@code signalGroup} elements whose members
 * are {@code signal} references only — membership carries no lane data.
 *
 * <p>Unmodeled child elements are not preserved; they are reported through
 * {@link #getUnknownElements()} as
 * {@code signalGroups:<parentElement>:<childName>} so callers can surface them
 * as warnings instead of silently trusting them. The list is cleared at the
 * start of every read, so it always reflects the most recent parse.</p>
 */
public final class SignalGroupsXmlReader {
    private static final String SCHEMA = "/schemas/signalGroups.xsd";
    private static final String FAMILY = "signalGroups";

    private final boolean validateSchema;
    private final List<String> unknownElements = new ArrayList<>();

    public SignalGroupsXmlReader() {
        this(false);
    }

    public SignalGroupsXmlReader(boolean validateSchema) {
        this.validateSchema = validateSchema;
    }

    public Map<Id<SignalSystem>, SignalSystemGroups> read(Path path) {
        return read((validateSchema ? XmlSupport.parse(path, SCHEMA) : XmlSupport.parse(path)).getDocumentElement());
    }

    public Map<Id<SignalSystem>, SignalSystemGroups> read(InputStream inputStream) {
        return read((validateSchema ? XmlSupport.parse(inputStream, SCHEMA) : XmlSupport.parse(inputStream)).getDocumentElement());
    }

    public Map<Id<SignalSystem>, SignalSystemGroups> read(String xml) {
        return read((validateSchema ? XmlSupport.parse(xml, SCHEMA) : XmlSupport.parse(xml)).getDocumentElement());
    }

    public List<String> getUnknownElements() {
        return Collections.unmodifiableList(unknownElements);
    }

    private Map<Id<SignalSystem>, SignalSystemGroups> read(Element root) {
        unknownElements.clear();
        if (!FAMILY.equals(root.getTagName())) {
            throw new IllegalArgumentException("expected <" + FAMILY + "> root element, got <"
                    + root.getTagName() + ">");
        }
        Map<Id<SignalSystem>, SignalSystemGroups> bySystem = new LinkedHashMap<>();
        for (Element systemElement : XmlSupport.children(root, "signalSystem")) {
            recordUnknown(systemElement, "signalSystem", List.of("attributes", "signalGroup"));
            SignalSystemGroups groups = new SignalSystemGroups(
                    Id.create(XmlSupport.attr(systemElement, "refId"), SignalSystem.class));
            XmlSupport.readAttributes(systemElement, groups.getAttributes());
            for (Element groupElement : XmlSupport.children(systemElement, "signalGroup")) {
                recordUnknown(groupElement, "signalGroup", List.of("attributes", "signal"));
                SignalGroup group = new SignalGroup(
                        Id.create(XmlSupport.attr(groupElement, "id"), SignalGroup.class));
                for (Element signalElement : XmlSupport.children(groupElement, "signal")) {
                    group.addSignalRef(Id.create(XmlSupport.attr(signalElement, "refId"), Signal.class));
                }
                XmlSupport.readAttributes(groupElement, group.getAttributes());
                groups.addGroup(group);
            }
            bySystem.put(groups.getId(), groups);
        }
        recordUnknown(root, "signalGroups", List.of("signalSystem"));
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

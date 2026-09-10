package com.citymodeler.matsim.models.osm;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Objects;

import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.osm.model.OsmNodeRecord;
import com.citymodeler.matsim.models.osm.model.OsmRelationRecord;
import com.citymodeler.matsim.models.osm.model.OsmWayRecord;

public final class OsmImportResult {
    private final Map<String, OsmNodeRecord> nodes;
    private final Map<String, OsmWayRecord> ways;
    private final Map<String, OsmRelationRecord> relations;
    private final List<OsmImportIssue> issues;
    private final OsmProvenance provenance;

    public OsmImportResult(
            Map<String, OsmNodeRecord> nodes,
            Map<String, OsmWayRecord> ways,
            Map<String, OsmRelationRecord> relations,
            List<OsmImportIssue> issues,
            OsmProvenance provenance) {
        this.nodes = immutableCopy(nodes);
        this.ways = immutableCopy(ways);
        this.relations = immutableCopy(relations);
        this.issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
        this.provenance = Objects.requireNonNull(provenance, "provenance");
    }

    public Map<String, OsmNodeRecord> nodes() {
        return nodes;
    }

    public Map<String, OsmWayRecord> ways() {
        return ways;
    }

    public Map<String, OsmRelationRecord> relations() {
        return relations;
    }

    public List<OsmImportIssue> issues() {
        return issues;
    }

    public OsmProvenance provenance() {
        return provenance;
    }

    public void applyProvenanceTo(Network network) {
        Objects.requireNonNull(network, "network");
        network.getAttributes().putAttribute("osm:source", provenance.sourceName());
        network.getAttributes().putAttribute("osm:license", provenance.sourceLicense());
        network.getAttributes().putAttribute("osm:attribution", provenance.attributionText());
        network.getAttributes().putAttribute("osm:input", provenance.inputName());
        network.getAttributes().putAttribute("osm:targetCrs", provenance.targetCrs());
        network.getAttributes().putAttribute("osm:geometryMode", provenance.geometryMode());
        network.getAttributes().putAttribute("osm:rawTagsKept", provenance.rawTagsKept());
        network.getAttributes().putAttribute("osm:importedAt", provenance.importedAt().toString());
    }

    private static <K extends Comparable<K>, V> Map<K, V> immutableCopy(Map<K, V> source) {
        Objects.requireNonNull(source, "source");
        return Collections.unmodifiableSortedMap(new TreeMap<>(source));
    }
}

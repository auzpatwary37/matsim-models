package com.citymodeler.matsim.models.osm.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.turnrestrictions.DisallowedNextLinks;
import com.citymodeler.matsim.models.network.turnrestrictions.TurnRestrictionIndex;
import com.citymodeler.matsim.models.osm.OsmImportIssue;
import com.citymodeler.matsim.models.osm.OsmIssueSeverity;
import com.citymodeler.matsim.models.osm.OsmImportResult;
import com.citymodeler.matsim.models.osm.OsmElementType;
import com.citymodeler.matsim.models.osm.model.OsmRelationMemberRecord;
import com.citymodeler.matsim.models.osm.model.OsmRelationRecord;
import com.citymodeler.matsim.models.osm.model.OsmWayRecord;

/**
 * Reads OSM restriction relations and produces a TurnRestrictionIndex
 * plus DisallowedNextLinks attributes on affected links.
 */
public final class OsmTurnRestrictionReader {

    private OsmTurnRestrictionReader() {
    }

    public static Record read(
            OsmImportResult importResult,
            OsmNetworkBuildResult buildResult) {

        Map<String, List<String>> linkIdsByWay = buildResult.linkIdsByOsmWayId();
        var network = buildResult.cleanedNetwork();
        List<OsmImportIssue> issues = new ArrayList<>();
        TurnRestrictionIndex index = new TurnRestrictionIndex();
        Map<String, DisallowedNextLinks> perLink = new HashMap<>();

        for (OsmRelationRecord rel : importResult.relations().values()) {
            String type = rel.tags().get("type");
            if (!"restriction".equals(type) && !"keep_right".equals(type) && !"keep_left".equals(type)) {
                continue;
            }

            String restriction = rel.tags().get("restriction");
            if (restriction == null) continue;

            String fromWayId = null, viaNodeId = null, toWayId = null;
            for (OsmRelationMemberRecord member : rel.members()) {
                switch (member.role()) {
                    case "from" -> { if (member.type() == OsmElementType.WAY) fromWayId = member.ref(); }
                    case "via" -> { if (member.type() == OsmElementType.NODE) viaNodeId = member.ref(); }
                    case "to" -> { if (member.type() == OsmElementType.WAY) toWayId = member.ref(); }
                    default -> { }
                }
            }
            if (fromWayId == null || toWayId == null) {
                issues.add(new OsmImportIssue(OsmIssueSeverity.WARNING,
                        "restriction-incomplete",
                        "Restriction relation " + rel.id() + " missing from/via/to", null));
                continue;
            }

            // Resolve from-link: the forward link of fromWay that ends at viaNode
            String fromLinkId = resolveFromLink(fromWayId, viaNodeId, linkIdsByWay);
            String toLinkId = resolveToLink(toWayId, viaNodeId, linkIdsByWay);
            if (fromLinkId == null || toLinkId == null) {
                issues.add(new OsmImportIssue(OsmIssueSeverity.WARNING,
                        "restriction-unresolved",
                        "Could not resolve links for restriction " + rel.id(), null));
                continue;
            }

            // Determine affected modes from the from-way's rule
            OsmWayRecord fromWay = importResult.ways().get(fromWayId);
            Set<String> modes = Set.of("car");
            if (fromWay != null) {
                var rule = OsmNetworkBuildConfig.defaultConfig().resolveRule(fromWay.tags());
                if (rule != null) modes = rule.allowedModes();
            }

            for (String mode : modes) {
                index.addRestriction(fromLinkId, toLinkId, mode);
                DisallowedNextLinks current = perLink.getOrDefault(fromLinkId, DisallowedNextLinks.empty());
                perLink.put(fromLinkId, current.plus(mode, List.of(toLinkId)));
            }

            // Attach attribute to the from-link
            var fromLinkIdObj = com.citymodeler.matsim.models.api.Id.create(fromLinkId, Link.class);
            Link fromLink = network.getLinks().get(fromLinkIdObj);
            if (fromLink != null) {
                fromLink.getAttributes().putAttribute("disallowedNextLinks",
                        perLink.get(fromLinkId).toString());
            }
        }

        return new Record(index, perLink, issues);
    }

    private static String resolveFromLink(String wayId, String viaNodeId,
                                           Map<String, List<String>> linkIdsByWay) {
        List<String> links = linkIdsByWay.get(wayId);
        if (links == null) return null;
        // Forward link ends at via node: the last segment's forward direction
        for (String linkId : links) {
            if (linkId.endsWith("_f")) return linkId;
        }
        return links.isEmpty() ? null : links.get(0);
    }

    private static String resolveToLink(String wayId, String viaNodeId,
                                         Map<String, List<String>> linkIdsByWay) {
        List<String> links = linkIdsByWay.get(wayId);
        if (links == null) return null;
        for (String linkId : links) {
            if (linkId.endsWith("_f")) return linkId;
        }
        return links.isEmpty() ? null : links.get(0);
    }

    public record Record(TurnRestrictionIndex index, Map<String, DisallowedNextLinks> perLink, List<OsmImportIssue> issues) {
    }
}

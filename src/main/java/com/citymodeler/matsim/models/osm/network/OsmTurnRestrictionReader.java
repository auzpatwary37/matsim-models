package com.citymodeler.matsim.models.osm.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.Node;
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
 *
 * <p>Resolution strategy:
 * <ul>
 *   <li>From-link: the link whose <b>to-node</b> is the via node (entering the junction)</li>
 *   <li>To-link: the link whose <b>from-node</b> is the via node (leaving the junction)</li>
 *   <li>{@code no_*}: the specified to-link is disallowed</li>
 *   <li>{@code only_*}: all OTHER to-links are disallowed (the specified one is the only allowed)</li>
 *   <li>Via-way restrictions are rejected with a warning</li>
 * </ul>
 */
public final class OsmTurnRestrictionReader {

    private OsmTurnRestrictionReader() {
    }

    public static Record read(
            OsmImportResult importResult,
            OsmNetworkBuildResult buildResult) {

        Network network = buildResult.cleanedNetwork();
        List<OsmImportIssue> issues = new ArrayList<>();
        TurnRestrictionIndex index = new TurnRestrictionIndex();
        Map<String, DisallowedNextLinks> perLink = new HashMap<>();

        // Build topological indexes: nodeId → outgoing/incoming link IDs
        Map<String, List<String>> nodeOutgoing = new HashMap<>(); // node → links leaving
        Map<String, List<String>> nodeIncoming = new HashMap<>(); // node → links entering
        for (var entry : network.getLinks().entrySet()) {
            Link link = entry.getValue();
            String linkId = entry.getKey().toString();
            String fromNode = link.getFromNode().getId().toString();
            String toNode = link.getToNode().getId().toString();
            nodeOutgoing.computeIfAbsent(fromNode, k -> new ArrayList<>()).add(linkId);
            nodeIncoming.computeIfAbsent(toNode, k -> new ArrayList<>()).add(linkId);
        }

        for (OsmRelationRecord rel : importResult.relations().values()) {
            String type = rel.tags().get("type");
            if (!"restriction".equals(type) && !"keep_right".equals(type) && !"keep_left".equals(type)) {
                continue;
            }

            String restriction = rel.tags().get("restriction");
            if (restriction == null) continue;

            String fromWayId = null, viaNodeId = null, toWayId = null;
            boolean viaIsWay = false;
            for (OsmRelationMemberRecord member : rel.members()) {
                switch (member.role()) {
                    case "from" -> { if (member.type() == OsmElementType.WAY) fromWayId = member.ref(); }
                    case "via" -> {
                        if (member.type() == OsmElementType.NODE) viaNodeId = member.ref();
                        else if (member.type() == OsmElementType.WAY) viaIsWay = true;
                    }
                    case "to" -> { if (member.type() == OsmElementType.WAY) toWayId = member.ref(); }
                    default -> { }
                }
            }

            if (viaIsWay) {
                issues.add(new OsmImportIssue(OsmIssueSeverity.WARNING,
                        "restriction-via-way-unsupported",
                        "Restriction " + rel.id() + " uses a via-way; not supported in Phase 1", null));
                continue;
            }
            if (fromWayId == null || toWayId == null || viaNodeId == null) {
                issues.add(new OsmImportIssue(OsmIssueSeverity.WARNING,
                        "restriction-incomplete",
                        "Restriction relation " + rel.id() + " missing from/via/to", null));
                continue;
            }

            // Resolve the via node's MATSim ID
            String matSimViaNodeId = OsmGeneratedIds.nodeId(viaNodeId);

            // Determine affected modes, respecting except=* exceptions
            Set<String> modes = resolveAffectedModes(rel, importResult, fromWayId, issues);
            if (modes.isEmpty()) continue;

            boolean isOnly = restriction.startsWith("only_");
            boolean isNo = restriction.startsWith("no_") || restriction.startsWith("no_") || type.startsWith("keep");

            // Resolve from-link(s): incoming to via node, belonging to fromWay
            List<String> fromLinks = resolveIncomingToVia(matSimViaNodeId, fromWayId,
                    buildResult.linkIdsByOsmWayId(), nodeIncoming);
            if (fromLinks.isEmpty()) {
                issues.add(new OsmImportIssue(OsmIssueSeverity.WARNING,
                        "restriction-from-unresolved",
                        "Could not resolve from-link for restriction " + rel.id(), null));
                continue;
            }

            // Resolve to-link: outgoing from via node, belonging to toWay
            List<String> toLinks = resolveOutgoingFromVia(matSimViaNodeId, toWayId,
                    buildResult.linkIdsByOsmWayId(), nodeOutgoing);
            if (toLinks.isEmpty()) {
                issues.add(new OsmImportIssue(OsmIssueSeverity.WARNING,
                        "restriction-to-unresolved",
                        "Could not resolve to-link for restriction " + rel.id(), null));
                continue;
            }

            // Apply semantics
            for (String fromLinkId : fromLinks) {
                DisallowedNextLinks current = perLink.getOrDefault(fromLinkId, DisallowedNextLinks.empty());

                if (isOnly) {
                    // only_*: disallow ALL other outgoing links from via node except toLinks
                    Set<String> allowed = new HashSet<>(toLinks);
                    for (String candidate : nodeOutgoing.getOrDefault(matSimViaNodeId, List.of())) {
                        if (!allowed.contains(candidate)) {
                            for (String mode : modes) {
                                index.addRestriction(fromLinkId, candidate, mode);
                                current = current.plus(mode, List.of(candidate));
                            }
                        }
                    }
                } else {
                    // no_*: disallow the specified to-links
                    for (String toLinkId : toLinks) {
                        for (String mode : modes) {
                            index.addRestriction(fromLinkId, toLinkId, mode);
                            current = current.plus(mode, List.of(toLinkId));
                        }
                    }
                }

                perLink.put(fromLinkId, current);
            }

            // Attach attribute to from-links
            for (String fromLinkId : fromLinks) {
                Link fromLink = network.getLinks().get(Id.create(fromLinkId, Link.class));
                if (fromLink != null) {
                    fromLink.getAttributes().putAttribute("disallowedNextLinks",
                            perLink.get(fromLinkId).toJson());
                }
            }
        }

        return new Record(index, perLink, issues);
    }

    /** Resolve modes from the from-way's rule, applying except=* exemptions. */
    private static Set<String> resolveAffectedModes(OsmRelationRecord rel, OsmImportResult importResult,
                                                     String fromWayId, List<OsmImportIssue> issues) {
        OsmWayRecord fromWay = importResult.ways().get(fromWayId);
        Set<String> modes;
        if (fromWay != null) {
            var rule = OsmNetworkBuildConfig.defaultConfig().resolveRule(fromWay.tags());
            modes = rule != null ? new HashSet<>(rule.allowedModes()) : new HashSet<>(Set.of("car"));
        } else {
            modes = new HashSet<>(Set.of("car"));
        }

        // Apply except=* exemptions
        for (var entry : rel.tags().asMap().entrySet()) {
            if (entry.getKey().startsWith("except:")) {
                String exceptMode = entry.getKey().substring("except:".length());
                if (exceptMode.equals(entry.getValue())) {
                    modes.remove(exceptMode);
                }
            }
        }
        return modes;
    }

    /** Find links entering the via node that belong to the given OSM way. */
    private static List<String> resolveIncomingToVia(String viaNodeId, String fromWayId,
                                                      Map<String, List<String>> linkIdsByWay,
                                                      Map<String, List<String>> nodeIncoming) {
        List<String> candidates = nodeIncoming.getOrDefault(viaNodeId, List.of());
        List<String> wayLinks = linkIdsByWay.getOrDefault(fromWayId, List.of());
        Set<String> wayLinkSet = new HashSet<>(wayLinks);

        // Prefer links from the from-way
        List<String> matched = new ArrayList<>();
        for (String linkId : candidates) {
            if (wayLinkSet.contains(linkId)) {
                matched.add(linkId);
            }
        }
        // Fallback: if no way-specific match, use any incoming link
        // (handles cases where the from-way wasn't imported or ID differs)
        if (matched.isEmpty()) {
            return List.copyOf(candidates);
        }
        return matched;
    }

    /** Find links leaving the via node that belong to the given OSM way. */
    private static List<String> resolveOutgoingFromVia(String viaNodeId, String toWayId,
                                                        Map<String, List<String>> linkIdsByWay,
                                                        Map<String, List<String>> nodeOutgoing) {
        List<String> candidates = nodeOutgoing.getOrDefault(viaNodeId, List.of());
        List<String> wayLinks = linkIdsByWay.getOrDefault(toWayId, List.of());
        Set<String> wayLinkSet = new HashSet<>(wayLinks);

        List<String> matched = new ArrayList<>();
        for (String linkId : candidates) {
            if (wayLinkSet.contains(linkId)) {
                matched.add(linkId);
            }
        }
        if (matched.isEmpty()) {
            return List.copyOf(candidates);
        }
        return matched;
    }

    public record Record(TurnRestrictionIndex index, Map<String, DisallowedNextLinks> perLink, List<OsmImportIssue> issues) {
    }
}

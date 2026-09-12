package com.citymodeler.matsim.models.osm.network;

import java.util.*;

import com.citymodeler.matsim.models.osm.OsmImportResult;
import com.citymodeler.matsim.models.osm.model.OsmNodeRecord;
import com.citymodeler.matsim.models.osm.model.OsmWayRecord;

/**
 * Atomic segment graph: one undirected edge per consecutive OSM node pair of an accepted way, each
 * carrying the resolved routing attributes of that pair and its allowed travel directions. Built
 * once and shared by the topology builder and the signal-aware simplifier so their topology cannot
 * diverge. Pure data; no MATSim types.
 */
public final class OsmSegmentGraph {

    /** One consecutive OSM node pair of a way, with resolved directed routing attributes. */
    public record Segment(
            String wayId, int segmentIndex, String nodeA, String nodeB,
            Set<String> modes, double speed, double lanes, double capacityPerLane,
            boolean forwardAllowed, boolean backwardAllowed) {
        public Segment {
            Objects.requireNonNull(wayId, "wayId");
            Objects.requireNonNull(nodeA, "nodeA");
            Objects.requireNonNull(nodeB, "nodeB");
            modes = Collections.unmodifiableSet(new TreeSet<>(modes));
        }
    }

    private final List<Segment> segments;
    private final Map<String, List<Segment>> byNode;

    private OsmSegmentGraph(List<Segment> segments) {
        this.segments = List.copyOf(segments);
        Map<String, List<Segment>> map = new TreeMap<>();
        for (Segment s : segments) {
            map.computeIfAbsent(s.nodeA(), k -> new ArrayList<>()).add(s);
            if (!s.nodeB().equals(s.nodeA())) {
                map.computeIfAbsent(s.nodeB(), k -> new ArrayList<>()).add(s);
            }
        }
        this.byNode = map;
    }

    public static OsmSegmentGraph build(OsmImportResult importResult, OsmNetworkBuildConfig config) {
        List<Segment> out = new ArrayList<>();
        OsmModeAccessResolver access = new OsmModeAccessResolver();
        OsmSpeedResolver speed = new OsmSpeedResolver();
        OsmLaneResolver lanes = new OsmLaneResolver();

        for (OsmWayRecord way : importResult.ways().values()) {
            OsmWayRule rule = config.resolveRule(way.tags());
            if (rule == null) {
                continue;
            }
            List<String> refs = way.nodeRefs();
            if (refs.size() < 2) {
                continue;
            }
            List<OsmModeAccessResolver.DirectionDecision> decisions = access.resolve(way, rule.allowedModes());
            for (int i = 0; i + 1 < refs.size(); i++) {
                String a = refs.get(i);
                String b = refs.get(i + 1);
                if (importResult.nodes().get(a) == null || importResult.nodes().get(b) == null) {
                    continue;
                }
                Set<String> modes = new TreeSet<>();
                boolean fwd = false;
                boolean bwd = false;
                for (OsmModeAccessResolver.DirectionDecision d : decisions) {
                    if (d.allowedModes().isEmpty()) {
                        continue;
                    }
                    if (d.forward()) { fwd = true; modes.addAll(d.allowedModes()); }
                    if (d.backward()) { bwd = true; modes.addAll(d.allowedModes()); }
                }
                if (modes.isEmpty()) {
                    continue;
                }
                boolean oneway = !(fwd && bwd);
                double resolvedSpeed = speed.resolve(way, rule, true);
                double resolvedLanes = lanes.resolve(way, rule, true, oneway);
                out.add(new Segment(way.id(), i, a, b, modes, resolvedSpeed, resolvedLanes,
                        rule.capacityPerLane(), fwd, bwd));
            }
        }
        out.sort(Comparator.comparing((Segment s) -> s.wayId())
                .thenComparingInt(Segment::segmentIndex));
        return new OsmSegmentGraph(out);
    }

    public List<Segment> segments() {
        return segments;
    }

    public Set<String> nodeIds() {
        return Collections.unmodifiableSet(byNode.keySet());
    }

    public List<Segment> segmentsFrom(String osmNodeId) {
        return Collections.unmodifiableList(byNode.getOrDefault(osmNodeId, List.of()));
    }

    public String other(Segment s, String osmNodeId) {
        if (s.nodeA().equals(osmNodeId)) return s.nodeB();
        if (s.nodeB().equals(osmNodeId)) return s.nodeA();
        throw new IllegalArgumentException("node " + osmNodeId + " not an endpoint of " + s);
    }

    /** True when the segment permits travel in the given directed endpoint order. */
    public static boolean allowsTravel(Segment s, String from, String to) {
        if (s.nodeA().equals(from) && s.nodeB().equals(to)) return s.forwardAllowed();
        if (s.nodeB().equals(from) && s.nodeA().equals(to)) return s.backwardAllowed();
        return false;
    }
}

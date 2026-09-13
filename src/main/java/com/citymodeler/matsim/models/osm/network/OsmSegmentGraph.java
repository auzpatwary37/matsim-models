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

    /** One consecutive OSM node pair of a way, with resolved DIRECTIONAL routing attributes. */
    public record Segment(
            String wayId, int segmentIndex, String nodeA, String nodeB,
            Set<String> forwardModes, Set<String> backwardModes,
            double forwardSpeed, double backwardSpeed,
            double forwardLanes, double backwardLanes, double capacityPerLane,
            boolean forwardAllowed, boolean backwardAllowed, double length) {
        public Segment {
            Objects.requireNonNull(wayId, "wayId");
            Objects.requireNonNull(nodeA, "nodeA");
            Objects.requireNonNull(nodeB, "nodeB");
            forwardModes = Collections.unmodifiableSet(new TreeSet<>(forwardModes));
            backwardModes = Collections.unmodifiableSet(new TreeSet<>(backwardModes));
        }

        /** Modes permitted in the given travel direction (nodeA->nodeB when {@code forward}). */
        public Set<String> modes(boolean forward) {
            return forward ? forwardModes : backwardModes;
        }

        /** Free speed in the given travel direction (nodeA->nodeB when {@code forward}). */
        public double speed(boolean forward) {
            return forward ? forwardSpeed : backwardSpeed;
        }

        /** Lanes in the given travel direction (nodeA->nodeB when {@code forward}). */
        public double lanes(boolean forward) {
            return forward ? forwardLanes : backwardLanes;
        }

        /** True when travel is permitted in the given direction (nodeA->nodeB when {@code forward}). */
        public boolean allowsTravel(boolean forward) {
            return forward ? forwardAllowed : backwardAllowed;
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

        Set<String> duplicateTracks = config.collapseParallelTransitTracks()
                ? parallelTransitDuplicates(importResult, config)
                : Set.of();

        for (OsmWayRecord way : importResult.ways().values()) {
            OsmWayRule rule = config.resolveRule(way.tags());
            if (rule == null || duplicateTracks.contains(way.id())) {
                continue;
            }
            List<String> refs = way.nodeRefs();
            if (refs.size() < 2) {
                continue;
            }
            List<OsmModeAccessResolver.DirectionDecision> decisions =
                    access.resolve(way, rule.allowedModes(), rule.defaultOneway());
            for (int i = 0; i + 1 < refs.size(); i++) {
                String a = refs.get(i);
                String b = refs.get(i + 1);
                if (importResult.nodes().get(a) == null || importResult.nodes().get(b) == null) {
                    continue;
                }
                Set<String> forwardModes = new TreeSet<>();
                Set<String> backwardModes = new TreeSet<>();
                boolean fwd = false;
                boolean bwd = false;
                for (OsmModeAccessResolver.DirectionDecision d : decisions) {
                    if (d.allowedModes().isEmpty()) {
                        continue;
                    }
                    if (d.forward()) { fwd = true; forwardModes.addAll(d.allowedModes()); }
                    if (d.backward()) { bwd = true; backwardModes.addAll(d.allowedModes()); }
                }
                if (forwardModes.isEmpty() && backwardModes.isEmpty()) {
                    continue;
                }
                boolean oneway = !(fwd && bwd);
                double forwardSpeed = speed.resolve(way, rule, true);
                double backwardSpeed = speed.resolve(way, rule, false);
                double forwardLanes = lanes.resolve(way, rule, true, oneway);
                double backwardLanes = lanes.resolve(way, rule, false, oneway);
                OsmNodeRecord ra = importResult.nodes().get(a);
                OsmNodeRecord rb = importResult.nodes().get(b);
                double length = Math.hypot(
                        ra.projectedCoord().getX() - rb.projectedCoord().getX(),
                        ra.projectedCoord().getY() - rb.projectedCoord().getY());
                out.add(new Segment(way.id(), i, a, b,
                        forwardModes, backwardModes, forwardSpeed, backwardSpeed,
                        forwardLanes, backwardLanes, rule.capacityPerLane(), fwd, bwd, length));
            }
        }
        out.sort(Comparator.comparing((Segment s) -> s.wayId())
                .thenComparingInt(Segment::segmentIndex));
        return new OsmSegmentGraph(out);
    }

    public List<Segment> segments() {
        return segments;
    }

    /**
     * Identifies transit-track ways that duplicate a parallel sibling and should be dropped. Two
     * {@code railway}-tagged ways are treated as one physical corridor when both endpoints coincide
     * within {@code PARALLEL_TRACK_TOLERANCE_METERS}; the lexicographically smaller way id is kept and
     * the other returned as a duplicate. This reflects the OSM convention of mapping a dual-track
     * railway as two parallel ways (one per direction); rendering both as two-way would double-count
     * direction. Deterministic: comparison is over sorted way ids.
     */
    private static Set<String> parallelTransitDuplicates(OsmImportResult importResult,
                                                         OsmNetworkBuildConfig config) {
        final double tolerance = 30.0;
        Map<String, double[]> endpoint = new TreeMap<>();
        Map<String, String> railType = new TreeMap<>();
        for (OsmWayRecord way : importResult.ways().values()) {
            String railway = way.tags().get("railway");
            if (railway == null || config.resolveRule(way.tags()) == null) {
                continue;
            }
            List<String> refs = way.nodeRefs();
            if (refs.size() < 2) {
                continue;
            }
            OsmNodeRecord a = importResult.nodes().get(refs.get(0));
            OsmNodeRecord b = importResult.nodes().get(refs.get(refs.size() - 1));
            if (a == null || b == null) {
                continue;
            }
            endpoint.put(way.id(), new double[] {
                    a.projectedCoord().getX(), a.projectedCoord().getY(),
                    b.projectedCoord().getX(), b.projectedCoord().getY()});
            railType.put(way.id(), railway);
        }

        Set<String> duplicates = new TreeSet<>();
        List<String> ids = new ArrayList<>(endpoint.keySet());
        for (int i = 0; i < ids.size(); i++) {
            String first = ids.get(i);
            if (duplicates.contains(first)) {
                continue;
            }
            for (int j = i + 1; j < ids.size(); j++) {
                String second = ids.get(j);
                if (duplicates.contains(second) || !railType.get(first).equals(railType.get(second))) {
                    continue;
                }
                if (parallel(endpoint.get(first), endpoint.get(second), tolerance)) {
                    // Keep the lexicographically smaller id; drop the other.
                    duplicates.add(first.compareTo(second) < 0 ? second : first);
                }
            }
        }
        return duplicates;
    }

    private static boolean parallel(double[] a, double[] b, double tolerance) {
        double straight = Math.hypot(a[0] - b[0], a[1] - b[1])
                + Math.hypot(a[2] - b[2], a[3] - b[3]);
        double crossed = Math.hypot(a[0] - b[2], a[1] - b[3])
                + Math.hypot(a[2] - b[0], a[3] - b[1]);
        return Math.min(straight, crossed) <= tolerance;
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

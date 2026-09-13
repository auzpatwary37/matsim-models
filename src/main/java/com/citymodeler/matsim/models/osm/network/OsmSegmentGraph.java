package com.citymodeler.matsim.models.osm.network;

import java.util.*;

import com.citymodeler.matsim.models.api.Coord;
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

    /** Endpoint coincidence tolerance for two parallel transit-track ways (metres). */
    private static final double PARALLEL_TRACK_TOLERANCE_METERS = 30.0;
    /** Intermediate-vertex coincidence tolerance used to confirm two tracks share a corridor. */
    private static final double PARALLEL_TRACK_VERTEX_TOLERANCE_METERS = 15.0;
    /** Spatial bucket size for the parallel-track candidate scan (metres). */
    private static final double PARALLEL_TRACK_CELL_METERS = 30.0;

    /** One consecutive OSM node pair of a way, with resolved DIRECTIONAL routing attributes. */
    public record Segment(
            String wayId, int segmentIndex, String nodeA, String nodeB,
            Set<String> forwardModes, Set<String> backwardModes,
            double forwardSpeed, double backwardSpeed,
            double forwardLanes, double backwardLanes, double capacityPerLane,
            boolean forwardAllowed, boolean backwardAllowed, double length,
            String forwardLaneSignature, String backwardLaneSignature) {
        public Segment {
            Objects.requireNonNull(wayId, "wayId");
            Objects.requireNonNull(nodeA, "nodeA");
            Objects.requireNonNull(nodeB, "nodeB");
            forwardModes = Collections.unmodifiableSet(new TreeSet<>(forwardModes));
            backwardModes = Collections.unmodifiableSet(new TreeSet<>(backwardModes));
            forwardLaneSignature = forwardLaneSignature == null ? "" : forwardLaneSignature;
            backwardLaneSignature = backwardLaneSignature == null ? "" : backwardLaneSignature;
        }

        /** Modes permitted in the given travel direction (nodeA->nodeB when {@code forward}). */
        public Set<String> modes(boolean forward) {
            return forward ? forwardModes : backwardModes;
        }

        /** Direction-specific lane-semantic tags, aligned to nodeA->nodeB when {@code forward}. */
        public String laneSignature(boolean forward) {
            return forward ? forwardLaneSignature : backwardLaneSignature;
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
                    access.resolve(way, OsmModeAccessResolver.candidateModes(rule.allowedModes(),
                            config.addBusToCarRoads()), rule.defaultOneway());
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
                        forwardLanes, backwardLanes, rule.capacityPerLane(), fwd, bwd, length,
                        laneSignature(way, true), laneSignature(way, false)));
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
     * Direction-specific lane/turn-lane semantics for a way, aligned to the way's node order when
     * {@code forward}. Used as a contraction boundary: lane semantics can change along a chain even
     * when the coarse routing attributes (lanes, speed, modes) do not, and {@code turn:lanes} is
     * approach-specific, so a node where the lane signature changes must remain a routing node.
     */
    private static String laneSignature(OsmWayRecord way, boolean forward) {
        StringBuilder sb = new StringBuilder();
        appendTag(sb, way, forward ? "lanes:forward" : "lanes:backward");
        appendTag(sb, way, forward ? "turn:lanes:forward" : "turn:lanes:backward");
        appendTag(sb, way, forward ? "bus:lanes:forward" : "bus:lanes:backward");
        appendTag(sb, way, forward ? "psv:lanes:forward" : "psv:lanes:backward");
        // Direction-independent approach semantics count for both orientations.
        appendTag(sb, way, "turn:lanes");
        appendTag(sb, way, "bus:lanes");
        appendTag(sb, way, "psv:lanes");
        return sb.toString();
    }

    private static void appendTag(StringBuilder sb, OsmWayRecord way, String key) {
        String value = way.tags().get(key);
        if (value != null) {
            sb.append(key).append('=').append(value).append(';');
        }
    }

    /**
     * Identifies transit-track ways that duplicate a parallel sibling and should be dropped. Two
     * {@code railway}-tagged ways of the same type are treated as one physical corridor when both
     * endpoints coincide within {@code PARALLEL_TRACK_TOLERANCE_METERS} <b>and</b> their intermediate
     * polylines also run close together (see {@link #polylinesClose}), so two genuinely distinct
     * tracks that merely share endpoints are never merged. The lexicographically smaller way id is
     * kept and the other returned as a duplicate. Deterministic: comparison is over sorted way ids,
     * and candidates are bucketed by a quantized endpoint cell so the scan is not O(R^2) globally.
     */
    private static Set<String> parallelTransitDuplicates(OsmImportResult importResult,
                                                         OsmNetworkBuildConfig config) {
        Map<String, List<Coord>> polyline = new TreeMap<>();
        Map<String, String> railType = new TreeMap<>();
        Map<String, long[]> endpoints = new TreeMap<>();
        for (OsmWayRecord way : importResult.ways().values()) {
            String railway = way.tags().get("railway");
            if (railway == null || config.resolveRule(way.tags()) == null) {
                continue;
            }
            List<String> refs = way.nodeRefs();
            if (refs.size() < 2) {
                continue;
            }
            List<Coord> pts = new ArrayList<>(refs.size());
            boolean complete = true;
            for (String ref : refs) {
                OsmNodeRecord nd = importResult.nodes().get(ref);
                if (nd == null) {
                    complete = false;
                    break;
                }
                pts.add(nd.projectedCoord());
            }
            if (!complete) {
                continue;
            }
            polyline.put(way.id(), pts);
            railType.put(way.id(), railway);
            Coord a = pts.get(0);
            Coord b = pts.get(pts.size() - 1);
            endpoints.put(way.id(), new long[] {
                    quantize(a.getX()), quantize(a.getY()),
                    quantize(b.getX()), quantize(b.getY())});
        }

        // Bucket candidates by both endpoint cells so only near-endpoint ways are compared.
        Map<String, List<String>> byCell = new TreeMap<>();
        for (var entry : endpoints.entrySet()) {
            for (String cell : cells(entry.getValue())) {
                byCell.computeIfAbsent(cell, k -> new ArrayList<>()).add(entry.getKey());
            }
        }

        Set<String> duplicates = new TreeSet<>();
        for (var entry : byCell.entrySet()) {
            List<String> candidates = entry.getValue();
            if (candidates.size() < 2) {
                continue;
            }
            for (int i = 0; i < candidates.size(); i++) {
                String first = candidates.get(i);
                if (duplicates.contains(first)) {
                    continue;
                }
                for (int j = i + 1; j < candidates.size(); j++) {
                    String second = candidates.get(j);
                    if (duplicates.contains(second) || first.equals(second)
                            || !railType.get(first).equals(railType.get(second))) {
                        continue;
                    }
                    if (parallel(endpoints.get(first), endpoints.get(second))
                            && polylinesClose(polyline.get(first), polyline.get(second))) {
                        // Keep the lexicographically smaller id; drop the other.
                        duplicates.add(first.compareTo(second) < 0 ? second : first);
                    }
                }
            }
        }
        return duplicates;
    }

    private static boolean parallel(long[] a, long[] b) {
        double straight = Math.hypot(a[0] - b[0], a[1] - b[1])
                + Math.hypot(a[2] - b[2], a[3] - b[3]);
        double crossed = Math.hypot(a[0] - b[2], a[1] - b[3])
                + Math.hypot(a[2] - b[0], a[3] - b[1]);
        return Math.min(straight, crossed) <= PARALLEL_TRACK_TOLERANCE_METERS;
    }

    /**
     * True when every vertex of one polyline has a close vertex on the other (and vice versa) in a
     * consistent orientation. This rejects two tracks that only share endpoints but diverge in the
     * middle (bridges, junctions, layer differences, distinct geometry).
     */
    private static boolean polylinesClose(List<Coord> a, List<Coord> b) {
        if (a == null || b == null) {
            return false;
        }
        return closeInOrientation(a, b) || closeInOrientation(a, reversed(b));
    }

    private static boolean closeInOrientation(List<Coord> a, List<Coord> b) {
        for (Coord p : a) {
            if (!hasNearbyVertex(p, b)) {
                return false;
            }
        }
        for (Coord p : b) {
            if (!hasNearbyVertex(p, a)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasNearbyVertex(Coord p, List<Coord> others) {
        for (Coord q : others) {
            if (Math.hypot(p.getX() - q.getX(), p.getY() - q.getY())
                    <= PARALLEL_TRACK_VERTEX_TOLERANCE_METERS) {
                return true;
            }
        }
        return false;
    }

    private static List<Coord> reversed(List<Coord> pts) {
        List<Coord> out = new ArrayList<>(pts);
        Collections.reverse(out);
        return out;
    }

    private static long quantize(double value) {
        return Math.round(value / PARALLEL_TRACK_CELL_METERS);
    }

    private static List<String> cells(long[] endpoint) {
        // Bucket by a small neighborhood of endpoint cells so a pair straddling a cell boundary is
        // still compared. Keyed on quantized (x,y) of both endpoints, orientation-insensitive.
        List<String> cells = new ArrayList<>(4);
        for (long dx = -1; dx <= 1; dx++) {
            for (long dy = -1; dy <= 1; dy++) {
                long ax = endpoint[0] + dx;
                long ay = endpoint[1] + dy;
                long bx = endpoint[2] + dx;
                long by = endpoint[3] + dy;
                long loX = Math.min(ax, bx);
                long loY = Math.min(ay, by);
                long hiX = Math.max(ax, bx);
                long hiY = Math.max(ay, by);
                cells.add(loX + ":" + loY + "|" + hiX + ":" + hiY);
            }
        }
        return cells;
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

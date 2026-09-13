package com.citymodeler.matsim.models.osm.network;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.citymodeler.matsim.models.osm.OsmTagSet;

public final class OsmNetworkBuildConfig {
    private final OsmGeometryMode geometryMode;
    private final boolean preserveTransitStopNodes;
    private final Set<String> explicitOsmNodeIdsToKeep;
    private final double sharpBendAngleDegrees;
    private final Map<String, OsmWayRule> rulesByKeyValue;
    private final boolean preserveCrossingNodes;
    private final boolean preserveBarrierNodes;
    private final boolean cleanupIsolatedComponents;
    private final Set<String> excludedHighwayClasses;
    private final Set<String> routableModes;
    private final double maxContractedLinkLengthMeters;
    private final boolean addBusToCarRoads;

    private OsmNetworkBuildConfig(OsmGeometryMode geometryMode, boolean preserveTransitStopNodes,
                                   Set<String> explicitOsmNodeIdsToKeep, double sharpBendAngleDegrees,
                                   Map<String, OsmWayRule> rulesByKeyValue,
                                   boolean preserveCrossingNodes, boolean preserveBarrierNodes,
                                   boolean cleanupIsolatedComponents, Set<String> excludedHighwayClasses,
                                   Set<String> routableModes, double maxContractedLinkLengthMeters,
                                   boolean addBusToCarRoads) {
        if (maxContractedLinkLengthMeters < 0) {
            throw new IllegalArgumentException("maxContractedLinkLengthMeters must be >= 0");
        }
        this.geometryMode = geometryMode;
        this.preserveTransitStopNodes = preserveTransitStopNodes;
        this.explicitOsmNodeIdsToKeep = Set.copyOf(explicitOsmNodeIdsToKeep);
        this.sharpBendAngleDegrees = sharpBendAngleDegrees;
        this.rulesByKeyValue = rulesByKeyValue;
        this.preserveCrossingNodes = preserveCrossingNodes;
        this.preserveBarrierNodes = preserveBarrierNodes;
        this.cleanupIsolatedComponents = cleanupIsolatedComponents;
        this.excludedHighwayClasses = Set.copyOf(excludedHighwayClasses);
        this.routableModes = Set.copyOf(routableModes);
        this.maxContractedLinkLengthMeters = maxContractedLinkLengthMeters;
        this.addBusToCarRoads = addBusToCarRoads;
    }

    public static OsmNetworkBuildConfig materializeGeometryConfig() {
        return new OsmNetworkBuildConfig(OsmGeometryMode.MATERIALIZE_GEOMETRY_NODES,
                true, Set.of(), 35.0, defaultRules(), false, true, false, Set.of(), defaultRoutableModes(),
                0.0, true);
    }

    /** Materialize-geometry config that additionally enforces routable cleaning (bundle pipeline). */
    public static OsmNetworkBuildConfig materializeGeometryConfigWithCleanup() {
        return new OsmNetworkBuildConfig(OsmGeometryMode.MATERIALIZE_GEOMETRY_NODES,
                true, Set.of(), 35.0, defaultRules(), false, true, true, Set.of(), defaultRoutableModes(),
                0.0, true);
    }

    public static OsmNetworkBuildConfig defaultConfig() {
        return new OsmNetworkBuildConfig(OsmGeometryMode.PRESERVE_AS_LINK_GEOMETRY,
                true, Set.of(), 35.0, defaultRules(), false, true, true, Set.of(), defaultRoutableModes(),
                0.0, true);
    }

    /** Default contraction config that additionally removes isolated non-transit components. */
    public static OsmNetworkBuildConfig defaultConfigWithCleanup() {
        return new OsmNetworkBuildConfig(OsmGeometryMode.PRESERVE_AS_LINK_GEOMETRY,
                true, Set.of(), 35.0, defaultRules(), false, true, true, Set.of(), defaultRoutableModes(),
                0.0, true);
    }

    /**
     * Scope comparable to pt2MATSim's default OSM converter: excludes {@code highway=service},
     * admits buses on all car roads (pt2MATSim labels roads {@code bus,car}), and caps contracted
     * road-link length at 500 m (pt2MATSim's {@code maxLinkLength}). The cap applies to car roads
     * only, never to rail/tram, matching pt2MATSim's rail handling.
     */
    public static OsmNetworkBuildConfig pt2matsimComparableConfig() {
        return new OsmNetworkBuildConfig(OsmGeometryMode.PRESERVE_AS_LINK_GEOMETRY,
                true, Set.of(), 35.0, defaultRules(), false, true, true, Set.of("service"),
                defaultRoutableModes(), 500.0, true);
    }

    private static Set<String> defaultRoutableModes() {
        return Set.of("car", "bus");
    }

    public OsmGeometryMode geometryMode() {
        return geometryMode;
    }

    public boolean preserveTransitStopNodes() {
        return preserveTransitStopNodes;
    }

    public Set<String> explicitOsmNodeIdsToKeep() {
        return explicitOsmNodeIdsToKeep;
    }

    public double sharpBendAngleDegrees() {
        return sharpBendAngleDegrees;
    }

    public boolean preserveCrossingNodes() {
        return preserveCrossingNodes;
    }

    public boolean preserveBarrierNodes() {
        return preserveBarrierNodes;
    }

    public boolean cleanupIsolatedComponents() {
        return cleanupIsolatedComponents;
    }

    /** Highway classes explicitly excluded from the network scope (empty = admit every rule). */
    public Set<String> excludedHighwayClasses() {
        return excludedHighwayClasses;
    }

    /** Modes for which the cleaner enforces strongly connected routability. */
    public Set<String> routableModes() {
        return routableModes;
    }

    /**
     * Maximum length (metres) of a contracted link; {@code 0} means no cap. When positive, a
     * degree-2 node is retained as a routing node if dissolving it would make the merged link longer
     * than this value.
     */
    public double maxContractedLinkLengthMeters() {
        return maxContractedLinkLengthMeters;
    }

    /**
     * When true, every link that allows {@code car} also allows {@code bus} (matching pt2MATSim's
     * {@code bus,car} road labelling), so transit mapping can route buses over the road network.
     */
    public boolean addBusToCarRoads() {
        return addBusToCarRoads;
    }

    public OsmWayRule resolveRule(OsmTagSet tags) {
        String highway = tags.get("highway");
        if (highway != null) {
            if (excludedHighwayClasses.contains(highway)) {
                return null;
            }
            OsmWayRule rule = rulesByKeyValue.get("highway:" + highway);
            if (rule != null) {
                return rule;
            }
        }
        String railway = tags.get("railway");
        if (railway != null) {
            OsmWayRule rule = rulesByKeyValue.get("railway:" + railway);
            if (rule != null) {
                return rule;
            }
        }
        String route = tags.get("route");
        if (route != null) {
            OsmWayRule rule = rulesByKeyValue.get("route:" + route);
            if (rule != null) {
                return rule;
            }
        }
        return null;
    }

    private static Map<String, OsmWayRule> defaultRules() {
        Map<String, OsmWayRule> rules = new LinkedHashMap<>();
        rules.put("highway:motorway", new OsmWayRule("highway", "motorway", 1,
                Set.of("car"), 3.0, 47.22, 1200.0, true, false));
        rules.put("highway:trunk", new OsmWayRule("highway", "trunk", 2,
                Set.of("car"), 3.0, 41.67, 1200.0, false, false));
        rules.put("highway:primary", new OsmWayRule("highway", "primary", 3,
                Set.of("car"), 2.0, 38.89, 900.0, false, false));
        rules.put("highway:secondary", new OsmWayRule("highway", "secondary", 4,
                Set.of("car"), 2.0, 33.33, 900.0, false, false));
        rules.put("highway:tertiary", new OsmWayRule("highway", "tertiary", 5,
                Set.of("car"), 1.0, 27.78, 600.0, false, false));
        rules.put("highway:unclassified", new OsmWayRule("highway", "unclassified", 6,
                Set.of("car"), 1.0, 13.89, 600.0, false, false));
        rules.put("highway:residential", new OsmWayRule("highway", "residential", 7,
                Set.of("car"), 1.0, 13.89, 600.0, false, false));
        rules.put("highway:living_street", new OsmWayRule("highway", "living_street", 8,
                Set.of("car"), 1.0, 8.33, 300.0, false, false));
        rules.put("highway:service", new OsmWayRule("highway", "service", 9,
                Set.of("car"), 1.0, 11.11, 600.0, false, false));
        // Link roads: OSM tags ramps/connectors as the parent class with a _link suffix. Each is a
        // distinct highway class, so each keeps its own hierarchy/modes/free-speed rather than one
        // generic rule (Review #1: do not invent a synthetic generic highway=link as the signal).
        // These are the connector/slip/internal roads at complex intersections.
        rules.put("highway:motorway_link", new OsmWayRule("highway", "motorway_link", 1,
                Set.of("car"), 3.0, 44.44, 1200.0, true, false));
        rules.put("highway:trunk_link", new OsmWayRule("highway", "trunk_link", 2,
                Set.of("car"), 3.0, 38.89, 1200.0, false, false));
        rules.put("highway:primary_link", new OsmWayRule("highway", "primary_link", 3,
                Set.of("car"), 2.0, 33.33, 900.0, false, false));
        rules.put("highway:secondary_link", new OsmWayRule("highway", "secondary_link", 4,
                Set.of("car"), 2.0, 27.78, 900.0, false, false));
        rules.put("highway:tertiary_link", new OsmWayRule("highway", "tertiary_link", 5,
                Set.of("car"), 1.0, 22.22, 600.0, false, false));
        // Bare highway=link is the unclassified minor-link variant; still a valid internal road.
        rules.put("highway:link", new OsmWayRule("highway", "link", 6,
                Set.of("car"), 1.0, 13.89, 600.0, false, false));
        rules.put("highway:busway", new OsmWayRule("highway", "busway", 3,
                Set.of("bus", "pt"), 1.0, 13.89, 600.0, false, true));
        rules.put("railway:rail", new OsmWayRule("railway", "rail", 1,
                Set.of("rail", "pt"), 1.0, 83.33, 3000.0, true, true));
        rules.put("railway:light_rail", new OsmWayRule("railway", "light_rail", 2,
                Set.of("light_rail", "pt"), 1.0, 27.78, 1200.0, true, true));
        rules.put("railway:subway", new OsmWayRule("railway", "subway", 3,
                Set.of("subway", "pt"), 1.0, 27.78, 1200.0, true, true));
        rules.put("railway:tram", new OsmWayRule("railway", "tram", 4,
                Set.of("tram", "pt"), 1.0, 16.67, 600.0, true, true));
        rules.put("railway:monorail", new OsmWayRule("railway", "monorail", 5,
                Set.of("monorail", "pt"), 1.0, 27.78, 1200.0, true, true));
        rules.put("railway:funicular", new OsmWayRule("railway", "funicular", 6,
                Set.of("funicular", "pt"), 1.0, 8.33, 300.0, false, true));
        rules.put("route:ferry", new OsmWayRule("route", "ferry", 10,
                Set.of("ferry", "car"), 1.0, 13.89, 300.0, true, true));
        return rules;
    }
}

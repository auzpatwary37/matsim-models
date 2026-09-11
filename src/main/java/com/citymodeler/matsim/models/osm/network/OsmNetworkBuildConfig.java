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

    private OsmNetworkBuildConfig(OsmGeometryMode geometryMode, boolean preserveTransitStopNodes,
                                   Set<String> explicitOsmNodeIdsToKeep, double sharpBendAngleDegrees,
                                   Map<String, OsmWayRule> rulesByKeyValue) {
        this.geometryMode = geometryMode;
        this.preserveTransitStopNodes = preserveTransitStopNodes;
        this.explicitOsmNodeIdsToKeep = Set.copyOf(explicitOsmNodeIdsToKeep);
        this.sharpBendAngleDegrees = sharpBendAngleDegrees;
        this.rulesByKeyValue = rulesByKeyValue;
    }

    public static OsmNetworkBuildConfig materializeGeometryConfig() {
        return new OsmNetworkBuildConfig(OsmGeometryMode.MATERIALIZE_GEOMETRY_NODES,
                true, Set.of(), 35.0, defaultRules());
    }

    public static OsmNetworkBuildConfig defaultConfig() {
        return new OsmNetworkBuildConfig(OsmGeometryMode.PRESERVE_AS_LINK_GEOMETRY,
                true, Set.of(), 35.0, defaultRules());
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

    public OsmWayRule resolveRule(OsmTagSet tags) {
        String highway = tags.get("highway");
        if (highway != null) {
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
        // Narrow junction/access roads. Included so that the internal box roads of a
        // multi-node intersection are materialised; the signal-aware simplifier relies on
        // highway=link as the canonical marker of "inside the same intersection box".
        rules.put("highway:link", new OsmWayRule("highway", "link", 9,
                Set.of("car"), 1.0, 11.11, 600.0, false, false));
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

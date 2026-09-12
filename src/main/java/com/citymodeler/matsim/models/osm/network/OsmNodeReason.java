package com.citymodeler.matsim.models.osm.network;

/** Reasons an OSM node must be preserved as a routing node during signal-aware simplification. */
public enum OsmNodeReason {
    WAY_ENDPOINT,
    SHARED_BY_MULTIPLE_WAYS,
    SIGNALIZED,
    TURN_RESTRICTION_VIA,
    TRANSIT_STOP,
    BARRIER,
    CROSSING,
    SEMANTIC_NODE_TAG,
    SHARP_BEND,
    EXPLICIT_PRESERVE
}

package com.citymodeler.matsim.models.osm.network;

/** Best-effort geometric classification of a turning movement. */
public enum OsmTurnType {
    LEFT,
    RIGHT,
    U_TURN,
    THROUGH,
    MERGE,
    SPLIT,
    UNKNOWN
}

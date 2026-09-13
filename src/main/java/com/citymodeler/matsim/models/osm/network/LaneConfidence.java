package com.citymodeler.matsim.models.osm.network;

/** Provenance vocabulary for a lane count / lane movement. */
public final class LaneConfidence {
    public static final String PRESENT = "present";
    public static final String EVEN_SPLIT = "wiki-default-even-split";
    public static final String UNDETERMINED_SPLIT = "undetermined-split";
    public static final String TURN_LANES_AUTHORITATIVE = "turn-lanes-authoritative";
    public static final String ABSENT = "absent";
    public static final String NONE_OBSERVED = "none-observed";
    public static final String UNSUPPORTED = "unsupported";
    public static final String PARTIAL = "partial";
    public static final String MERGE = "merge";

    private LaneConfidence() {
    }
}

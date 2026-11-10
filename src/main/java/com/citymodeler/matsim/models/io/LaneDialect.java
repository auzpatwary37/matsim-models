package com.citymodeler.matsim.models.io;

/**
 * Wire dialects recognized by the lane readers. Detection is structural
 * (element shapes), optionally confirmed by version/schema-location hints;
 * conflicting hints or mixed structures are reported, never guessed.
 */
public enum LaneDialect {

    /** Canonical {@code laneDefinitions_v2.0}: {@code lanesToLinkAssignment@linkIdRef}
     *  with nested {@code leadsTo/toLink|toLane}. */
    MATSIM_V2_0,

    /** Canonical {@code laneDefinitions_v1.1}: same assignment envelope with
     *  direct {@code toLink} children, {@code length/@meter}, representedLanes. */
    MATSIM_V1_1,

    /** This library's historic format: {@code assignment@linkId} with
     *  comma-separated {@code toLinkIds}/{@code toLaneIds} attributes. */
    MODELS_1_0
}

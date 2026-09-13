package com.citymodeler.matsim.models.osm.network;

import java.util.List;
import java.util.Objects;

/** One {@code turn:lanes} cell: its raw text, parsed movements, merge direction and unknowns. */
public record OsmTurnLaneCell(
        String raw,
        List<LaneTurnClass> indications,
        LaneMerge merge,
        boolean empty,
        List<String> unsupportedTokens) {

    public OsmTurnLaneCell {
        raw = Objects.requireNonNull(raw, "raw");
        indications = List.copyOf(indications);
        merge = Objects.requireNonNull(merge, "merge");
        unsupportedTokens = List.copyOf(unsupportedTokens);
    }
}

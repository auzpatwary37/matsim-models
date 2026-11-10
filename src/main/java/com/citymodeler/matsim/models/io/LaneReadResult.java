package com.citymodeler.matsim.models.io;

import java.util.List;
import java.util.Optional;

import com.citymodeler.matsim.models.lanes.Lanes;
import com.citymodeler.matsim.models.validation.SourceLocation;
import com.citymodeler.matsim.models.validation.ValidationIssue;

/**
 * Result of reading lane definitions from a source.
 *
 * <p>Invariants:</p>
 * <ul>
 *   <li>{@code PRESENT}: model present (possibly empty), no ERROR issues;</li>
 *   <li>{@code ABSENT}: no model, no dialect, no source, no issues;</li>
 *   <li>{@code INVALID}/{@code UNSUPPORTED}: no usable model, at least one
 *       ERROR issue;</li>
 *   <li>{@code dialect} present only when positively identified;</li>
 *   <li>issue list is a defensive copy; the model is caller-owned.</li>
 * </ul>
 */
public record LaneReadResult(LaneReadState state, Optional<Lanes> lanes,
        Optional<LaneDialect> dialect, Optional<SourceLocation> source,
        List<ValidationIssue> issues) {

    public LaneReadResult {
        issues = List.copyOf(issues);
    }

    public static LaneReadResult absent() {
        return new LaneReadResult(LaneReadState.ABSENT, Optional.empty(),
                Optional.empty(), Optional.empty(), List.of());
    }

    public static LaneReadResult present(Lanes lanes, LaneDialect dialect,
            SourceLocation source, List<ValidationIssue> issues) {
        return new LaneReadResult(LaneReadState.PRESENT, Optional.of(lanes),
                Optional.of(dialect), Optional.of(source), issues);
    }

    public static LaneReadResult invalid(LaneDialect dialect, SourceLocation source,
            List<ValidationIssue> issues) {
        return new LaneReadResult(LaneReadState.INVALID, Optional.empty(),
                Optional.ofNullable(dialect), Optional.ofNullable(source), issues);
    }

    public static LaneReadResult unsupported(SourceLocation source,
            List<ValidationIssue> issues) {
        return new LaneReadResult(LaneReadState.UNSUPPORTED, Optional.empty(),
                Optional.empty(), Optional.ofNullable(source), issues);
    }
}

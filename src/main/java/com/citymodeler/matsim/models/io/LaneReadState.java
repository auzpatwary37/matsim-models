package com.citymodeler.matsim.models.io;

/** Outcome state of a lane read; see {@link LaneReadResult} invariants. */
public enum LaneReadState {

    /** No lane block exists in the source. */
    ABSENT,

    /** A usable lane model was parsed; may carry warnings but no errors.
     *  An explicitly empty block (container present, zero assignments) is
     *  PRESENT with an empty model. */
    PRESENT,

    /** A lane block was recognized but is invalid (bad refs, duplicates,
     *  cycles, mixed dialects); no usable model, at least one ERROR issue. */
    INVALID,

    /** Lane-looking content in a structure this reader does not understand;
     *  no usable model, at least one ERROR issue. Never silently empty. */
    UNSUPPORTED
}

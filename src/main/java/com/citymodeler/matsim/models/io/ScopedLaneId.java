package com.citymodeler.matsim.models.io;

/** Composite {@code assignmentLink + laneId} identity with an unambiguous
 *  internal encoding (NUL separator; ids may contain underscores or colons). */
record ScopedLaneId(String linkId, String laneId) {

    static final char SEPARATOR = '\u0000';

    static String of(String linkId, String laneId) {
        return linkId + SEPARATOR + laneId;
    }

    static ScopedLaneId parse(String scopedKey) {
        int index = scopedKey.indexOf(SEPARATOR);
        return new ScopedLaneId(scopedKey.substring(0, index),
                scopedKey.substring(index + 1));
    }
}

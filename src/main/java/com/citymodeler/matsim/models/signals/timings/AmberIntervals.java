package com.citymodeler.matsim.models.signals.timings;

import com.citymodeler.matsim.models.api.Attributes;

/**
 * One amber interval pair (wire family amberTimes_v1.0, matsimObjectType
 * levels {@code globalDefaults}/{@code systemDefaults}/per-signal override):
 * optional {@code redAmber} and {@code amber} second counts. Null fields mean
 * "not specified at this level" — the published schema allows partial levels.
 * Attributes ride every level (matsimObjectType {@code <attributes>}).
 */
public final class AmberIntervals {
    private final Integer redAmberSec;
    private final Integer amberSec;
    private final Attributes attributes = new Attributes();

    public AmberIntervals(Integer redAmberSec, Integer amberSec) {
        if (redAmberSec != null && redAmberSec < 0) {
            throw new IllegalArgumentException("redAmberSec must be >= 0: " + redAmberSec);
        }
        if (amberSec != null && amberSec < 0) {
            throw new IllegalArgumentException("amberSec must be >= 0: " + amberSec);
        }
        this.redAmberSec = redAmberSec;
        this.amberSec = amberSec;
    }

    public Integer getRedAmberSec() {
        return redAmberSec;
    }

    public Integer getAmberSec() {
        return amberSec;
    }

    public Attributes getAttributes() {
        return attributes;
    }

}

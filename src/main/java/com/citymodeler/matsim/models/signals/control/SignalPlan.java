package com.citymodeler.matsim.models.signals.control;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.citymodeler.matsim.models.api.Attributes;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.signals.SignalGroup;

/**
 * One signal plan (wire family signalControl_v2.0, element
 * {@code <signalPlan>}). The {@code id} is optional on the wire; time-gating
 * ({@code start}/{@code stop} daytime strings, stored verbatim), optional
 * {@code cycleTime}/{@code offset} seconds, and flat per-group
 * {@code onset}/{@code dropping} settings are preserved exactly as read —
 * stage synthesis is projection-side, not IO.
 */
public final class SignalPlan {
    private final Map<Id<SignalGroup>, SignalGroupSettings> settings = new LinkedHashMap<>();
    private final Attributes attributes = new Attributes();
    private String id;
    private String startDaytime;
    private String stopDaytime;
    private Integer cycleTimeSec;
    private Integer offsetSec;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStartDaytime() {
        return startDaytime;
    }

    public void setStartDaytime(String startDaytime) {
        this.startDaytime = startDaytime;
    }

    public String getStopDaytime() {
        return stopDaytime;
    }

    public void setStopDaytime(String stopDaytime) {
        this.stopDaytime = stopDaytime;
    }

    public Integer getCycleTimeSec() {
        return cycleTimeSec;
    }

    public void setCycleTimeSec(Integer cycleTimeSec) {
        if (cycleTimeSec != null && cycleTimeSec < 0) {
            throw new IllegalArgumentException("cycleTimeSec must be >= 0: " + cycleTimeSec);
        }
        this.cycleTimeSec = cycleTimeSec;
    }

    public Integer getOffsetSec() {
        return offsetSec;
    }

    public void setOffsetSec(Integer offsetSec) {
        if (offsetSec != null && offsetSec < 0) {
            throw new IllegalArgumentException("offsetSec must be >= 0: " + offsetSec);
        }
        this.offsetSec = offsetSec;
    }

    public Map<Id<SignalGroup>, SignalGroupSettings> getSettings() {
        return Collections.unmodifiableMap(settings);
    }

    public void addSetting(SignalGroupSettings setting) {
        Objects.requireNonNull(setting, "setting");
        settings.put(setting.getSignalGroupRef(), setting);
    }

    public Attributes getAttributes() {
        return attributes;
    }
}

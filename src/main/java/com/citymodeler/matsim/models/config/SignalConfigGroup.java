package com.citymodeler.matsim.models.config;

import java.util.Objects;
import java.util.Optional;

/**
 * Typed accessor view over the generic {@code signals} config module of a
 * {@link Config}. The generic {@code Config}/{@code ConfigGroup} round-trip
 * already carries unknown module params (verified against the published
 * config writer/reader), so this class adds NO parsing: every accessor is a
 * one-line delegation to {@link ConfigGroup#addParam}/{@link
 * ConfigGroup#getParam}/{@link ConfigGroup#removeParam} with the exact MATSim
 * param name.
 *
 * <p>Comments and non-{@code name} module attributes are NOT preserved by the
 * generic config writer (a pre-existing library limitation, not introduced
 * here). Unknown or future MATSim params on the module survive via
 * {@link #getGroup()}.</p>
 */
public final class SignalConfigGroup {
    private static final String MODULE = "signals";

    private final ConfigGroup group;

    private SignalConfigGroup(ConfigGroup group) {
        this.group = group;
    }

    /**
     * Returns a typed view of the {@code signals} module, creating the module
     * when absent so setters always succeed (a get-or-create module is the
     * documented MATSim-style behavior for config groups).
     */
    public static SignalConfigGroup of(Config config) {
        Objects.requireNonNull(config, "config");
        return new SignalConfigGroup(
                config.getModule(MODULE).orElseGet(() -> config.createModule(MODULE)));
    }

    public Optional<String> getSignalSystemsPath() {
        return param("signalSystemsPath");
    }

    public void setSignalSystemsPath(String value) {
        putParam("signalSystemsPath", value);
    }

    public void removeSignalSystemsPath() {
        removeParam("signalSystemsPath");
    }

    public Optional<String> getSignalGroupsPath() {
        return param("signalGroupsPath");
    }

    public void setSignalGroupsPath(String value) {
        putParam("signalGroupsPath", value);
    }

    public void removeSignalGroupsPath() {
        removeParam("signalGroupsPath");
    }

    public Optional<String> getSignalControlPath() {
        return param("signalControlPath");
    }

    public void setSignalControlPath(String value) {
        putParam("signalControlPath", value);
    }

    public void removeSignalControlPath() {
        removeParam("signalControlPath");
    }

    public Optional<String> getSignalAmberTimesPath() {
        return param("signalAmberTimesPath");
    }

    public void setSignalAmberTimesPath(String value) {
        putParam("signalAmberTimesPath", value);
    }

    public void removeSignalAmberTimesPath() {
        removeParam("signalAmberTimesPath");
    }

    public Optional<String> getSignalIntergreenTimesPath() {
        return param("signalIntergreenTimesPath");
    }

    public void setSignalIntergreenTimesPath(String value) {
        putParam("signalIntergreenTimesPath", value);
    }

    public void removeSignalIntergreenTimesPath() {
        removeParam("signalIntergreenTimesPath");
    }

    public Optional<String> getHandler() {
        return param("handler");
    }

    public void setHandler(String value) {
        putParam("handler", value);
    }

    public void removeHandler() {
        removeParam("handler");
    }

    public Optional<String> getOffset() {
        return param("offset");
    }

    public void setOffset(String value) {
        putParam("offset", value);
    }

    public void removeOffset() {
        removeParam("offset");
    }

    /** Escape hatch for params this typed view does not model yet. */
    public void addParam(String name, String value) {
        group.addParam(name, value);
    }

    /** The wrapped generic module, for generic tooling and assertions. */
    public ConfigGroup getGroup() {
        return group;
    }

    private Optional<String> param(String name) {
        return group.getParam(name);
    }

    private void putParam(String name, String value) {
        group.addParam(name, value);
    }

    private void removeParam(String name) {
        group.removeParam(name);
    }
}

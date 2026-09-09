package com.citymodeler.matsim.models.validation;

import java.util.Set;

/**
 * Optional checks and severity overrides for {@link NetworkValidator}. All
 * options are disabled by default; defaults preserve the historical behavior.
 */
public final class NetworkValidatorOptions {
    private String selfLoopInfoPrefix;
    private Double maxLinkLengthMeters;
    private Set<String> requiredModes;

    private NetworkValidatorOptions() {
    }

    public static NetworkValidatorOptions defaults() {
        return new NetworkValidatorOptions();
    }

    /** Self-loops on link ids starting with this prefix are reported as INFO instead of WARNING. */
    public NetworkValidatorOptions allowSelfLoopPrefix(String prefix) {
        if (prefix != null && prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        this.selfLoopInfoPrefix = prefix;
        return this;
    }

    /** Flags links longer than the given number of meters. */
    public NetworkValidatorOptions maxLinkLengthMeters(double meters) {
        if (meters <= 0.0 || Double.isNaN(meters) || Double.isInfinite(meters)) {
            throw new IllegalArgumentException("meters must be positive and finite: " + meters);
        }
        this.maxLinkLengthMeters = meters;
        return this;
    }

    /** Flags modes that no link in the network allows. */
    public NetworkValidatorOptions requireModes(Set<String> modes) {
        if (modes != null) {
            for (String mode : modes) {
                if (mode == null || mode.isBlank()) {
                    throw new IllegalArgumentException("modes must not contain null or blank entries");
                }
            }
        }
        this.requiredModes = modes == null ? null : Set.copyOf(modes);
        return this;
    }

    public String getSelfLoopInfoPrefix() {
        return selfLoopInfoPrefix;
    }

    public Double getMaxLinkLengthMeters() {
        return maxLinkLengthMeters;
    }

    public Set<String> getRequiredModes() {
        return requiredModes;
    }
}

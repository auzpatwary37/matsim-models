package com.citymodeler.matsim.models.gtfs;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Derives and sanitizes GTFS feed IDs. IDs are always {@code [A-Za-z0-9_-]+}.
 */
public final class GtfsFeedIdCodec {

    private GtfsFeedIdCodec() {
    }

    public static String derive(Path path) {
        String name = path.getFileName() != null ? path.getFileName().toString() : path.toString();
        // Strip .zip / .gtfs extension
        name = name.replaceAll("(?i)\\.(zip|gtfs)$", "");
        return sanitize(name);
    }

    /**
     * Sanitize a raw candidate into the documented {@code [A-Za-z0-9_-]+} contract: any character
     * outside that set (including {@code .}) becomes {@code _}, runs collapse, and leading/trailing
     * separators are trimmed. Falls back to {@code feed} when nothing survives.
     */
    public static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) return "feed";
        String cleaned = raw.replaceAll("[^A-Za-z0-9_-]", "_")
                .replaceAll("_+", "_")
                .replaceAll("(^[_-]+|[_-]+$)", "");
        return cleaned.isEmpty() ? "feed" : cleaned;
    }

    /**
     * Assign unique IDs preserving input order: the first occurrence keeps the sanitized base, and
     * later collisions get a deterministic {@code -2}, {@code -3}, ... suffix. Deterministic for a
     * given ordered input, which is what multi-feed import relies on.
     */
    public static List<String> assignUnique(List<String> candidates) {
        List<String> assigned = new ArrayList<>(candidates.size());
        Set<String> used = new HashSet<>();
        for (String candidate : candidates) {
            String base = sanitize(candidate);
            String id = base;
            int suffix = 2;
            while (used.contains(id)) {
                id = base + "-" + suffix++;
            }
            used.add(id);
            assigned.add(id);
        }
        return assigned;
    }

    /** Assign unique IDs from a set of raw candidates, suffixing -2, -3 on collision. */
    public static Set<String> assignUnique(Set<String> candidates) {
        return new java.util.LinkedHashSet<>(assignUnique(new ArrayList<>(candidates)));
    }
}

package com.citymodeler.matsim.models.network.turnrestrictions;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Global index of turn restrictions keyed by (fromLinkId, toLinkId).
 * Allows fast lookup: "is movement from link A to link B disallowed for mode X?"
 */
public final class TurnRestrictionIndex {

    private record RestrictionKey(String fromLinkId, String toLinkId) implements Comparable<RestrictionKey> {
        @Override
        public int compareTo(RestrictionKey other) {
            int c = fromLinkId.compareTo(other.fromLinkId);
            return c != 0 ? c : toLinkId.compareTo(other.toLinkId);
        }
    }

    private final Map<RestrictionKey, Set<String>> disallowedModes;

    public TurnRestrictionIndex(Map<RestrictionKey, Set<String>> disallowedModes) {
        this.disallowedModes = new TreeMap<>();
        disallowedModes.forEach((k, v) -> this.disallowedModes.put(k, Set.copyOf(v)));
    }

    public TurnRestrictionIndex() {
        this.disallowedModes = new TreeMap<>();
    }

    public boolean isDisallowed(String mode, String fromLinkId, String toLinkId) {
        Set<String> modes = disallowedModes.get(new RestrictionKey(fromLinkId, toLinkId));
        return modes != null && modes.contains(mode);
    }

    public void addRestriction(String fromLinkId, String toLinkId, String mode) {
        disallowedModes.computeIfAbsent(new RestrictionKey(fromLinkId, toLinkId), k -> new TreeSet<>()).add(mode);
    }

    public int size() {
        return disallowedModes.size();
    }

    public boolean isEmpty() {
        return disallowedModes.isEmpty();
    }

    public Map<String, Set<String>> restrictionsForFromLink(String fromLinkId) {
        Map<String, Set<String>> result = new HashMap<>();
        for (var entry : disallowedModes.entrySet()) {
            if (entry.getKey().fromLinkId().equals(fromLinkId)) {
                result.put(entry.getKey().toLinkId(), Collections.unmodifiableSet(entry.getValue()));
            }
        }
        return result;
    }
}

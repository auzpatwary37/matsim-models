package com.citymodeler.matsim.models.network.turnrestrictions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import com.citymodeler.matsim.models.io.MatsimModelException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Immutable, mode-keyed collection of link sequences that may not follow the
 * current link. Wire format is the JSON form used by MATSim network files:
 * {@code {"car":[["linkA","linkB"]]}} with modes sorted alphabetically and
 * sequences in insertion order.
 */
public final class DisallowedNextLinks {
    private static final DisallowedNextLinks EMPTY = new DisallowedNextLinks(Map.of());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, List<List<String>>> byMode;

    private DisallowedNextLinks(Map<String, List<List<String>>> byMode) {
        Map<String, List<List<String>>> copy = new TreeMap<>();
        byMode.forEach((mode, sequences) ->
                copy.put(mode, sequences.stream().map(List::copyOf).toList()));
        this.byMode = Collections.unmodifiableMap(copy);
    }

    public static DisallowedNextLinks empty() {
        return EMPTY;
    }

    public DisallowedNextLinks plus(String mode, List<String> nextLinkSequence) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(nextLinkSequence, "nextLinkSequence");
        if (mode.isBlank()) {
            throw new IllegalArgumentException("mode must not be blank");
        }
        if (nextLinkSequence.isEmpty()) {
            throw new IllegalArgumentException("nextLinkSequence must not be empty");
        }
        for (String linkId : nextLinkSequence) {
            if (linkId == null || linkId.isBlank()) {
                throw new IllegalArgumentException("nextLinkSequence must not contain null or blank link ids");
            }
        }
        Map<String, List<List<String>>> copy = new TreeMap<>();
        byMode.forEach((existingMode, sequences) -> copy.put(existingMode, new ArrayList<>(sequences)));
        List<List<String>> sequences = copy.computeIfAbsent(mode, ignored -> new ArrayList<>());
        List<String> frozen = List.copyOf(nextLinkSequence);
        if (!sequences.contains(frozen)) {
            sequences.add(frozen);
        }
        return new DisallowedNextLinks(copy);
    }

    public boolean isDisallowed(String mode, List<String> nextLinkSequence) {
        if (mode == null || nextLinkSequence == null) {
            return false;
        }
        return byMode.getOrDefault(mode, List.of()).contains(nextLinkSequence);
    }

    public Map<String, List<List<String>>> asMap() {
        Map<String, List<List<String>>> copy = new TreeMap<>();
        byMode.forEach((mode, sequences) -> copy.put(mode, sequences.stream().map(List::copyOf).toList()));
        return Collections.unmodifiableMap(copy);
    }

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(byMode);
        } catch (JsonProcessingException exception) {
            throw new MatsimModelException("Could not serialize disallowed next links", exception);
        }
    }

    public static DisallowedNextLinks fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new MatsimModelException("Disallowed next links JSON must not be blank");
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            if (!root.isObject()) {
                throw new MatsimModelException("Disallowed next links JSON must be an object: " + json);
            }
            Map<String, List<List<String>>> parsed = new TreeMap<>();
            var fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if (entry.getKey().isBlank()) {
                    throw new MatsimModelException("Disallowed next links JSON must not contain blank modes: " + json);
                }
                parsed.put(entry.getKey(), parseSequences(entry.getValue(), json));
            }
            return new DisallowedNextLinks(parsed);
        } catch (MatsimModelException exception) {
            throw exception;
        } catch (JsonProcessingException exception) {
            throw new MatsimModelException("Disallowed next links JSON is malformed: " + json, exception);
        }
    }

    private static List<List<String>> parseSequences(JsonNode modeNode, String json) {
        if (!modeNode.isArray()) {
            throw new MatsimModelException("Disallowed next links mode entry must be an array: " + json);
        }
        List<List<String>> sequences = new ArrayList<>();
        for (JsonNode sequenceNode : modeNode) {
            if (!sequenceNode.isArray() || sequenceNode.isEmpty()) {
                throw new MatsimModelException("Disallowed next links sequences must be non-empty arrays: " + json);
            }
            List<String> sequence = new ArrayList<>();
            for (JsonNode linkNode : sequenceNode) {
                if (!linkNode.isTextual() || linkNode.asText().isBlank()) {
                    throw new MatsimModelException("Disallowed next links link ids must be non-blank strings: " + json);
                }
                sequence.add(linkNode.asText());
            }
            sequences.add(sequence);
        }
        return sequences;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DisallowedNextLinks other)) {
            return false;
        }
        return byMode.equals(other.byMode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(byMode);
    }

    @Override
    public String toString() {
        return toJson();
    }
}

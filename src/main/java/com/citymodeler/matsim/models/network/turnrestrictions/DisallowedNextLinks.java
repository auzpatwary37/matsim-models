package com.citymodeler.matsim.models.network.turnrestrictions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import com.citymodeler.matsim.models.io.MatsimModelException;

/**
 * Immutable value object storing disallowed next-link sequences per mode.
 * A "sequence" is an ordered list of link IDs that must appear consecutively
 * for the restriction to apply.
 */
public final class DisallowedNextLinks {

    private final Map<String, List<List<String>>> byMode;

    private DisallowedNextLinks(Map<String, List<List<String>>> byMode) {
        this.byMode = Collections.unmodifiableMap(new TreeMap<>(byMode));
    }

    public static DisallowedNextLinks empty() {
        return new DisallowedNextLinks(new LinkedHashMap<>());
    }

    public DisallowedNextLinks plus(String mode, List<String> sequence) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(sequence, "sequence");
        if (mode.isBlank()) {
            throw new IllegalArgumentException("mode must not be blank");
        }
        if (sequence.isEmpty()) {
            throw new IllegalArgumentException("sequence must not be empty");
        }
        for (String link : sequence) {
            if (link == null) {
                throw new IllegalArgumentException("sequence contains null");
            }
        }

        Map<String, List<List<String>>> copy = new LinkedHashMap<>();
        for (var entry : byMode.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        List<List<String>> existing = copy.getOrDefault(mode, List.of());
        List<List<String>> newSeqs = new ArrayList<>(existing);
        if (!newSeqs.contains(sequence)) {
            newSeqs.add(List.copyOf(sequence));
        }
        copy.put(mode, List.copyOf(newSeqs));
        return new DisallowedNextLinks(copy);
    }

    /** Checks if the given sequence is a prefix-matching disallowed sequence for the mode. */
    public boolean isDisallowed(String mode, List<String> candidate) {
        List<List<String>> sequences = byMode.get(mode);
        if (sequences == null) return false;
        for (List<String> seq : sequences) {
            if (candidate.size() < seq.size()) continue;
            boolean match = true;
            for (int i = 0; i < seq.size(); i++) {
                if (!candidate.get(i).equals(seq.get(i))) {
                    match = false;
                    break;
                }
            }
            if (match) return true;
        }
        return false;
    }

    public Map<String, List<List<String>>> asMap() {
        Map<String, List<List<String>>> result = new LinkedHashMap<>();
        byMode.forEach((k, v) -> result.put(k, Collections.unmodifiableList(v.stream()
                .map(List::copyOf)
                .toList())));
        return Collections.unmodifiableMap(result);
    }

    public String toJson() {
        if (byMode.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var entry : byMode.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escape(entry.getKey())).append("\":[");
            List<List<String>> seqs = entry.getValue();
            for (int i = 0; i < seqs.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("[");
                List<String> seq = seqs.get(i);
                for (int j = 0; j < seq.size(); j++) {
                    if (j > 0) sb.append(",");
                    sb.append("\"").append(escape(seq.get(j))).append("\"");
                }
                sb.append("]");
            }
            sb.append("]");
        }
        sb.append("}");
        return sb.toString();
    }

    public static DisallowedNextLinks fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new MatsimModelException("DisallowedNextLinks JSON must not be null or blank");
        }
        String trimmed = json.trim();
        if ("{}".equals(trimmed)) {
            return empty();
        }
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw new MatsimModelException("DisallowedNextLinks JSON must be an object: " + trimmed);
        }
        String body = trimmed.substring(1, trimmed.length() - 1).trim();
        if (body.isEmpty()) {
            return empty();
        }

        Map<String, List<List<String>>> result = new LinkedHashMap<>();
        int pos = 0;
        while (pos < body.length()) {
            // Skip whitespace and commas
            while (pos < body.length() && (body.charAt(pos) == ' ' || body.charAt(pos) == ',')) pos++;
            if (pos >= body.length()) break;
            if (body.charAt(pos) != '"') {
                throw new MatsimModelException("Expected '\"' at position " + pos + " in: " + trimmed);
            }
            // Parse mode key
            int keyEnd = findClosingQuote(body, pos + 1);
            String mode = body.substring(pos + 1, keyEnd);
            pos = keyEnd + 1;
            // Expect ':'
            while (pos < body.length() && body.charAt(pos) == ' ') pos++;
            if (pos >= body.length() || body.charAt(pos) != ':') {
                throw new MatsimModelException("Expected ':' at position " + pos);
            }
            pos++;
            // Expect '['
            while (pos < body.length() && body.charAt(pos) == ' ') pos++;
            if (pos >= body.length() || body.charAt(pos) != '[') {
                throw new MatsimModelException("Expected '[' for mode " + mode + " at position " + pos);
            }
            pos++;
            // Parse list of sequences
            List<List<String>> sequences = new ArrayList<>();
            while (pos < body.length()) {
                while (pos < body.length() && (body.charAt(pos) == ' ' || body.charAt(pos) == ',')) pos++;
                if (pos >= body.length()) break;
                if (body.charAt(pos) == ']') { pos++; break; }
                if (body.charAt(pos) != '[') {
                    throw new MatsimModelException("Expected '[' or ']' at position " + pos + " in: " + trimmed);
                }
                pos++; // skip '['
                // Parse inner sequence
                List<String> seq = new ArrayList<>();
                while (pos < body.length()) {
                    while (pos < body.length() && (body.charAt(pos) == ' ' || body.charAt(pos) == ',')) pos++;
                    if (pos >= body.length()) break;
                    if (body.charAt(pos) == ']') { pos++; break; }
                    if (body.charAt(pos) != '"') {
                        throw new MatsimModelException("Expected '\"' in sequence at position " + pos);
                    }
                    int valEnd = findClosingQuote(body, pos + 1);
                    String val = body.substring(pos + 1, valEnd);
                    if (val.isEmpty()) {
                        throw new MatsimModelException("Empty link ID in sequence");
                    }
                    seq.add(val);
                    pos = valEnd + 1;
                }
                if (seq.isEmpty()) {
                    throw new MatsimModelException("Empty sequence for mode " + mode);
                }
                sequences.add(List.copyOf(seq));
            }
            result.put(mode, List.copyOf(sequences));
        }
        return new DisallowedNextLinks(result);
    }

    private static int findClosingQuote(String s, int start) {
        for (int i = start; i < s.length(); i++) {
            if (s.charAt(i) == '\\') { i++; continue; }
            if (s.charAt(i) == '"') return i;
        }
        throw new MatsimModelException("Unterminated string in JSON");
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DisallowedNextLinks other)) return false;
        return byMode.equals(other.byMode);
    }

    @Override
    public int hashCode() {
        return byMode.hashCode();
    }

    @Override
    public String toString() {
        return "DisallowedNextLinks" + byMode;
    }
}

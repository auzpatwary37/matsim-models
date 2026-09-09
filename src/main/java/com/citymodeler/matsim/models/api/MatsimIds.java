package com.citymodeler.matsim.models.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic codec for composite ids built from typed parts. The
 * delimiter is a single character; an occurrence of the delimiter inside a
 * part is escaped by doubling it, which makes round trips lossless:
 * {@code compose(":", "a:b", "c")} → {@code "a::b:c"} →
 * {@code decompose("a::b:c", ":")} → {@code ["a:b", "c"]}.
 */
public final class MatsimIds {
    private MatsimIds() {
    }

    private static void requireSingleCharacterDelimiter(String delimiter) {
        if (delimiter == null || delimiter.length() != 1) {
            throw new IllegalArgumentException("delimiter must be a single character: " + delimiter);
        }
    }

    public static String escape(String part, String delimiter) {
        requireSingleCharacterDelimiter(delimiter);
        if (part == null) {
            throw new NullPointerException("part");
        }
        return part.replace(delimiter, delimiter + delimiter);
    }

    public static String compose(String delimiter, String... parts) {
        requireSingleCharacterDelimiter(delimiter);
        if (parts == null) {
            throw new NullPointerException("parts");
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part == null) {
                throw new NullPointerException("parts[" + i + "]");
            }
            if (part.isBlank()) {
                throw new IllegalArgumentException("parts[" + i + "] must not be blank");
            }
            if (i > 0) {
                result.append(delimiter);
            }
            result.append(escape(part, delimiter));
        }
        return result.toString();
    }

    public static List<String> decompose(String id, String delimiter) {
        requireSingleCharacterDelimiter(delimiter);
        if (id == null) {
            throw new NullPointerException("id");
        }
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        char delim = delimiter.charAt(0);
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c == delim && i + 1 < id.length() && id.charAt(i + 1) == delim) {
                current.append(delim);
                i++;
            } else if (c == delim) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());
        for (String part : parts) {
            if (part.isEmpty()) {
                throw new IllegalArgumentException("id contains a blank part: " + id);
            }
        }
        return List.copyOf(parts);
    }
}

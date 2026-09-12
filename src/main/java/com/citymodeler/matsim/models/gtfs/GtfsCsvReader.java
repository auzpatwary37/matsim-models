package com.citymodeler.matsim.models.gtfs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Robust GTFS CSV parser. Handles:
 * - UTF-8 BOM
 * - CRLF and LF line endings
 * - Quoted fields (with embedded commas and newlines)
 * - Doubled quotes inside quoted fields
 * - Missing optional columns (row shorter than header)
 * - Extra columns (ignored)
 * - Empty lines (skipped)
 */
public final class GtfsCsvReader {

    private GtfsCsvReader() {
    }

    public record CsvTable(List<String> headers, List<Map<String, String>> rows) {
        public int column(String name) {
            return headers.indexOf(name);
        }
        public boolean hasColumn(String name) {
            return headers.contains(name);
        }
    }

    public static String cell(Map<String, String> row, String name) {
        return row.get(name);
    }

    public static CsvTable read(Reader input) throws IOException {
        BufferedReader br = new BufferedReader(input);

        // Read first line, strip BOM if present
        String firstLine = br.readLine();
        if (firstLine == null) {
            return new CsvTable(List.of(), List.of());
        }
        if (!firstLine.isEmpty() && firstLine.charAt(0) == '\uFEFF') {
            firstLine = firstLine.substring(1);
        }

        // Find the header line (skip empty lines)
        String headerLine = null;
        while (firstLine != null) {
            if (!firstLine.isBlank()) {
                headerLine = firstLine;
                break;
            }
            firstLine = br.readLine();
        }
        if (headerLine == null) {
            return new CsvTable(List.of(), List.of());
        }

        List<String> headers = parseLine(headerLine);
        List<Map<String, String>> rows = new ArrayList<>();
        String line;
        while ((line = readLogicalLine(br)) != null) {
            if (line.isBlank()) continue;
            List<String> fields = parseLine(line);
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                String value = i < fields.size() ? fields.get(i) : "";
                row.put(headers.get(i), value);
            }
            rows.add(row);
        }
        return new CsvTable(List.copyOf(headers), List.copyOf(rows));
    }

    private static String readLogicalLine(BufferedReader br) throws IOException {
        String line = br.readLine();
        if (line == null) return null;
        // A logical record continues onto the next physical line only while we end inside an
        // unterminated quoted field. This is a boolean "still open?" test, not a quote count.
        while (endsInsideQuotedField(line)) {
            String next = br.readLine();
            if (next == null) break;
            line = line + "\n" + next;
        }
        return line;
    }

    /** True if the physical line ends while still inside an unterminated quoted field. */
    private static boolean endsInsideQuotedField(String s) {
        boolean inQuotes = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < s.length() && s.charAt(i + 1) == '"') {
                        i++; // doubled quote: escaped quote, stay inside
                    } else {
                        inQuotes = false;
                    }
                }
            } else if (c == '"') {
                inQuotes = true;
            }
        }
        return inQuotes;
    }

    static List<String> parseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
        }
        fields.add(current.toString());
        return fields;
    }
}

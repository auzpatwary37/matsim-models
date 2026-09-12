package com.citymodeler.matsim.models.gtfs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads one or more GTFS feeds (zip or folder) into a GtfsFeedSet.
 */
public final class GtfsImporter {

    public GtfsImporter() {
    }

    public GtfsFeedSet read(GtfsImportConfig config) throws IOException {
        Map<String, GtfsFeed> feeds = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();

        // Deterministic collision handling (Review #4): resolve unique feed ids in source order
        // (first occurrence keeps the base id, later ones get -2, -3, ...) BEFORE parsing, so two
        // sources that sanitize/derive to the same id no longer abort the whole import.
        List<String> requestedIds = new ArrayList<>();
        for (GtfsImportConfig.FeedSource source : config.feeds()) {
            requestedIds.add(source.effectiveFeedId());
        }
        List<String> assignedIds = GtfsFeedIdCodec.assignUnique(requestedIds);
        for (int i = 0; i < requestedIds.size(); i++) {
            if (!requestedIds.get(i).equals(assignedIds.get(i))) {
                warnings.add("feed id collision: '" + requestedIds.get(i) + "' -> '"
                        + assignedIds.get(i) + "'");
            }
        }

        for (int i = 0; i < config.feeds().size(); i++) {
            GtfsImportConfig.FeedSource source = config.feeds().get(i);
            String feedId = assignedIds.get(i);
            Map<String, GtfsCsvReader.CsvTable> tables = readTables(source.path(), warnings);
            GtfsFeed feed = parseFeed(feedId, tables, warnings);
            if (feeds.containsKey(feedId)) {
                // Should be impossible after assignUnique, but keep a deterministic guard.
                throw new IOException("Duplicate feed ID after collision handling: " + feedId);
            }
            feeds.put(feedId, feed);
        }
        return new GtfsFeedSet(feeds, warnings);
    }

    private Map<String, GtfsCsvReader.CsvTable> readTables(Path path, List<String> warnings) throws IOException {
        Map<String, GtfsCsvReader.CsvTable> tables = new HashMap<>();
        if (path.toFile().isDirectory()) {
            readFromDirectory(path, tables);
        } else {
            readFromZip(path, tables);
        }
        return tables;
    }

    private void readFromDirectory(Path dir, Map<String, GtfsCsvReader.CsvTable> tables) throws IOException {
        List<Path> csvFiles = Files.list(dir)
                .filter(p -> p.toString().endsWith(".txt"))
                .sorted()
                .toList();
        for (Path file : csvFiles) {
            String name = file.getFileName().toString().replace(".txt", "");
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                tables.put(name, GtfsCsvReader.read(reader));
            }
        }
    }

    private void readFromZip(Path zipPath, Map<String, GtfsCsvReader.CsvTable> tables) throws IOException {
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".txt")) continue;
                String name = entry.getName();
                name = name.substring(name.lastIndexOf('/') + 1).replace(".txt", "");
                try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                    tables.put(name, GtfsCsvReader.read(reader));
                }
            }
        }
    }

    private GtfsFeed parseFeed(String feedId, Map<String, GtfsCsvReader.CsvTable> tables,
                                List<String> warnings) {
        // Agency
        String agencyId = null;
        String timezone = null;
        GtfsCsvReader.CsvTable agencyTable = tables.get("agency");
        if (agencyTable != null && !agencyTable.rows().isEmpty()) {
            var row = agencyTable.rows().get(0);
            agencyId = GtfsCsvReader.cell(row, "agency_id");
            timezone = GtfsCsvReader.cell(row, "agency_timezone");
        }

        // Stops
        Map<String, GtfsStop> stops = new LinkedHashMap<>();
        GtfsCsvReader.CsvTable stopsTable = tables.get("stops");
        if (stopsTable != null) {
            for (var row : stopsTable.rows()) {
                String stopId = GtfsCsvReader.cell(row, "stop_id");
                String name = GtfsCsvReader.cell(row, "stop_name");
                double lat = parseDouble(GtfsCsvReader.cell(row, "stop_lat"));
                double lon = parseDouble(GtfsCsvReader.cell(row, "stop_lon"));
                int locType = parseInt(GtfsCsvReader.cell(row, "location_type"), 0);
                String parent = GtfsCsvReader.cell(row, "parent_station");
                String zoneId = GtfsCsvReader.cell(row, "zone_id");
                String url = GtfsCsvReader.cell(row, "stop_url");
                String desc = GtfsCsvReader.cell(row, "description");
                stops.put(stopId, new GtfsStop(stopId, name, lat, lon, locType, parent, zoneId, url, desc));
            }
        }

        // Routes
        Map<String, GtfsRoute> routes = new LinkedHashMap<>();
        GtfsCsvReader.CsvTable routesTable = tables.get("routes");
        if (routesTable != null) {
            boolean hasRouteType = routesTable.hasColumn("route_type");
            for (var row : routesTable.rows()) {
                String routeId = GtfsCsvReader.cell(row, "route_id");
                String rAgency = GtfsCsvReader.cell(row, "agency_id");
                String shortName = GtfsCsvReader.cell(row, "route_short_name");
                String longName = GtfsCsvReader.cell(row, "route_long_name");
                String url = GtfsCsvReader.cell(row, "route_url");
                int routeType = parseInt(GtfsCsvReader.cell(row, "route_type"), -1);
                if (!hasRouteType && routeType == -1) {
                    warnings.add(feedId + ": missing route_type column, defaulting to 3 (bus)");
                    routeType = 3;
                }
                String color = GtfsCsvReader.cell(row, "route_color");
                String textColor = GtfsCsvReader.cell(row, "route_text_color");
                routes.put(routeId, new GtfsRoute(routeId, rAgency != null ? rAgency : agencyId,
                        shortName, longName, url, routeType, color, textColor));
            }
        }

        // Trips
        Map<String, GtfsTrip> trips = new LinkedHashMap<>();
        GtfsCsvReader.CsvTable tripsTable = tables.get("trips");
        if (tripsTable != null) {
            for (var row : tripsTable.rows()) {
                String tripId = GtfsCsvReader.cell(row, "trip_id");
                String routeId = GtfsCsvReader.cell(row, "route_id");
                String serviceId = GtfsCsvReader.cell(row, "service_id");
                String headsign = GtfsCsvReader.cell(row, "trip_headsign");
                String shortDesc = GtfsCsvReader.cell(row, "trip_short_description");
                String dirStr = GtfsCsvReader.cell(row, "direction_id");
                boolean hasDir = dirStr != null && !dirStr.isBlank();
                int dir = parseInt(dirStr, 0);
                String blockId = GtfsCsvReader.cell(row, "block_id");
                String shapeId = GtfsCsvReader.cell(row, "shape_id");
                boolean wheelchair = "1".equals(GtfsCsvReader.cell(row, "wheelchair_accessible"));
                boolean bikes = "1".equals(GtfsCsvReader.cell(row, "bikes_allowed"));
                trips.put(tripId, new GtfsTrip(tripId, routeId, serviceId, headsign,
                        shortDesc, dir, hasDir, blockId, shapeId, wheelchair, bikes));
            }
        }

        // Stop times
        Map<String, List<GtfsStopTime>> stopTimesByTrip = new LinkedHashMap<>();
        GtfsCsvReader.CsvTable stTable = tables.get("stop_times");
        if (stTable != null) {
            for (var row : stTable.rows()) {
                String tripId = GtfsCsvReader.cell(row, "trip_id");
                String stopId = GtfsCsvReader.cell(row, "stop_id");
                int seq = parseInt(GtfsCsvReader.cell(row, "stop_sequence"), 0);
                String arrStr = GtfsCsvReader.cell(row, "arrival_time");
                String depStr = GtfsCsvReader.cell(row, "departure_time");
                Integer arr = (arrStr != null && !arrStr.isBlank()) ? parseTime(arrStr) : null;
                Integer dep = (depStr != null && !depStr.isBlank()) ? parseTime(depStr) : null;
                int tpRaw = parseInt(GtfsCsvReader.cell(row, "timepoint"), 1);
                boolean isTimepoint = tpRaw == 1; // GTFS: 1=exact, 0=approximate; default=1
                int pickup = parseInt(GtfsCsvReader.cell(row, "pickup_type"), 0);
                int dropOff = parseInt(GtfsCsvReader.cell(row, "drop_off_type"), 0);
                GtfsStopTime st = new GtfsStopTime(tripId, stopId, seq, arr, dep, isTimepoint, pickup, dropOff);
                stopTimesByTrip.computeIfAbsent(tripId, k -> new ArrayList<>()).add(st);
            }
            // Sort by stop_sequence
            stopTimesByTrip.values().forEach(list ->
                    list.sort(Comparator.comparingInt(GtfsStopTime::stopSequence)));
        }

        // Calendar
        List<GtfsCalendarRow> calendarRows = new ArrayList<>();
        GtfsCsvReader.CsvTable calTable = tables.get("calendar");
        if (calTable != null) {
            for (var row : calTable.rows()) {
                calendarRows.add(new GtfsCalendarRow(
                        GtfsCsvReader.cell(row, "service_id"),
                        parseInt(GtfsCsvReader.cell(row, "monday"), 0),
                        parseInt(GtfsCsvReader.cell(row, "tuesday"), 0),
                        parseInt(GtfsCsvReader.cell(row, "wednesday"), 0),
                        parseInt(GtfsCsvReader.cell(row, "thursday"), 0),
                        parseInt(GtfsCsvReader.cell(row, "friday"), 0),
                        parseInt(GtfsCsvReader.cell(row, "saturday"), 0),
                        parseInt(GtfsCsvReader.cell(row, "sunday"), 0),
                        GtfsCsvReader.cell(row, "start_date"),
                        GtfsCsvReader.cell(row, "end_date")));
            }
        }

        // Calendar dates
        List<GtfsCalendarDatesRow> calDatesRows = new ArrayList<>();
        GtfsCsvReader.CsvTable calDatesTable = tables.get("calendar_dates");
        if (calDatesTable != null) {
            for (var row : calDatesTable.rows()) {
                calDatesRows.add(new GtfsCalendarDatesRow(
                        GtfsCsvReader.cell(row, "service_id"),
                        GtfsCsvReader.cell(row, "date"),
                        parseInt(GtfsCsvReader.cell(row, "exception_type"), 1)));
            }
        }

        // Frequencies
        List<GtfsFrequencyRow> freqRows = new ArrayList<>();
        GtfsCsvReader.CsvTable freqTable = tables.get("frequencies");
        if (freqTable != null) {
            for (var row : freqTable.rows()) {
                freqRows.add(new GtfsFrequencyRow(
                        GtfsCsvReader.cell(row, "trip_id"),
                        parseTime(GtfsCsvReader.cell(row, "start_time")),
                        parseTime(GtfsCsvReader.cell(row, "end_time")),
                        parseInt(GtfsCsvReader.cell(row, "headway_secs"), 0),
                        parseInt(GtfsCsvReader.cell(row, "exact_times"), 0)));
            }
        }

        // Shapes
        Map<String, List<GtfsShapePoint>> shapes = new LinkedHashMap<>();
        GtfsCsvReader.CsvTable shapesTable = tables.get("shapes");
        if (shapesTable != null) {
            for (var row : shapesTable.rows()) {
                String shapeId = GtfsCsvReader.cell(row, "shape_id");
                int seq = parseInt(GtfsCsvReader.cell(row, "shape_pt_sequence"), 0);
                double lat = parseDouble(GtfsCsvReader.cell(row, "shape_pt_lat"));
                double lon = parseDouble(GtfsCsvReader.cell(row, "shape_pt_lon"));
                Integer dist = parseOptInt(GtfsCsvReader.cell(row, "shape_dist_traveled"));
                shapes.computeIfAbsent(shapeId, k -> new ArrayList<>())
                        .add(new GtfsShapePoint(shapeId, seq, lat, lon, dist));
            }
            shapes.values().forEach(list ->
                    list.sort(Comparator.comparingInt(GtfsShapePoint::shapePtSeq)));
        }

        return new GtfsFeed(feedId, agencyId, timezone, stops, routes, trips,
                stopTimesByTrip, calendarRows, calDatesRows, freqRows, shapes, warnings);
    }

    static double parseDouble(String s) {
        if (s == null || s.isBlank()) return 0.0;
        try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return 0.0; }
    }

    static int parseInt(String s, int defaultValue) {
        if (s == null || s.isBlank()) return defaultValue;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return defaultValue; }
    }

    static Integer parseOptInt(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    /** Parses HH:MM:SS to seconds. Handles >24h times. */
    static int parseTime(String s) {
        if (s == null || s.isBlank()) return 0;
        s = s.trim();
        String[] parts = s.split(":");
        int h = parts.length > 0 ? Integer.parseInt(parts[0]) : 0;
        int m = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        int sec = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
        return h * 3600 + m * 60 + sec;
    }
}

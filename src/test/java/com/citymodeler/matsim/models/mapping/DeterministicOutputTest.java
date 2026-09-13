package com.citymodeler.matsim.models.mapping;

import static org.junit.jupiter.api.Assertions.*;

import com.citymodeler.matsim.models.gtfs.GtfsFeed;
import com.citymodeler.matsim.models.gtfs.GtfsFeedSet;
import com.citymodeler.matsim.models.gtfs.GtfsImportConfig;
import com.citymodeler.matsim.models.gtfs.GtfsImporter;
import com.citymodeler.matsim.models.gtfs.GtfsTransitScheduleBuilder;
import com.citymodeler.matsim.models.io.TransitScheduleXmlWriter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

/**
 * Determinism: importing and building the same GTFS feed must yield byte-identical schedule XML, and
 * the result must not depend on the physical row order of the source CSV files.
 *
 * <p>The fixture is fixed-date (2026-01) and contains multiple rows per file so the row shuffles below
 * genuinely reorder records rather than being cosmetic.
 */
class DeterministicOutputTest {

    private static final String AGENCY_HEADER = "agency_id,agency_name,agency_url,agency_timezone";
    private static final String STOPS_HEADER = "stop_id,stop_name,stop_lat,stop_lon";
    private static final String ROUTES_HEADER = "route_id,route_short_name,route_type";
    private static final String TRIPS_HEADER = "trip_id,route_id,service_id,direction_id";
    private static final String STOP_TIMES_HEADER =
            "trip_id,stop_id,stop_sequence,arrival_time,departure_time";
    private static final String CALENDAR_HEADER =
            "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date";

    private static final List<String> AGENCY_ROWS = List.of(
            "AG1,Test,https://x.com,UTC");

    private static final List<String> STOP_ROWS = List.of(
            "S1,Alpha,45.50,-73.50",
            "S2,Beta,45.51,-73.51",
            "S3,Gamma,45.52,-73.52");

    private static final List<String> ROUTE_ROWS = List.of(
            "R1,1,3",
            "R2,2,0");

    private static final List<String> TRIP_ROWS = List.of(
            "T1,R1,SVC,0",
            "T2,R1,SVC,1",
            "T3,R2,SVC,0");

    private static final List<String> STOP_TIME_ROWS = List.of(
            "T1,S1,1,08:00:00,08:00:00",
            "T1,S2,2,08:05:00,08:05:00",
            "T1,S3,3,08:10:00,08:10:00",
            "T2,S1,1,09:00:00,09:00:00",
            "T2,S2,2,09:05:00,09:05:00",
            "T2,S3,3,09:10:00,09:10:00",
            "T3,S1,1,10:00:00,10:00:00",
            "T3,S2,2,10:07:00,10:07:00",
            "T3,S3,3,10:14:00,10:14:00");

    private static final List<String> CALENDAR_ROWS = List.of(
            "SVC,1,1,1,1,1,0,0,20260101,20260131");

    @Test
    void identicalFeedImportProducesByteIdenticalSchedule(@TempDir Path feedDir) throws Exception {
        writeFeed(feedDir, 20260913L);

        GtfsImportConfig config = GtfsImportConfig.forFolder(feedDir,
                GtfsImportConfig.ServiceDateSelection.DAY_WITH_MOST_TRIPS);
        GtfsFeedSet feeds1 = new GtfsImporter().read(config);
        GtfsFeedSet feeds2 = new GtfsImporter().read(config);

        String xml1 = new TransitScheduleXmlWriter().writeToString(
                new GtfsTransitScheduleBuilder().build(feeds1, config).schedule());
        String xml2 = new TransitScheduleXmlWriter().writeToString(
                new GtfsTransitScheduleBuilder().build(feeds2, config).schedule());

        assertEquals(xml1, xml2, "two imports of the same feed must serialize identically");
        assertFalse(xml1.isBlank());
    }

    @Test
    void shuffledFeedRowOrderProducesByteIdenticalSchedule(@TempDir Path feedDir) throws Exception {
        // Same physical directory (hence same derived feed id) is rewritten with a different, fixed
        // shuffle seed before the second import, so the only variable is source row order.
        GtfsImportConfig config = GtfsImportConfig.forFolder(feedDir,
                GtfsImportConfig.ServiceDateSelection.DAY_WITH_MOST_TRIPS);

        writeFeed(feedDir, 111L);
        String rawBefore = allRawRows(feedDir);
        String xmlBefore = new TransitScheduleXmlWriter().writeToString(
                new GtfsTransitScheduleBuilder().build(new GtfsImporter().read(config), config).schedule());

        writeFeed(feedDir, 222L);
        String rawAfter = allRawRows(feedDir);
        // Prove the shuffle is real: the source bytes actually changed between the two builds.
        assertNotEquals(rawBefore, rawAfter, "fixture shuffle must reorder the source rows");
        String xmlAfter = new TransitScheduleXmlWriter().writeToString(
                new GtfsTransitScheduleBuilder().build(new GtfsImporter().read(config), config).schedule());

        assertEquals(xmlBefore, xmlAfter, "schedule XML must not depend on GTFS row order");
    }

    /**
     * Regression guard for the per-JVM hash-salt determinism bug. The parsed feed must expose its
     * entity maps in a canonical (natural key) order: the builder numbers routes ({@code _r0},
     * {@code _r1}, ...) by iterating these maps, so a {@code Map.copyOf}-style unordered view makes
     * the serialized schedule depend on process identity, while preserving CSV physical row order
     * would make it depend on row order. Canonical order is invariant to both.
     */
    @Test
    void parsedFeedExposesCanonicalKeyOrder(@TempDir Path feedDir) throws Exception {
        writeFeed(feedDir, 333L);
        GtfsImportConfig config = GtfsImportConfig.forFolder(feedDir,
                GtfsImportConfig.ServiceDateSelection.DAY_WITH_MOST_TRIPS);

        GtfsFeedSet feeds = new GtfsImporter().read(config);
        GtfsFeed feed = feeds.allFeeds().iterator().next();

        assertEquals(new TreeSet<>(csvFirstColumn(feedDir, "stops.txt")),
                new TreeSet<>(feed.stops().keySet()),
                "stop set must match the source");
        for (List<String> keys : List.of(
                new ArrayList<>(feed.stops().keySet()),
                new ArrayList<>(feed.routes().keySet()),
                new ArrayList<>(feed.trips().keySet()))) {
            List<String> sorted = new ArrayList<>(keys);
            Collections.sort(sorted);
            assertEquals(sorted, keys, "feed maps must iterate in canonical key order: " + keys);
        }
    }

    private static List<String> csvFirstColumn(Path dir, String name) throws IOException {
        List<String> ids = new ArrayList<>();
        List<String> lines = Files.readAllLines(dir.resolve(name));
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (!line.isEmpty()) {
                ids.add(line.split(",", 2)[0]);
            }
        }
        return ids;
    }

    private static void writeFeed(Path dir, long shuffleSeed) throws IOException {
        Random random = new Random(shuffleSeed);
        writeCsv(dir, "agency.txt", AGENCY_HEADER, AGENCY_ROWS);
        writeCsv(dir, "stops.txt", STOPS_HEADER, shuffled(STOP_ROWS, random));
        writeCsv(dir, "routes.txt", ROUTES_HEADER, shuffled(ROUTE_ROWS, random));
        writeCsv(dir, "trips.txt", TRIPS_HEADER, shuffled(TRIP_ROWS, random));
        writeCsv(dir, "stop_times.txt", STOP_TIMES_HEADER, shuffled(STOP_TIME_ROWS, random));
        writeCsv(dir, "calendar.txt", CALENDAR_HEADER, CALENDAR_ROWS);
    }

    private static String allRawRows(Path dir) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String name : new String[] {"agency.txt", "stops.txt", "routes.txt",
                "trips.txt", "stop_times.txt", "calendar.txt"}) {
            sb.append(Files.readString(dir.resolve(name)));
        }
        return sb.toString();
    }

    private static List<String> shuffled(List<String> rows, Random random) {
        List<String> copy = new ArrayList<>(rows);
        Collections.shuffle(copy, random);
        return copy;
    }

    private static void writeCsv(Path dir, String name, String header, List<String> rows)
            throws IOException {
        StringBuilder sb = new StringBuilder(header).append('\n');
        for (String row : rows) {
            sb.append(row).append('\n');
        }
        Files.writeString(dir.resolve(name), sb.toString());
    }
}

package com.citymodeler.matsim.models.gtfs;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GtfsImporterTest {

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void createFeed() throws IOException {
        Path feed = tempDir.resolve("sample-feed");
        Files.createDirectories(feed);

        Files.writeString(feed.resolve("agency.txt"), """
                agency_id,agency_name,agency_url,agency_timezone
                A1,Sample Transit,https://example.com,America/New_York
                """);
        Files.writeString(feed.resolve("stops.txt"), """
                stop_id,stop_name,stop_lat,stop_lon,location_type,parent_station
                S1,Central Station,45.5017,-73.5673,0,
                S2,Platform North,45.5020,-73.5670,0,S1
                ST1,Station Area,45.5018,-73.5672,1,
                S3,Empty Stop,,,0,
                """);
        Files.writeString(feed.resolve("routes.txt"), """
                route_id,agency_id,route_short_name,route_long_name,route_type
                R1,A1,1,Central Line,3
                R2,A1,T1,Tram,0
                """);
        Files.writeString(feed.resolve("trips.txt"), """
                trip_id,route_id,service_id,trip_headsign,direction_id
                T1,R1,SVC1,Downtown,0
                T2,R1,SVC1,Uptown,1
                T3,R2,SVC1,East,
                """);
        Files.writeString(feed.resolve("stop_times.txt"), """
                trip_id,stop_id,stop_sequence,arrival_time,departure_time
                T1,S1,1,08:00:00,08:00:00
                T1,S2,2,08:05:00,08:05:30
                T2,S2,1,08:10:00,08:10:00
                T2,S1,2,08:15:00,08:15:00
                T3,S1,1,09:00:00,09:00:00
                T3,S3,2,09:05:00,09:05:00
                """);
        Files.writeString(feed.resolve("calendar.txt"), """
                service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date
                SVC1,1,1,1,1,1,0,0,20260101,20261231
                """);
    }

    @Test
    void readsBasicFeed() throws IOException {
        GtfsImportConfig config = GtfsImportConfig.forFolder(tempDir.resolve("sample-feed"),
                GtfsImportConfig.ServiceDateSelection.DAY_WITH_MOST_TRIPS);
        GtfsFeedSet set = new GtfsImporter().read(config);

        assertEquals(1, set.feeds().size());
        GtfsFeed feed = set.feeds().values().iterator().next();
        assertEquals("sample-feed", feed.feedId());
        assertEquals("A1", feed.agencyId());
        assertEquals("America/New_York", feed.agencyTimezone());
        assertEquals(4, feed.stops().size());
        assertEquals(2, feed.routes().size());
        assertEquals(3, feed.trips().size());
    }

    @Test
    void filtersLocationTypeForPlatforms() throws IOException {
        GtfsImportConfig config = GtfsImportConfig.forFolder(tempDir.resolve("sample-feed"),
                GtfsImportConfig.ServiceDateSelection.ALL);
        GtfsFeedSet set = new GtfsImporter().read(config);
        GtfsFeed feed = set.feeds().values().iterator().next();

        // ST1 is location_type=1, not a platform
        assertFalse(feed.stops().get("ST1").isPlatform());
        assertTrue(feed.stops().get("S1").isPlatform());
        // S3 has no coordinates
        assertFalse(feed.stops().get("S3").hasCoordinates());
    }

    @Test
    void directionIdDefaultsToZeroWhenEmpty() throws IOException {
        GtfsImportConfig config = GtfsImportConfig.forFolder(tempDir.resolve("sample-feed"),
                GtfsImportConfig.ServiceDateSelection.ALL);
        GtfsFeedSet set = new GtfsImporter().read(config);
        GtfsFeed feed = set.feeds().values().iterator().next();

        GtfsTrip t3 = feed.trips().get("T3");
        assertFalse(t3.hasDirection());
        assertEquals(0, t3.effectiveDirectionId());

        GtfsTrip t1 = feed.trips().get("T1");
        assertTrue(t1.hasDirection());
        assertEquals(0, t1.effectiveDirectionId());
    }

    @Test
    void stopTimesAreParsedAndSorted() throws IOException {
        GtfsImportConfig config = GtfsImportConfig.forFolder(tempDir.resolve("sample-feed"),
                GtfsImportConfig.ServiceDateSelection.ALL);
        GtfsFeedSet set = new GtfsImporter().read(config);
        GtfsFeed feed = set.feeds().values().iterator().next();

        List<GtfsStopTime> t1Stops = feed.stopTimesByTrip().get("T1");
        assertEquals(2, t1Stops.size());
        assertEquals("S1", t1Stops.get(0).stopId());
        assertEquals(28800, t1Stops.get(0).departureTime()); // 08:00:00
        assertEquals("S2", t1Stops.get(1).stopId());
    }

    @Test
    void calendarRowsParsed() throws IOException {
        GtfsImportConfig config = GtfsImportConfig.forFolder(tempDir.resolve("sample-feed"),
                GtfsImportConfig.ServiceDateSelection.ALL);
        GtfsFeedSet set = new GtfsImporter().read(config);
        GtfsFeed feed = set.feeds().values().iterator().next();

        assertEquals(1, feed.calendarRows().size());
        GtfsCalendarRow cal = feed.calendarRows().get(0);
        assertEquals("SVC1", cal.serviceId());
        assertEquals(1, cal.monday());
        assertEquals(0, cal.saturday());
        assertEquals("20260101", cal.startDate());
    }

    @Test
    void prefixedIdsUseColonDelimeter() throws IOException {
        GtfsImportConfig config = GtfsImportConfig.forFolder(tempDir.resolve("sample-feed"),
                GtfsImportConfig.ServiceDateSelection.ALL);
        GtfsFeedSet set = new GtfsImporter().read(config);
        GtfsFeed feed = set.feeds().values().iterator().next();

        assertEquals("sample-feed:S1", feed.prefixedStopId("S1"));
        assertEquals("sample-feed:R1", feed.prefixedRouteId("R1"));
        assertEquals("sample-feed:T1", feed.prefixedTripId("T1"));
    }

    @Test
    void feedSetAggregatesAcrossFeeds() throws IOException {
        // Create a second feed
        Path feed2 = tempDir.resolve("other-feed");
        Files.createDirectories(feed2);
        Files.writeString(feed2.resolve("agency.txt"), "agency_id,agency_name,agency_url,agency_timezone\nA2,Other,https://x.com,UTC\n");
        Files.writeString(feed2.resolve("stops.txt"), "stop_id,stop_name,stop_lat,stop_lon\nX1,West Stop,45.6,-73.7\n");
        Files.writeString(feed2.resolve("routes.txt"), "route_id,route_short_name,route_type\nX1,W1,3\n");
        Files.writeString(feed2.resolve("trips.txt"), "trip_id,route_id,service_id\nXT1,X1,SVX\n");
        Files.writeString(feed2.resolve("stop_times.txt"), "trip_id,stop_id,stop_sequence,departure_time\nXT1,X1,1,07:00:00\n");
        Files.writeString(feed2.resolve("calendar.txt"), "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date\nSVX,1,1,1,1,1,0,0,20260101,20261231\n");

        GtfsImportConfig config = new GtfsImportConfig(
                List.of(
                        new GtfsImportConfig.FeedSource(tempDir.resolve("sample-feed"), null),
                        new GtfsImportConfig.FeedSource(feed2, null)),
                GtfsImportConfig.ServiceDateSelection.ALL, false);
        GtfsFeedSet set = new GtfsImporter().read(config);

        assertEquals(2, set.feeds().size());
        assertEquals(5, set.allStops().size()); // 4 + 1
        assertTrue(set.allStops().containsKey("sample-feed:S1"));
        assertTrue(set.allStops().containsKey("other-feed:X1"));
    }

    @Test
    void csvParserHandlesQuotedFields() throws IOException {
        String csv = "a,b,c\n\"hello, world\",\"say \"\"hi\"\"\",plain\n";
        GtfsCsvReader.CsvTable table = GtfsCsvReader.read(new StringReader(csv));
        assertEquals(List.of("a", "b", "c"), table.headers());
        assertEquals("hello, world", GtfsCsvReader.cell(table.rows().get(0), "a"));
        assertEquals("say \"hi\"", GtfsCsvReader.cell(table.rows().get(0), "b"));
        assertEquals("plain", GtfsCsvReader.cell(table.rows().get(0), "c"));
    }

    @Test
    void csvParserHandlesBomAndCrlf() throws IOException {
        String csv = "\uFEFFa,b\r\n1,2\r\n3,4\r\n";
        GtfsCsvReader.CsvTable table = GtfsCsvReader.read(new StringReader(csv));
        assertEquals(List.of("a", "b"), table.headers());
        assertEquals(2, table.rows().size());
        assertEquals("1", GtfsCsvReader.cell(table.rows().get(0), "a"));
    }

    @Test
    void timeParsingHandlesOver24Hours() {
        assertEquals(90000, GtfsImporter.parseTime("25:00:00"));
        assertEquals(86400, GtfsImporter.parseTime("24:00:00"));
        assertEquals(0, GtfsImporter.parseTime("00:00:00"));
        assertEquals(28830, GtfsImporter.parseTime("08:00:30"));
    }
}

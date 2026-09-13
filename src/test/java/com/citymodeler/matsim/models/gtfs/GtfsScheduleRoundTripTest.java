package com.citymodeler.matsim.models.gtfs;

import static org.junit.jupiter.api.Assertions.*;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.io.TransitScheduleXmlReader;
import com.citymodeler.matsim.models.io.TransitScheduleXmlWriter;
import com.citymodeler.matsim.models.transit.Departure;
import com.citymodeler.matsim.models.transit.TransitLine;
import com.citymodeler.matsim.models.transit.TransitRoute;
import com.citymodeler.matsim.models.transit.TransitRouteStop;
import com.citymodeler.matsim.models.transit.TransitSchedule;
import com.citymodeler.matsim.models.transit.TransitStopFacility;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Round-trip stability: schedule built from GTFS -> XML -> read back must preserve structure, stop
 * coordinates and departures, and writing the re-read schedule must reproduce the exact same bytes.
 */
class GtfsScheduleRoundTripTest {

    @Test
    void builtScheduleSurvivesXmlRoundTripAndReserializesIdentically(@TempDir Path feedDir)
            throws Exception {
        Files.writeString(feedDir.resolve("agency.txt"),
                "agency_id,agency_name,agency_url,agency_timezone\nAG1,Test,https://x.com,UTC\n");
        Files.writeString(feedDir.resolve("stops.txt"),
                "stop_id,stop_name,stop_lat,stop_lon\n" +
                "S1,Alpha,45.500000,-73.500000\n" +
                "S2,Beta,45.510000,-73.510000\n" +
                "S3,Gamma,45.520000,-73.520000\n");
        Files.writeString(feedDir.resolve("routes.txt"),
                "route_id,route_short_name,route_type\nR1,1,3\n");
        Files.writeString(feedDir.resolve("trips.txt"),
                "trip_id,route_id,service_id\nT1,R1,SVC\n");
        Files.writeString(feedDir.resolve("stop_times.txt"),
                "trip_id,stop_id,stop_sequence,arrival_time,departure_time\n" +
                "T1,S1,1,08:00:00,08:00:00\n" +
                "T1,S2,2,08:05:00,08:07:00\n" +
                "T1,S3,3,08:12:00,08:12:00\n");
        Files.writeString(feedDir.resolve("calendar.txt"),
                "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date\n" +
                "SVC,1,1,1,1,1,0,0,20260101,20260131\n");

        GtfsImportConfig config = GtfsImportConfig.forFolder(feedDir,
                GtfsImportConfig.ServiceDateSelection.DAY_WITH_MOST_TRIPS);
        GtfsFeedSet feeds = new GtfsImporter().read(config);
        TransitSchedule built = new GtfsTransitScheduleBuilder().build(feeds, config).schedule();

        assertFalse(built.getFacilities().isEmpty());
        assertFalse(built.getTransitLines().isEmpty());

        TransitScheduleXmlWriter writer = new TransitScheduleXmlWriter();
        String firstXml = writer.writeToString(built);

        TransitSchedule reread = new TransitScheduleXmlReader().read(firstXml);

        // Stop set and coordinates survive.
        assertEquals(built.getFacilities().size(), reread.getFacilities().size());
        assertEquals(built.getFacilities().keySet(), reread.getFacilities().keySet());
        for (Map.Entry<Id<TransitStopFacility>, TransitStopFacility> entry
                : built.getFacilities().entrySet()) {
            TransitStopFacility original = entry.getValue();
            TransitStopFacility restored = reread.getFacilities().get(entry.getKey());
            assertNotNull(restored, "facility must survive round-trip: " + entry.getKey());
            assertEquals(original.getCoord(), restored.getCoord(),
                    "coordinates must survive round-trip for " + entry.getKey());
            assertEquals(original.getName(), restored.getName());
        }

        // Lines / routes / departures / stops counts survive, keyed identically.
        assertEquals(built.getTransitLines().size(), reread.getTransitLines().size());
        assertEquals(built.getTransitLines().keySet(), reread.getTransitLines().keySet());

        String secondXml = writer.writeToString(reread);
        assertEquals(firstXml, secondXml, "re-serializing the re-read schedule must be byte-identical");
    }

    /**
     * Structural counts are asserted per line, per route and per departure so a lossy round-trip that
     * happens to keep the top-level totals would still fail here.
     */
    @Test
    void routeAndDepartureStructurePreservedPerLine(@TempDir Path feedDir) throws Exception {
        Files.writeString(feedDir.resolve("agency.txt"),
                "agency_id,agency_name,agency_url,agency_timezone\nAG1,Test,https://x.com,UTC\n");
        Files.writeString(feedDir.resolve("stops.txt"),
                "stop_id,stop_name,stop_lat,stop_lon\n" +
                "S1,Alpha,45.500000,-73.500000\n" +
                "S2,Beta,45.510000,-73.510000\n");
        Files.writeString(feedDir.resolve("routes.txt"),
                "route_id,route_short_name,route_type\nR1,1,3\nR2,2,0\n");
        Files.writeString(feedDir.resolve("trips.txt"),
                "trip_id,route_id,service_id,direction_id\n" +
                "T1,R1,SVC,0\nT2,R1,SVC,1\nT3,R2,SVC,0\n");
        Files.writeString(feedDir.resolve("stop_times.txt"),
                "trip_id,stop_id,stop_sequence,arrival_time,departure_time\n" +
                "T1,S1,1,08:00:00,08:00:00\nT1,S2,2,08:10:00,08:10:00\n" +
                "T2,S2,1,09:00:00,09:00:00\nT2,S1,2,09:10:00,09:10:00\n" +
                "T3,S1,1,10:00:00,10:00:00\nT3,S2,2,10:12:00,10:12:00\n");
        Files.writeString(feedDir.resolve("calendar.txt"),
                "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date\n" +
                "SVC,1,1,1,1,1,0,0,20260101,20260131\n");

        GtfsImportConfig config = GtfsImportConfig.forFolder(feedDir,
                GtfsImportConfig.ServiceDateSelection.DAY_WITH_MOST_TRIPS);
        GtfsFeedSet feeds = new GtfsImporter().read(config);
        TransitSchedule built = new GtfsTransitScheduleBuilder().build(feeds, config).schedule();

        String xml = new TransitScheduleXmlWriter().writeToString(built);
        TransitSchedule reread = new TransitScheduleXmlReader().read(xml);

        int builtRoutes = 0;
        int builtDepartures = 0;
        int builtStops = 0;
        Map<String, Integer> departuresPerRoute = new LinkedHashMap<>();
        for (TransitLine line : built.getTransitLines().values()) {
            for (TransitRoute route : line.getRoutes().values()) {
                builtRoutes++;
                builtStops += route.getStops().size();
                departuresPerRoute.put(route.getId().toString(), route.getDepartures().size());
                builtDepartures += route.getDepartures().size();
            }
        }
        int rereadRoutes = 0;
        int rereadDepartures = 0;
        int rereadStops = 0;
        Map<String, Integer> rereadDeparturesPerRoute = new LinkedHashMap<>();
        for (TransitLine line : reread.getTransitLines().values()) {
            for (TransitRoute route : line.getRoutes().values()) {
                rereadRoutes++;
                rereadStops += route.getStops().size();
                rereadDeparturesPerRoute.put(route.getId().toString(), route.getDepartures().size());
                rereadDepartures += route.getDepartures().size();
            }
        }

        assertTrue(builtRoutes > 1, "fixture must exercise multiple routes");
        assertTrue(builtDepartures > 0, "fixture must produce departures");
        assertEquals(builtRoutes, rereadRoutes);
        assertEquals(builtStops, rereadStops);
        assertEquals(builtDepartures, rereadDepartures);
        assertEquals(departuresPerRoute, rereadDeparturesPerRoute);

        // Departure times and vehicle references survive individually.
        for (TransitLine line : built.getTransitLines().values()) {
            for (TransitRoute route : line.getRoutes().values()) {
                TransitRoute restoredRoute = findRoute(reread, route.getId().toString());
                assertNotNull(restoredRoute, "route must survive: " + route.getId());
                for (Departure departure : route.getDepartures().values()) {
                    Departure restored = restoredRoute.getDepartures().get(departure.getId());
                    assertNotNull(restored, "departure must survive: " + departure.getId());
                    assertEquals(departure.getDepartureTime(), restored.getDepartureTime());
                    assertEquals(departure.getVehicleId(), restored.getVehicleId());
                }
                for (TransitRouteStop stop : route.getStops()) {
                    assertEquals(stop.getArrivalOffset(), findStop(restoredRoute, stop).getArrivalOffset());
                    assertEquals(stop.getDepartureOffset(), findStop(restoredRoute, stop).getDepartureOffset());
                }
            }
        }
    }

    private static TransitRoute findRoute(TransitSchedule schedule, String routeId) {
        for (TransitLine line : schedule.getTransitLines().values()) {
            TransitRoute route = line.getRoutes().get(Id.create(routeId, TransitRoute.class));
            if (route != null) {
                return route;
            }
        }
        return null;
    }

    private static TransitRouteStop findStop(TransitRoute route, TransitRouteStop stop) {
        for (TransitRouteStop candidate : route.getStops()) {
            if (candidate.getStopFacilityId().equals(stop.getStopFacilityId())) {
                return candidate;
            }
        }
        throw new AssertionError("stop not found after round-trip: " + stop.getStopFacilityId());
    }

    @Test
    void coordinatesAreStableAcrossMultipleRoundTrips() {
        // Directly exercise the coordinate contract without GTFS: two reads/writes must be a fixed
        // point so a formatting asymmetry cannot silently drift coordinates.
        TransitSchedule schedule = new TransitSchedule();
        TransitStopFacility facility = new TransitStopFacility(
                Id.create("s1", TransitStopFacility.class), new Coord(-73.5, 45.5), false);
        facility.setName("Alpha");
        schedule.addStopFacility(facility);

        TransitScheduleXmlWriter writer = new TransitScheduleXmlWriter();
        TransitScheduleXmlReader reader = new TransitScheduleXmlReader();

        String xml1 = writer.writeToString(schedule);
        TransitSchedule read1 = reader.read(xml1);
        String xml2 = writer.writeToString(read1);
        TransitSchedule read2 = reader.read(xml2);

        Coord restored = read2.getFacilities().get(Id.create("s1", TransitStopFacility.class)).getCoord();
        assertEquals(-73.5, restored.getX());
        assertEquals(45.5, restored.getY());
        assertEquals(xml1, xml2);
    }
}

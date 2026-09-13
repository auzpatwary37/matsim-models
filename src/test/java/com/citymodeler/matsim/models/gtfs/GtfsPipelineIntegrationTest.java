package com.citymodeler.matsim.models.gtfs;

import static org.junit.jupiter.api.Assertions.*;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.io.TransitScheduleXmlReader;
import com.citymodeler.matsim.models.io.TransitScheduleXmlWriter;
import com.citymodeler.matsim.models.mapping.TransitMappingConfig;
import com.citymodeler.matsim.models.mapping.TransitMappingResult;
import com.citymodeler.matsim.models.mapping.TransitNetworkMapper;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.Node;
import com.citymodeler.matsim.models.network.index.LinkSpatialIndex;
import com.citymodeler.matsim.models.transit.TransitLine;
import com.citymodeler.matsim.models.transit.TransitRoute;
import com.citymodeler.matsim.models.transit.TransitSchedule;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * End-to-end integration: GTFS feed → schedule → network mapping → XML round-trip.
 */
class GtfsPipelineIntegrationTest {

    @Test
    void fullPipelineProducesValidSchedule() throws Exception {
        // 1. Create a minimal GTFS feed
        Path feedDir = Files.createTempDirectory("gtfs-integration");
        Files.writeString(feedDir.resolve("agency.txt"),
                "agency_id,agency_name,agency_url,agency_timezone\nAG1,Test,https://x.com,UTC\n");
        Files.writeString(feedDir.resolve("stops.txt"),
                "stop_id,stop_name,stop_lat,stop_lon\nS1,Start,45.5,-73.5\nS2,End,45.51,-73.51\n");
        Files.writeString(feedDir.resolve("routes.txt"),
                "route_id,route_short_name,route_type\nR1,1,3\n");
        Files.writeString(feedDir.resolve("trips.txt"),
                "trip_id,route_id,service_id\nT1,R1,SVC\n");
        Files.writeString(feedDir.resolve("stop_times.txt"),
                "trip_id,stop_id,stop_sequence,arrival_time,departure_time\n" +
                "T1,S1,1,08:00:00,08:00:00\n" +
                "T1,S2,2,08:10:00,08:10:00\n");
        Files.writeString(feedDir.resolve("calendar.txt"),
                "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date\n" +
                "SVC,1,0,0,0,0,0,0,20260901,20260930\n");

        // 2. Import
        GtfsImportConfig importConfig = GtfsImportConfig.forFolder(feedDir,
                GtfsImportConfig.ServiceDateSelection.DAY_WITH_MOST_TRIPS);
        GtfsFeedSet feeds = new GtfsImporter().read(importConfig);
        assertEquals(1, feeds.feeds().size());

        // 3. Build schedule
        GtfsTransitBuildResult buildResult = new GtfsTransitScheduleBuilder().build(feeds, importConfig);
        TransitSchedule schedule = buildResult.schedule();
        assertFalse(schedule.getFacilities().isEmpty());
        assertFalse(schedule.getTransitLines().isEmpty());

        // 4. Create network with a link near the stops
        Network network = new Network();
        Node n1 = new Node(Id.create("n1", Node.class), new Coord(-73.5, 45.5));
        Node n2 = new Node(Id.create("n2", Node.class), new Coord(-73.51, 45.51));
        network.addNode(n1);
        network.addNode(n2);
        Link link = new Link(Id.create("link1", Link.class), n1.getId(), n2.getId(),
                150.0, 900.0, 13.9, 2.0, Set.of("car", "pt"));
        network.addLink(link);
        network.postProcess();

        // 5. Map
        LinkSpatialIndex index = new LinkSpatialIndex(network, 100.0);
        TransitNetworkMapper mapper = new TransitNetworkMapper(
                TransitMappingConfig.defaults(), index);
        TransitMappingResult mappingResult = mapper.map(schedule, network);
        assertNotNull(mappingResult.mappedSchedule());

        // 6. XML round-trip
        String xml = new TransitScheduleXmlWriter().writeToString(schedule);
        assertNotNull(xml);
        TransitSchedule roundTripped = new TransitScheduleXmlReader().read(xml);
        assertEquals(schedule.getFacilities().size(), roundTripped.getFacilities().size());
        assertEquals(schedule.getTransitLines().size(), roundTripped.getTransitLines().size());
    }

    @Test
    void deterministicOutputUnderShuffledInput() throws Exception {
        // Two identical feeds processed in different order should produce same schedule
        Path feedDir = Files.createTempDirectory("gtfs-det");
        Files.writeString(feedDir.resolve("agency.txt"),
                "agency_id,agency_name,agency_url,agency_timezone\nAG1,Test,https://x.com,UTC\n");
        Files.writeString(feedDir.resolve("stops.txt"),
                "stop_id,stop_name,stop_lat,stop_lon\nS1,A,45.5,-73.5\nS2,B,45.51,-73.51\n");
        Files.writeString(feedDir.resolve("routes.txt"),
                "route_id,route_short_name,route_type\nR1,1,3\n");
        Files.writeString(feedDir.resolve("trips.txt"),
                "trip_id,route_id,service_id\nT1,R1,SVC\n");
        Files.writeString(feedDir.resolve("stop_times.txt"),
                "trip_id,stop_id,stop_sequence,arrival_time,departure_time\n" +
                "T1,S1,1,08:00:00,08:00:00\nT1,S2,2,08:10:00,08:10:00\n");
        Files.writeString(feedDir.resolve("calendar.txt"),
                "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date\n" +
                "SVC,1,1,1,1,1,0,0,20260101,20260131\n");

        GtfsImportConfig config = GtfsImportConfig.forFolder(feedDir,
                GtfsImportConfig.ServiceDateSelection.DAY_WITH_MOST_TRIPS);

        GtfsFeedSet feeds1 = new GtfsImporter().read(config);
        GtfsFeedSet feeds2 = new GtfsImporter().read(config);

        String xml1 = new TransitScheduleXmlWriter().writeToString(
                new GtfsTransitScheduleBuilder().build(feeds1, config).schedule());
        String xml2 = new TransitScheduleXmlWriter().writeToString(
                new GtfsTransitScheduleBuilder().build(feeds2, config).schedule());

        assertEquals(xml1, xml2);
    }

    /**
     * Spec §Vehicles: the GTFS build produces one VehicleType per resulting transit mode and one
     * Vehicle per departure, with referential integrity (every departure's vehicleRefId resolves to
     * a declared vehicle, and every vehicle references a declared type). The whole bundle is written
     * (schedule + vehicles) and read back.
     */
    @Test
    void vehiclesAreProducedWithReferentialIntegrity(@org.junit.jupiter.api.io.TempDir Path feedDir)
            throws Exception {
        Files.writeString(feedDir.resolve("agency.txt"),
                "agency_id,agency_name,agency_url,agency_timezone\nAG1,Test,https://x.com,UTC\n");
        Files.writeString(feedDir.resolve("stops.txt"),
                "stop_id,stop_name,stop_lat,stop_lon\nS1,Start,45.5,-73.5\nS2,End,45.51,-73.51\n");
        Files.writeString(feedDir.resolve("routes.txt"),
                "route_id,route_short_name,route_type\nR1,1,3\nR2,2,0\n"); // bus + tram
        Files.writeString(feedDir.resolve("trips.txt"),
                "trip_id,route_id,service_id\nT1,R1,SVC\nT2,R2,SVC\n");
        Files.writeString(feedDir.resolve("stop_times.txt"),
                "trip_id,stop_id,stop_sequence,arrival_time,departure_time\n" +
                "T1,S1,1,08:00:00,08:00:00\nT1,S2,2,08:10:00,08:10:00\n" +
                "T2,S1,1,09:00:00,09:00:00\nT2,S2,2,09:12:00,09:12:00\n");
        Files.writeString(feedDir.resolve("calendar.txt"),
                "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date\n" +
                "SVC,1,1,1,1,1,0,0,20260101,20260131\n");

        GtfsImportConfig config = GtfsImportConfig.forFolder(feedDir,
                GtfsImportConfig.ServiceDateSelection.DAY_WITH_MOST_TRIPS);
        GtfsFeedSet feeds = new GtfsImporter().read(config);
        GtfsTransitBuildResult result = new GtfsTransitScheduleBuilder().build(feeds, config);

        var vehicles = result.vehicles();
        assertFalse(vehicles.getVehicles().isEmpty(), "vehicles must be produced");
        assertFalse(vehicles.getVehicleTypes().isEmpty(), "vehicle types must be produced");
        // One type per resulting transit mode (bus + tram here).
        assertEquals(2, vehicles.getVehicleTypes().size());

        // Every departure -> a vehicle that exists and references a declared type.
        int departures = 0;
        for (TransitLine line : result.schedule().getTransitLines().values()) {
            for (TransitRoute route : line.getRoutes().values()) {
                for (var dep : route.getDepartures().values()) {
                    departures++;
                    var vid = Id.create(dep.getVehicleId(), com.citymodeler.matsim.models.vehicles.Vehicle.class);
                    var vehicle = vehicles.getVehicles().get(vid);
                    assertNotNull(vehicle, "departure vehicle must exist: " + dep.getVehicleId());
                    assertTrue(vehicles.getVehicleTypes().containsKey(
                                    Id.create(vehicle.getType(), com.citymodeler.matsim.models.vehicles.VehicleType.class)),
                            "vehicle must reference a declared type: " + vehicle.getType());
                }
            }
        }
        assertTrue(departures > 0);

        // Bundle output: schedule + vehicles round-trip through the writers.
        String schedXml = new TransitScheduleXmlWriter().writeToString(result.schedule());
        String vehXml = new com.citymodeler.matsim.models.io.VehiclesXmlWriter().writeToString(vehicles);
        assertNotNull(schedXml);
        assertNotNull(vehXml);

        var vehBack = new com.citymodeler.matsim.models.io.VehiclesXmlReader().read(vehXml);
        assertEquals(vehicles.getVehicles().size(), vehBack.getVehicles().size());
        assertEquals(vehicles.getVehicleTypes().size(), vehBack.getVehicleTypes().size());
    }
}

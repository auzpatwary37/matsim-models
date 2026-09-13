package com.citymodeler.matsim.models.gtfs;

import static org.junit.jupiter.api.Assertions.*;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.transit.Departure;
import com.citymodeler.matsim.models.transit.TransitLine;
import com.citymodeler.matsim.models.transit.TransitRoute;
import com.citymodeler.matsim.models.vehicles.Vehicle;
import com.citymodeler.matsim.models.vehicles.VehicleDefinitions;
import com.citymodeler.matsim.models.vehicles.VehicleType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Referential integrity of the GTFS build's {@link VehicleDefinitions}: every departure's
 * {@code vehicleRefId} resolves to a declared vehicle, and every declared vehicle's type resolves to
 * a declared vehicle type. This is the vehicle-side analogue of the schedule's facility references.
 */
class VehicleDefinitionsTest {

    @Test
    void everyDepartureResolvesToVehicleAndVehicleType(@TempDir Path feedDir) throws Exception {
        // Two routes -> two transit modes (bus=3, tram=0) -> two vehicle types; multiple trips per
        // route so several vehicles/departures must resolve.
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
        GtfsTransitBuildResult result = new GtfsTransitScheduleBuilder().build(feeds, config);

        VehicleDefinitions vehicles = result.vehicles();
        assertFalse(vehicles.getVehicles().isEmpty(), "vehicles must be produced");
        assertFalse(vehicles.getVehicleTypes().isEmpty(), "vehicle types must be produced");
        assertEquals(2, vehicles.getVehicleTypes().size(),
                "one vehicle type per resulting transit mode (bus + tram)");

        Map<Id<VehicleType>, VehicleType> typesById = new LinkedHashMap<>(vehicles.getVehicleTypes());
        Map<String, Long> departureCountByVehicle = new LinkedHashMap<>();

        int departures = 0;
        for (TransitLine line : result.schedule().getTransitLines().values()) {
            for (TransitRoute route : line.getRoutes().values()) {
                for (Departure departure : route.getDepartures().values()) {
                    departures++;
                    String vehicleRef = departure.getVehicleId();
                    assertNotNull(vehicleRef, "departure must carry a vehicleRefId: " + departure.getId());

                    // (1) departure -> vehicle resolves
                    Vehicle vehicle = vehicles.getVehicles()
                            .get(Id.create(vehicleRef, Vehicle.class));
                    assertNotNull(vehicle,
                            "departure " + departure.getId() + " references missing vehicle " + vehicleRef);

                    // (2) vehicle -> vehicle type resolves
                    String typeId = vehicle.getType();
                    assertNotNull(typeId, "vehicle must declare a type: " + vehicle.getId());
                    VehicleType type = typesById.get(Id.create(typeId, VehicleType.class));
                    assertNotNull(type,
                            "vehicle " + vehicle.getId() + " references missing type " + typeId);

                    departureCountByVehicle.merge(vehicleRef, 1L, Long::sum);
                }
            }
        }
        assertTrue(departures > 0, "fixture must produce departures");

        // Every declared vehicle is actually referenced by at least one departure (no orphans), and
        // the mapping is one vehicle per departure id (vehicle id == departure id).
        assertEquals(vehicles.getVehicles().size(), departureCountByVehicle.size(),
                "every declared vehicle must be referenced by a departure");
        for (String vehicleRef : departureCountByVehicle.keySet()) {
            assertTrue(vehicles.getVehicles().containsKey(Id.create(vehicleRef, Vehicle.class)),
                    "referenced vehicle must be declared: " + vehicleRef);
        }
    }

    @Test
    void vehiclesAndTypesSurviveXmlRoundTripWithReferencesIntact(@TempDir Path feedDir) throws Exception {
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
        GtfsFeedSet feeds = new GtfsImporter().read(config);
        VehicleDefinitions vehicles = new GtfsTransitScheduleBuilder().build(feeds, config).vehicles();

        var writer = new com.citymodeler.matsim.models.io.VehiclesXmlWriter();
        var reader = new com.citymodeler.matsim.models.io.VehiclesXmlReader();
        VehicleDefinitions reread = reader.read(writer.writeToString(vehicles));

        assertEquals(vehicles.getVehicles().size(), reread.getVehicles().size());
        assertEquals(vehicles.getVehicleTypes().size(), reread.getVehicleTypes().size());

        Map<Id<VehicleType>, VehicleType> rereadTypes = new HashMap<>(reread.getVehicleTypes());
        for (Vehicle original : vehicles.getVehicles().values()) {
            Vehicle restored = reread.getVehicles().get(original.getId());
            assertNotNull(restored, "vehicle must survive round-trip: " + original.getId());
            assertEquals(original.getType(), restored.getType());
            assertTrue(rereadTypes.containsKey(Id.create(restored.getType(), VehicleType.class)),
                    "restored vehicle type must resolve: " + restored.getType());
        }
    }
}

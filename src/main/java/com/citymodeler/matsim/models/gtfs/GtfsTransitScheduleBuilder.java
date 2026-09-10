package com.citymodeler.matsim.models.gtfs;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.gtfs.GtfsDepartureBuilder.DepartureProfile;
import com.citymodeler.matsim.models.gtfs.GtfsTransitBuildResult.SelectedDate;
import com.citymodeler.matsim.models.gtfs.GtfsTransitBuildResult.VehicleDef;
import com.citymodeler.matsim.models.transit.Departure;
import com.citymodeler.matsim.models.transit.TransitLine;
import com.citymodeler.matsim.models.transit.TransitRoute;
import com.citymodeler.matsim.models.transit.TransitRouteStop;
import com.citymodeler.matsim.models.transit.TransitSchedule;
import com.citymodeler.matsim.models.transit.TransitStopFacility;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Produces a {@link TransitSchedule} from GTFS feeds.
 */
public final class GtfsTransitScheduleBuilder {

    public GtfsTransitBuildResult build(GtfsFeedSet feeds, GtfsImportConfig config) {
        List<String> warnings = new ArrayList<>();
        Map<String, List<SelectedDate>> selectedDatesByFeed = new LinkedHashMap<>();

        Map<String, Set<LocalDate>> datesByFeed = new LinkedHashMap<>();
        for (GtfsFeed feed : feeds.allFeeds()) {
            Set<LocalDate> dates = GtfsServiceSelector.selectDates(feed, config);
            datesByFeed.put(feed.feedId(), dates);
            for (LocalDate d : dates) {
                selectedDatesByFeed.computeIfAbsent(feed.feedId(), k -> new ArrayList<>())
                        .add(new SelectedDate(feed.feedId(), d));
            }
        }

        TransitSchedule schedule = new TransitSchedule();

        // Add stop facilities (only platforms with coordinates)
        for (GtfsFeed feed : feeds.allFeeds()) {
            for (Map.Entry<String, GtfsStop> entry : feed.stops().entrySet()) {
                GtfsStop stop = entry.getValue();
                if (!stop.hasCoordinates()) continue;
                if (stop.locationType() != 0) continue;
                String facId = feed.prefixedStopId(stop.id());
                Coord coord = new Coord(stop.lon(), stop.lat());
                TransitStopFacility facility = new TransitStopFacility(
                        Id.create(facId, TransitStopFacility.class), coord, false);
                facility.setName(stop.name());
                facility.getAttributes().putAttribute("gtfs:feedId", feed.feedId());
                facility.getAttributes().putAttribute("gtfs:agencyId", feed.agencyId() != null ? feed.agencyId() : "");
                schedule.addStopFacility(facility);
            }
        }

        // Group trips by (routeId, directionId, stopSequence)
        Map<String, List<GroupEntry>> groups = new LinkedHashMap<>();
        for (GtfsFeed feed : feeds.allFeeds()) {
            for (GtfsTrip trip : feed.trips().values()) {
                GtfsRoute route = feed.routes().get(trip.routeId());
                if (route == null) continue;
                List<GtfsStopTime> stopTimes = feed.stopTimesByTrip().get(trip.id());
                if (stopTimes == null || stopTimes.isEmpty()) continue;

                String mode = GtfsModeMapper.map(route.routeType());
                if (!GtfsModeMapper.isKnown(route.routeType())) {
                    warnings.add(feed.feedId() + ": unknown route_type " + route.routeType()
                            + " for route " + route.id());
                }

                StringBuilder sb = new StringBuilder();
                for (GtfsStopTime st : stopTimes) {
                    if (sb.length() > 0) sb.append('|');
                    sb.append(feed.prefixedStopId(st.stopId()));
                }
                String groupKey = trip.routeId() + "|" + trip.effectiveDirectionId() + "|" + sb;
                groups.computeIfAbsent(groupKey, k -> new ArrayList<>())
                        .add(new GroupEntry(feed, trip, route, mode, stopTimes));
            }
        }

        // Build lines and routes
        List<VehicleDef> vehicles = new ArrayList<>();
        int routeCounter = 0;
        Map<Id<TransitLine>, TransitLine> lineCache = new LinkedHashMap<>();

        for (List<GroupEntry> groupEntries : groups.values()) {
            if (groupEntries.isEmpty()) continue;
            GroupEntry first = groupEntries.get(0);
            GtfsFeed feed = first.feed();
            GtfsRoute route = first.route();
            String mode = first.mode();

            String lineIdStr = feed.prefixedRouteId(route.id());
            Id<TransitLine> lineId = Id.create(lineIdStr, TransitLine.class);
            TransitLine line = lineCache.get(lineId);
            if (line == null) {
                line = new TransitLine(lineId);
                line.setName(route.shortName() != null ? route.shortName() : route.longName());
                lineCache.put(lineId, line);
                schedule.addTransitLine(line);
            }

            String routeIdStr = lineIdStr + "_r" + routeCounter++;
            TransitRoute transitRoute = new TransitRoute(Id.create(routeIdStr, TransitRoute.class));
            transitRoute.setTransportMode(mode);
            transitRoute.setDescription(route.shortName() != null ? route.shortName() : route.id());

            // Add stops
            List<GtfsStopTime> refStopTimes = first.stopTimes();
            double base = 0;
            if (!refStopTimes.isEmpty() && refStopTimes.get(0).departureTime() != null) {
                base = refStopTimes.get(0).departureTime();
            }
            for (GtfsStopTime st : refStopTimes) {
                GtfsStop stop = feed.stops().get(st.stopId());
                if (stop == null || !stop.hasCoordinates() || stop.locationType() != 0) continue;
                double arrOff = (st.arrivalTime() != null ? st.arrivalTime() : base) - base;
                double depOff = (st.departureTime() != null ? st.departureTime() : base) - base;
                String facId = feed.prefixedStopId(st.stopId());
                TransitRouteStop trs = new TransitRouteStop(
                        Id.create(facId, TransitStopFacility.class),
                        arrOff, depOff,
                        Math.abs(depOff - arrOff) > 0.5);
                transitRoute.addStop(trs);
            }

            // Add departures
            Set<LocalDate> dates = datesByFeed.getOrDefault(feed.feedId(), Set.of());
            for (GroupEntry ge : groupEntries) {
                for (LocalDate date : dates) {
                    String serviceId = ge.trip().serviceId();
                    if (serviceId != null && !GtfsServiceSelector.activeDatesForService(serviceId, feed).contains(date)) {
                        continue;
                    }
                    List<GtfsFrequencyRow> freqs = feed.frequencyRows().stream()
                            .filter(f -> ge.trip().id().equals(f.tripId()))
                            .toList();
                    List<DepartureProfile> profiles = GtfsDepartureBuilder.build(
                            ge.trip(), ge.stopTimes(), freqs, date);
                    for (DepartureProfile profile : profiles) {
                        String depId = feed.feedId() + ":" + profile.departureId();
                        Departure dep = new Departure(Id.create(depId, Departure.class), profile.departureTime());
                        dep.setVehicleId(depId);
                        transitRoute.addDeparture(dep);
                        vehicles.add(new VehicleDef(depId, mode, 45, 30));
                    }
                }
            }

            line.addRoute(transitRoute);
        }

        schedule.postProcess();
        return new GtfsTransitBuildResult(schedule, vehicles, selectedDatesByFeed, warnings);
    }

    private record GroupEntry(GtfsFeed feed, GtfsTrip trip, GtfsRoute route, String mode,
                               List<GtfsStopTime> stopTimes) {
    }
}

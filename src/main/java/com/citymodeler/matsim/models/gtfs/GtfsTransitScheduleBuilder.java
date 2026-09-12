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
        List<String> fatal = new ArrayList<>();
        for (GtfsFeed feed : feeds.allFeeds()) {
            GtfsServiceSelector.ServiceSelection selection = GtfsServiceSelector.select(feed, config);
            warnings.addAll(selection.warnings());
            fatal.addAll(selection.fatal());
            Set<LocalDate> dates = selection.dates();
            datesByFeed.put(feed.feedId(), dates);
            for (LocalDate d : dates) {
                selectedDatesByFeed.computeIfAbsent(feed.feedId(), k -> new ArrayList<>())
                        .add(new SelectedDate(feed.feedId(), d));
            }
        }
        if (!fatal.isEmpty()) {
            throw new GtfsImportException(String.join("; ", fatal));
        }

        TransitSchedule schedule = new TransitSchedule();

        // Add stop facilities (only platforms with coordinates)
        // GTFS provides WGS84 lon/lat; we store them as-is and the mapping layer
        // applies CRS transformation via CrsUtils when matching to the projected network.
        for (GtfsFeed feed : feeds.allFeeds()) {
            for (Map.Entry<String, GtfsStop> entry : feed.stops().entrySet()) {
                GtfsStop stop = entry.getValue();
                if (!stop.hasCoordinates()) continue;
                if (stop.locationType() != 0) continue;
                String facId = feed.prefixedStopId(stop.id());
                // Store WGS84 coordinates; mapping layer projects them
                Coord coord = new Coord(stop.lon(), stop.lat());
                TransitStopFacility facility = new TransitStopFacility(
                        Id.create(facId, TransitStopFacility.class), coord, false);
                facility.setName(stop.name());
                facility.getAttributes().putAttribute("gtfs:feedId", feed.feedId());
                facility.getAttributes().putAttribute("gtfs:agencyId", feed.agencyId() != null ? feed.agencyId() : "");
                facility.getAttributes().putAttribute("gtfs:lon", stop.lon());
                facility.getAttributes().putAttribute("gtfs:lat", stop.lat());
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
                // Review #8/#9: normalize offsets once (linearly interpolating non-timepoints) and
                // use that same vector for BOTH the grouping key and the route stops. The key uses the
                // exact double values (not Math.round) so sub-second-different profiles are not
                // silently coalesced.
                List<GtfsDepartureBuilder.StopOffsets> offsets = normalizedOffsets(stopTimes);
                StringBuilder offsetSb = new StringBuilder();
                for (GtfsDepartureBuilder.StopOffsets so : offsets) {
                    if (offsetSb.length() > 0) offsetSb.append('|');
                    offsetSb.append(Double.toString(so.arrivalOffset()))
                            .append(',').append(Double.toString(so.departureOffset()));
                }
                String groupKey = trip.routeId() + "|" + trip.effectiveDirectionId() + "|" + sb + "|" + offsetSb;
                groups.computeIfAbsent(groupKey, k -> new ArrayList<>())
                        .add(new GroupEntry(feed, trip, route, mode, stopTimes, offsets));
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

            // Add stops using the SAME normalized (interpolated) offset vector that formed the
            // grouping key (review #8), so the written TransitRoute reflects the interpolated
            // timing profile rather than the raw, partially-missing stop_times values.
            List<GtfsStopTime> refStopTimes = first.stopTimes();
            List<GtfsDepartureBuilder.StopOffsets> offsets = first.offsets();
            for (int i = 0; i < refStopTimes.size(); i++) {
                GtfsStopTime st = refStopTimes.get(i);
                GtfsStop stop = feed.stops().get(st.stopId());
                if (stop == null || !stop.hasCoordinates() || stop.locationType() != 0) continue;
                GtfsDepartureBuilder.StopOffsets so = offsets.get(i);
                String facId = feed.prefixedStopId(st.stopId());
                TransitRouteStop trs = new TransitRouteStop(
                        Id.create(facId, TransitStopFacility.class),
                        so.arrivalOffset(), so.departureOffset(), so.awaitDeparture());
                transitRoute.addStop(trs);
            }

            // Review #10: exact_times. We deliberately expand both exact (1) and approximate (0)
            // frequency service identically into headway departures; the original distinction is
            // preserved in route metadata so downstream consumers are not misled. Overlapping
            // frequency rows for a trip are warned about per the GTFS contract.
            for (GroupEntry ge : groupEntries) {
                List<GtfsFrequencyRow> geFreqs = ge.feed().frequencyRows().stream()
                        .filter(f -> ge.trip().id().equals(f.tripId()))
                        .toList();
                validateFrequencyOverlaps(ge.feed().feedId(), ge.trip().id(), geFreqs, warnings);
            }
            List<GtfsFrequencyRow> refFreqs = feed.frequencyRows().stream()
                    .filter(f -> first.trip().id().equals(f.tripId()))
                    .toList();
            if (!refFreqs.isEmpty()) {
                int strictestExact = refFreqs.stream().mapToInt(GtfsFrequencyRow::exactTimes).min().orElse(1);
                transitRoute.getAttributes().putAttribute("gtfs:exact_times", strictestExact);
            }

            // Add departures
            Set<LocalDate> dates = datesByFeed.getOrDefault(feed.feedId(), Set.of());
            for (GroupEntry ge : groupEntries) {
                for (LocalDate date : dates) {
                    String serviceId = ge.trip().serviceId();
                    // Same policy as date selection (Review #1): an always-active feed must not be
                    // filtered out here just because it has no calendar rows.
                    if (!GtfsServiceSelector.serviceActiveOnDate(feed, serviceId, date, config)) {
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
                               List<GtfsStopTime> stopTimes,
                               List<GtfsDepartureBuilder.StopOffsets> offsets) {
    }

    /**
     * Normalized offset vector for a trip's stop_times: linearly interpolates non-timepoint stops and
     * bases all offsets on the first departure. Falls back to raw offsets if interpolation is
     * ill-formed for a malformed trip.
     */
    private static List<GtfsDepartureBuilder.StopOffsets> normalizedOffsets(List<GtfsStopTime> stopTimes) {
        double base = stopTimes.get(0).departureTime() != null ? stopTimes.get(0).departureTime() : 0;
        try {
            return GtfsDepartureBuilder.interpolateOffsets(stopTimes, base);
        } catch (RuntimeException e) {
            List<GtfsDepartureBuilder.StopOffsets> raw = new ArrayList<>();
            for (GtfsStopTime st : stopTimes) {
                double arrOff = (st.arrivalTime() != null ? st.arrivalTime() : base) - base;
                double depOff = (st.departureTime() != null ? st.departureTime() : base) - base;
                raw.add(new GtfsDepartureBuilder.StopOffsets(arrOff, depOff,
                        Math.abs(depOff - arrOff) > 0.5));
            }
            return raw;
        }
    }

    /** Review #10: GTFS frequencies.txt rows for a trip must not overlap; warn when they do. */
    private static void validateFrequencyOverlaps(String feedId, String tripId,
                                                    List<GtfsFrequencyRow> freqs,
                                                    List<String> warnings) {
        for (int i = 0; i < freqs.size(); i++) {
            for (int j = i + 1; j < freqs.size(); j++) {
                GtfsFrequencyRow a = freqs.get(i);
                GtfsFrequencyRow b = freqs.get(j);
                boolean overlap = Math.max(a.startTime(), b.startTime())
                        < Math.min(a.endTime(), b.endTime());
                if (overlap) {
                    warnings.add(feedId + ": overlapping frequency rows for trip " + tripId
                            + " [" + a.startTime() + "," + a.endTime() + "] and ["
                            + b.startTime() + "," + b.endTime() + "]");
                }
            }
        }
    }
}

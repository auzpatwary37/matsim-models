package com.citymodeler.matsim.models.mapping;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.index.LinkSpatialIndex;
import com.citymodeler.matsim.models.transit.TransitLine;
import com.citymodeler.matsim.models.transit.TransitRoute;
import com.citymodeler.matsim.models.transit.TransitRouteStop;
import com.citymodeler.matsim.models.transit.TransitSchedule;
import com.citymodeler.matsim.models.transit.TransitStopFacility;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Exports the stop-to-link mapping as plain-text GeoJSON: a {@code FeatureCollection} of {@code Point}
 * features, one per stop facility, exposing the mapped link id, the candidate link ids and the stop
 * coordinates as properties.
 *
 * <p>Output is deterministic: facilities are emitted in ascending facility-id order and candidate
 * link ids are sorted ascending. The writer does not require any GeoJSON dependency —
 * {@link ObjectMapper} is used only for escaping string values and assembling valid JSON.</p>
 */
public final class MappingGeoJsonWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final double SPATIAL_CELL_SIZE = 100.0;

    public void write(TransitSchedule schedule, Network network, Path path) {
        try {
            Files.writeString(path, writeToString(schedule, network), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new com.citymodeler.matsim.models.io.MatsimWriteException(
                    "Failed to write GeoJSON to " + path, e);
        }
    }

    public void write(TransitSchedule schedule, Network network, OutputStream outputStream) {
        try {
            outputStream.write(writeToString(schedule, network).getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        } catch (IOException e) {
            throw new com.citymodeler.matsim.models.io.MatsimWriteException(
                    "Failed to write GeoJSON", e);
        }
    }

    /**
     * Two-argument form: candidate links are emitted for each facility using the transport mode of
     * the first route that references it (in sorted line/route id order), or omitted when no route
     * references the facility.
     */
    public String writeToString(TransitSchedule schedule, Network network) {
        Map<String, String> modeByFacility = modeByFacility(schedule);
        return writeToString(schedule, network, modeByFacility);
    }

    /**
     * Explicit-mode form: candidates for every facility are scored with the supplied mode. Pass
     * {@code null} to omit candidates entirely.
     */
    public String writeToString(TransitSchedule schedule, Network network, String mode) {
        Map<String, String> modeByFacility = new TreeMap<>();
        if (mode != null) {
            for (Id<TransitStopFacility> id : schedule.getFacilities().keySet()) {
                modeByFacility.put(id.toString(), mode);
            }
        }
        return writeToString(schedule, network, modeByFacility);
    }

    private String writeToString(TransitSchedule schedule, Network network,
                                 Map<String, String> modeByFacility) {
        Map<String, Object> collection = new LinkedHashMap<>();
        collection.put("type", "FeatureCollection");

        Map<Id<TransitStopFacility>, TransitStopFacility> sorted =
                new TreeMap<>(java.util.Comparator.comparing(Id::toString));
        sorted.putAll(schedule.getFacilities());

        boolean anyMode = modeByFacility.values().stream().anyMatch(m -> m != null);
        StopCandidateScorer scorer = anyMode
                ? new StopCandidateScorer(TransitMappingConfig.defaults(),
                        new LinkSpatialIndex(network, SPATIAL_CELL_SIZE), network)
                : null;
        CrsUtils.Projector projector = CrsUtils.forCrs(CrsUtils.networkTargetCrs(network));

        List<Object> features = new ArrayList<>();
        for (Map.Entry<Id<TransitStopFacility>, TransitStopFacility> entry : sorted.entrySet()) {
            TransitStopFacility facility = entry.getValue();
            Coord coord = facility.getCoord();
            if (coord == null) {
                continue;
            }

            // GeoJSON is WGS84 lon/lat by contract (spec Part 4). Prefer the source GTFS lon/lat when
            // present; otherwise inverse-project the network-CRS coordinate back to WGS84 so projected
            // metres are never written into a .geojson as if they were degrees.
            Coord wgs84 = wgs84(facility, coord, projector);

            Map<String, Object> geometry = new LinkedHashMap<>();
            geometry.put("type", "Point");
            geometry.put("coordinates", List.of(wgs84.getX(), wgs84.getY()));

            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("facilityId", entry.getKey().toString());
            Id<Link> linkId = facility.getLinkId();
            properties.put("linkId", linkId == null ? null : linkId.toString());
            properties.put("name", facility.getName());

            List<String> candidateIds = List.of();
            String mode = modeByFacility.get(entry.getKey().toString());
            if (scorer != null && mode != null) {
                TransitStopFacility projected = projected(facility, projector);
                TreeSet<String> ids = new TreeSet<>();
                for (StopCandidate candidate : scorer.score(projected, mode)) {
                    ids.add(candidate.linkId().toString());
                }
                candidateIds = new ArrayList<>(ids);
            }
            properties.put("candidateLinkIds", candidateIds);

            Map<String, Object> feature = new LinkedHashMap<>();
            feature.put("type", "Feature");
            feature.put("geometry", geometry);
            feature.put("properties", properties);
            features.add(feature);
        }
        collection.put("features", features);

        try {
            return MAPPER.writeValueAsString(collection);
        } catch (IOException e) {
            throw new com.citymodeler.matsim.models.io.MatsimWriteException(
                    "Failed to serialize GeoJSON", e);
        }
    }

    /**
     * Maps each facility to the transport mode of the first (sorted) route that references it.
     * Facilities referenced by no route are absent from the map, so their candidates are omitted.
     */
    private static Map<String, String> modeByFacility(TransitSchedule schedule) {
        Map<String, String> result = new TreeMap<>();
        List<Map.Entry<Id<TransitLine>, TransitLine>> lines =
                new ArrayList<>(schedule.getTransitLines().entrySet());
        lines.sort(Map.Entry.comparingByKey(java.util.Comparator.comparing(Id::toString)));
        for (Map.Entry<Id<TransitLine>, TransitLine> lineEntry : lines) {
            List<Map.Entry<Id<TransitRoute>, TransitRoute>> routes =
                    new ArrayList<>(lineEntry.getValue().getRoutes().entrySet());
            routes.sort(Map.Entry.comparingByKey(java.util.Comparator.comparing(Id::toString)));
            for (Map.Entry<Id<TransitRoute>, TransitRoute> routeEntry : routes) {
                TransitRoute route = routeEntry.getValue();
                String mode = route.getTransportMode() != null ? route.getTransportMode() : "pt";
                for (TransitRouteStop stop : route.getStops()) {
                    result.putIfAbsent(stop.getStopFacilityId().toString(), mode);
                }
            }
        }
        return result;
    }

    /**
     * Returns the facility coordinate in WGS84 lon/lat: the {@code gtfs:lon}/{@code gtfs:lat}
     * attributes when present, else the inverse projection of the network-CRS coordinate.
     */
    private static Coord wgs84(TransitStopFacility facility, Coord coord,
                               CrsUtils.Projector projector) {
        Object lon = facility.getAttributes().getAttribute("gtfs:lon");
        Object lat = facility.getAttributes().getAttribute("gtfs:lat");
        if (lon != null && lat != null) {
            return new Coord(Double.parseDouble(lon.toString()), Double.parseDouble(lat.toString()));
        }
        return projector.unproject(coord.getX(), coord.getY());
    }

    /**
     * Uses the WGS84 {@code gtfs:lon}/{@code gtfs:lat} attributes when present (projecting them into
     * the network CRS); otherwise the facility coordinates are already in the network CRS.
     */
    private static TransitStopFacility projected(TransitStopFacility facility,
                                                 CrsUtils.Projector projector) {
        Object lon = facility.getAttributes().getAttribute("gtfs:lon");
        Object lat = facility.getAttributes().getAttribute("gtfs:lat");
        if (projector == null || lon == null || lat == null) {
            return facility;
        }
        Coord projected = projector.project(Double.parseDouble(lon.toString()),
                Double.parseDouble(lat.toString()));
        TransitStopFacility copy = new TransitStopFacility(facility.getId(), projected,
                facility.isBlockingLane());
        copy.setName(facility.getName());
        copy.setLinkId(facility.getLinkId());
        return copy;
    }
}

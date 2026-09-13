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

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.transit.TransitSchedule;
import com.citymodeler.matsim.models.transit.TransitStopFacility;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Exports the stop-to-link mapping as plain-text GeoJSON: a {@code FeatureCollection} of {@code Point}
 * features, one per stop facility, exposing the mapped link id and stop coordinates as properties.
 *
 * <p>Output is deterministic: facilities are emitted in ascending facility-id order. The writer does
 * not require any GeoJSON dependency — {@link ObjectMapper} is used only for escaping string values
 * and assembling valid JSON.
 */
public final class MappingGeoJsonWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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

    public String writeToString(TransitSchedule schedule, Network network) {
        Map<String, Object> collection = new LinkedHashMap<>();
        collection.put("type", "FeatureCollection");

        Map<Id<TransitStopFacility>, TransitStopFacility> sorted =
                new TreeMap<>(java.util.Comparator.comparing(Id::toString));
        sorted.putAll(schedule.getFacilities());

        List<Object> features = new ArrayList<>();
        for (Map.Entry<Id<TransitStopFacility>, TransitStopFacility> entry : sorted.entrySet()) {
            TransitStopFacility facility = entry.getValue();
            Coord coord = facility.getCoord();
            if (coord == null) {
                continue;
            }

            Map<String, Object> geometry = new LinkedHashMap<>();
            geometry.put("type", "Point");
            geometry.put("coordinates", List.of(coord.getX(), coord.getY()));

            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("facilityId", entry.getKey().toString());
            Id<com.citymodeler.matsim.models.network.Link> linkId = facility.getLinkId();
            properties.put("linkId", linkId == null ? null : linkId.toString());
            properties.put("name", facility.getName());

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
}

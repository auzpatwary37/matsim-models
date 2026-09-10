package com.citymodeler.matsim.models.osm.network;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class OsmGeometryStore {

    private final Map<String, OsmPolyline> geometries;

    public OsmGeometryStore(Map<String, OsmPolyline> geometries) {
        this.geometries = Collections.unmodifiableMap(new TreeMap<>(geometries));
    }

    public Optional<OsmPolyline> geometryForLink(String linkId) {
        return Optional.ofNullable(geometries.get(linkId));
    }

    public Map<String, OsmPolyline> asMap() {
        return geometries;
    }

    public int size() {
        return geometries.size();
    }

    public static OsmGeometryStore empty() {
        return new OsmGeometryStore(Map.of());
    }
}

package com.citymodeler.matsim.models.gtfs;

/**
 * A point from shapes.txt.
 */
public record GtfsShapePoint(
        String shapeId,
        int shapePtSeq,
        double shapePtLat,
        double shapePtLon,
        Integer shapeDistTraveled) {
}

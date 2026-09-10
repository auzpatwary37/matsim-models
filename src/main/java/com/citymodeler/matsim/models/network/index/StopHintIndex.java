package com.citymodeler.matsim.models.network.index;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.osm.network.OsmStopHint;

/**
 * Spatial index over stop hints for proximity queries.
 */
public final class StopHintIndex {

    private final List<OsmStopHint> stops;
    private final double minX, minY, maxX, maxY;

    public StopHintIndex(List<OsmStopHint> stops) {
        this.stops = List.copyOf(stops);
        double mnx = Double.MAX_VALUE, mny = Double.MAX_VALUE;
        double mxx = -Double.MAX_VALUE, mxy = -Double.MAX_VALUE;
        for (OsmStopHint stop : stops) {
            double x = stop.coord().getX();
            double y = stop.coord().getY();
            if (x < mnx) mnx = x;
            if (y < mny) mny = y;
            if (x > mxx) mxx = x;
            if (y > mxy) mxy = y;
        }
        if (mnx > mxx) { mnx = 0; mxx = 0; mny = 0; mxy = 0; }
        this.minX = mnx;
        this.minY = mny;
        this.maxX = mxx;
        this.maxY = mxy;
    }

    public List<String> stopsNear(Coord point, double maxDistance) {
        if (stops.isEmpty()) return List.of();
        double dx = Math.max(Math.abs(point.getX() - minX), Math.abs(point.getX() - maxX));
        double dy = Math.max(Math.abs(point.getY() - minY), Math.abs(point.getY() - maxY));
        if (dx > maxDistance && dy > maxDistance) return List.of();

        List<OsmStopHint> near = new ArrayList<>();
        for (OsmStopHint stop : stops) {
            double dist = Math.sqrt(
                    (point.getX() - stop.coord().getX()) * (point.getX() - stop.coord().getX())
                            + (point.getY() - stop.coord().getY()) * (point.getY() - stop.coord().getY()));
            if (dist <= maxDistance) {
                near.add(stop);
            }
        }
        near.sort(Comparator.comparingDouble(s ->
                (point.getX() - s.coord().getX()) * (point.getX() - s.coord().getX())
                        + (point.getY() - s.coord().getY()) * (point.getY() - s.coord().getY())));
        return near.stream().map(OsmStopHint::osmId).toList();
    }

    public List<OsmStopHint> allStops() {
        return stops;
    }
}

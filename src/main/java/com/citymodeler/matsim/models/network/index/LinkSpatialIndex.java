package com.citymodeler.matsim.models.network.index;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;

/**
 * Uniform grid spatial index for links. Supports nearest-link queries
 * by point-to-segment distance.
 */
public final class LinkSpatialIndex {

    private final double cellSize;
    private final int gridMinX, gridMinY, gridWidth, gridHeight;
    private final List<List<String>> grid;
    private final Network network;

    public LinkSpatialIndex(Network network, double cellSizeMeters) {
        if (cellSizeMeters <= 0) {
            throw new IllegalArgumentException("cellSizeMeters must be > 0");
        }
        this.cellSize = cellSizeMeters;
        this.network = network;

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (var entry : network.getNodes().entrySet()) {
            double x = entry.getValue().getCoord().getX();
            double y = entry.getValue().getCoord().getY();
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
        }
        if (minX > maxX) { minX = 0; maxX = 0; minY = 0; maxY = 0; }

        this.gridMinX = (int) (minX / cellSize);
        this.gridMinY = (int) (minY / cellSize);
        this.gridWidth = (int) ((maxX - minX) / cellSize) + 2;
        this.gridHeight = (int) ((maxY - minY) / cellSize) + 2;
        this.grid = new ArrayList<>(gridWidth * gridHeight);
        for (int i = 0; i < gridWidth * gridHeight; i++) {
            grid.add(new ArrayList<>());
        }

        for (var entry : network.getLinks().entrySet()) {
            Link link = entry.getValue();
            String linkId = entry.getKey().toString();
            double x1 = link.getFromNode().getCoord().getX();
            double y1 = link.getFromNode().getCoord().getY();
            double x2 = link.getToNode().getCoord().getX();
            double y2 = link.getToNode().getCoord().getY();
            int minGX = Math.max(0, (int) (Math.min(x1, x2) / cellSize) - gridMinX);
            int maxGX = Math.min(gridWidth - 1, (int) (Math.max(x1, x2) / cellSize) - gridMinX);
            int minGY = Math.max(0, (int) (Math.min(y1, y2) / cellSize) - gridMinY);
            int maxGY = Math.min(gridHeight - 1, (int) (Math.max(y1, y2) / cellSize) - gridMinY);
            for (int gx = minGX; gx <= maxGX; gx++) {
                for (int gy = minGY; gy <= maxGY; gy++) {
                    grid.get(gy * gridWidth + gx).add(linkId);
                }
            }
        }
    }

    public List<NearestLink> nearestLinks(Coord query, int maxResults, double maxDistance) {
        int cx = (int) (query.getX() / cellSize) - gridMinX;
        int cy = (int) (query.getY() / cellSize) - gridMinY;
        int radius = (int) (maxDistance / cellSize) + 1;

        java.util.Set<String> seen = new java.util.HashSet<>();
        List<NearestLink> candidates = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                int gx = cx + dx;
                int gy = cy + dy;
                if (gx < 0 || gx >= gridWidth || gy < 0 || gy >= gridHeight) continue;
                for (String linkId : grid.get(gy * gridWidth + gx)) {
                    if (!seen.add(linkId)) continue;
                    var id = Id.create(linkId, Link.class);
                    Link link = network.getLinks().get(id);
                    if (link == null) continue;
                    var result = pointToSegment(query, link);
                    if (result.distance() <= maxDistance) {
                        candidates.add(result);
                    }
                }
            }
        }

        candidates.sort(Comparator.comparingDouble(NearestLink::distance));
        return candidates.size() > maxResults ? List.copyOf(candidates.subList(0, maxResults)) : List.copyOf(candidates);
    }

    private static NearestLink pointToSegment(Coord query, Link link) {
        Coord a = link.getFromNode().getCoord();
        Coord b = link.getToNode().getCoord();
        double dx = b.getX() - a.getX();
        double dy = b.getY() - a.getY();
        double lenSq = dx * dx + dy * dy;
        double t = lenSq == 0 ? 0 : Math.max(0, Math.min(1,
                ((query.getX() - a.getX()) * dx + (query.getY() - a.getY()) * dy) / lenSq));
        double projX = a.getX() + t * dx;
        double projY = a.getY() + t * dy;
        double dist = Math.sqrt(
                (query.getX() - projX) * (query.getX() - projX)
                        + (query.getY() - projY) * (query.getY() - projY));
        double linkLen = Math.sqrt(lenSq);
        return new NearestLink(link.getId().toString(), dist, new Coord(projX, projY), linkLen == 0 ? 0 : t);
    }
}

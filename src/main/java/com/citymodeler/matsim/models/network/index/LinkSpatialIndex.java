package com.citymodeler.matsim.models.network.index;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.citymodeler.matsim.models.api.Coord;
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

        this.gridMinX = (int) (minX / cellSize);
        this.gridMinY = (int) (minY / cellSize);
        this.gridWidth = (int) ((maxX - minX) / cellSize) + 2;
        this.gridHeight = (int) ((maxY - minY) / cellSize) + 2;
        this.grid = new ArrayList<>(gridWidth * gridHeight);
        for (int i = 0; i < grid.size(); i++) {
            grid.add(new ArrayList<>());
        }

        for (var entry : network.getLinks().entrySet()) {
            Link link = entry.getValue();
            String linkId = entry.getKey().toString();
            double x1 = link.getFromNode().getCoord().getX();
            double y1 = link.getFromNode().getCoord().getY();
            double x2 = link.getToNode().getCoord().getX();
            double y2 = link.getToNode().getCoord().getY();
            double minGX = (int) (Math.min(x1, x2) / cellSize) - gridMinX;
            double maxGX = (int) (Math.max(x1, x2) / cellSize) - gridMinX;
            double minGY = (int) (Math.min(y1, y2) / cellSize) - gridMinY;
            double maxGY = (int) (Math.max(y1, y2) / cellSize) - gridMinY;
            for (int gx = Math.max(0, (int) minGX); gx <= Math.min(gridWidth - 1, (int) maxGX); gx++) {
                for (int gy = Math.max(0, (int) minGY); gy <= Math.min(gridHeight - 1, (int) maxGY); gy++) {
                    grid.get(gy * gridWidth + gx).add(linkId);
                }
            }
        }
    }

    public List<NearestLink> nearestLinks(Coord query, int maxResults, double maxDistance) {
        int cx = (int) (query.getX() / cellSize) - gridMinX;
        int cy = (int) (query.getY() / cellSize) - gridMinY;
        int radius = (int) (maxDistance / cellSize) + 1;

        List<NearestLink> candidates = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                int gx = cx + dx;
                int gy = cy + dy;
                if (gx < 0 || gx >= gridWidth || gy < 0 || gy >= gridHeight) continue;
                for (String linkId : grid.get(gy * gridWidth + gx)) {
                    var id = com.citymodeler.matsim.models.api.Id.create(linkId, Link.class);
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
        return candidates.size() > maxResults ? candidates.subList(0, maxResults) : candidates;
    }

    private NearestLink pointToSegment(Link link) {
        Coord p = query;
        Coord a = link.getFromNode().getCoord();
        Coord b = link.getToNode().getCoord();
        return computeClosest(link.getId().toString(), a, b, p);
    }

    private Coord query;

    private NearestLink pointToSegment(Coord query, Link link) {
        Coord a = link.getFromNode().getCoord();
        Coord b = link.getToNode().getCoord();
        return computeClosest(link.getId().toString(), a, b, query);
    }

    private static NearestLink computeClosest(String linkId, Coord a, Coord b, Coord p) {
        double dx = b.getX() - a.getX();
        double dy = b.getY() - a.getY();
        double lenSq = dx * dx + dy * dy;
        double t = lenSq == 0 ? 0 : Math.max(0, Math.min(1,
                ((p.getX() - a.getX()) * dx + (p.getY() - a.getY()) * dy) / lenSq));
        double projX = a.getX() + t * dx;
        double projY = a.getY() + t * dy;
        double dist = Math.sqrt((p.getX() - projX) * (p.getX() - projX) + (p.getY() - projY) * (p.getY() - projY));
        double linkLen = Math.sqrt(lenSq);
        return new NearestLink(linkId, dist, new Coord(projX, projY), linkLen == 0 ? 0 : t);
    }
}

package com.citymodeler.matsim.models.network.index;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.Node;

/**
 * Uniform grid spatial index for nodes. Supports nearest-node queries.
 */
public final class NodeSpatialIndex {

    private final double cellSize;
    private final int gridMinX, gridMinY, gridWidth, gridHeight;
    private final List<List<String>> grid;
    private final Map<Id<Node>, Node> nodes;

    public NodeSpatialIndex(Network network, double cellSizeMeters) {
        if (cellSizeMeters <= 0) {
            throw new IllegalArgumentException("cellSizeMeters must be > 0");
        }
        this.cellSize = cellSizeMeters;
        this.nodes = network.getNodes();

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Node node : nodes.values()) {
            double x = node.getCoord().getX();
            double y = node.getCoord().getY();
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

        for (var entry : nodes.entrySet()) {
            double x = entry.getValue().getCoord().getX();
            double y = entry.getValue().getCoord().getY();
            int gx = (int) (x / cellSize) - gridMinX;
            int gy = (int) (y / cellSize) - gridMinY;
            if (gx >= 0 && gx < gridWidth && gy >= 0 && gy < gridHeight) {
                grid.get(gy * gridWidth + gx).add(entry.getKey().toString());
            }
        }
    }

    public List<NearestNode> nearestNodes(Coord query, int maxResults, double maxDistance) {
        int cx = (int) (query.getX() / cellSize) - gridMinX;
        int cy = (int) (query.getY() / cellSize) - gridMinY;
        int radius = (int) (maxDistance / cellSize) + 1;

        List<NearestNode> candidates = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                int gx = cx + dx;
                int gy = cy + dy;
                if (gx < 0 || gx >= gridWidth || gy < 0 || gy >= gridHeight) continue;
                for (String nodeId : grid.get(gy * gridWidth + gx)) {
                    var id = Id.create(nodeId, Node.class);
                    Node node = nodes.get(id);
                    if (node == null) continue;
                    Coord nc = node.getCoord();
                    double dist = Math.sqrt(
                            (query.getX() - nc.getX()) * (query.getX() - nc.getX())
                                    + (query.getY() - nc.getY()) * (query.getY() - nc.getY()));
                    if (dist <= maxDistance) {
                        candidates.add(new NearestNode(nodeId, dist, nc));
                    }
                }
            }
        }

        candidates.sort(Comparator.comparingDouble(NearestNode::distance));
        return candidates.size() > maxResults ? List.copyOf(candidates.subList(0, maxResults)) : List.copyOf(candidates);
    }
}

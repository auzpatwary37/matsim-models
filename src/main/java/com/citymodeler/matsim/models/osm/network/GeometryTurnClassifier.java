package com.citymodeler.matsim.models.osm.network;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;

/**
 * Classifies a turning movement from link geometry: the incoming link's final segment versus the
 * outgoing link's first segment. Falls back to endpoint node coordinates when geometry is absent.
 * Buckets the signed bearing delta into sharp/left/slight/through/slight/right/sharp.
 */
public final class GeometryTurnClassifier implements MovementTurnClassifier {

    private final Network network;
    private final OsmGeometryStore geometryStore;

    public GeometryTurnClassifier(Network network, OsmGeometryStore geometryStore) {
        this.network = network;
        this.geometryStore = geometryStore;
    }

    @Override
    public LaneTurnClass classify(String inLinkId, String outLinkId) {
        Coord[] in = segment(inLinkId, true);
        Coord[] out = segment(outLinkId, false);
        if (in == null || out == null) {
            return LaneTurnClass.UNKNOWN;
        }
        double ix = in[1].getX() - in[0].getX();
        double iy = in[1].getY() - in[0].getY();
        double ox = out[1].getX() - out[0].getX();
        double oy = out[1].getY() - out[0].getY();
        // Coincident consecutive points give a zero-length segment; atan2(0, 0) is 0, which would
        // misclassify the movement as THROUGH. With no heading to compare, the turn is unknown.
        double inLength = Math.hypot(ix, iy);
        double outLength = Math.hypot(ox, oy);
        if (inLength < 1e-9 || outLength < 1e-9) {
            return LaneTurnClass.UNKNOWN;
        }
        double inHeading = Math.atan2(iy, ix);
        double outHeading = Math.atan2(oy, ox);
        double delta = Math.toDegrees(normalize(outHeading - inHeading));
        double abs = Math.abs(delta);
        if (abs < 20) {
            return LaneTurnClass.THROUGH;
        }
        if (abs > 150) {
            return LaneTurnClass.REVERSE;
        }
        boolean left = delta > 0;
        if (abs < 45) {
            return left ? LaneTurnClass.SLIGHT_LEFT : LaneTurnClass.SLIGHT_RIGHT;
        }
        if (abs > 120) {
            return left ? LaneTurnClass.SHARP_LEFT : LaneTurnClass.SHARP_RIGHT;
        }
        return left ? LaneTurnClass.LEFT : LaneTurnClass.RIGHT;
    }

    private static double normalize(double radians) {
        double r = radians;
        while (r > Math.PI) {
            r -= 2 * Math.PI;
        }
        while (r <= -Math.PI) {
            r += 2 * Math.PI;
        }
        return r;
    }

    private Coord[] segment(String linkId, boolean last) {
        var geometry = geometryStore.geometryForLink(linkId);
        if (geometry.isPresent() && geometry.get().points().size() >= 2) {
            var points = geometry.get().points();
            return last
                    ? new Coord[]{points.get(points.size() - 2), points.get(points.size() - 1)}
                    : new Coord[]{points.get(0), points.get(1)};
        }
        Link link = network.getLinks().get(Id.create(linkId, Link.class));
        if (link == null || link.getFromNode() == null || link.getToNode() == null) {
            return null;
        }
        return new Coord[]{link.getFromNode().getCoord(), link.getToNode().getCoord()};
    }
}

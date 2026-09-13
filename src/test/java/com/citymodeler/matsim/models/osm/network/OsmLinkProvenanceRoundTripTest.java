package com.citymodeler.matsim.models.osm.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.io.NetworkXmlReader;
import com.citymodeler.matsim.models.io.StreamingNetworkWriter;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.osm.OsmImportResult;
import com.citymodeler.matsim.models.osm.OsmProvenance;
import com.citymodeler.matsim.models.osm.OsmTagSet;
import com.citymodeler.matsim.models.osm.model.OsmNodeRecord;
import com.citymodeler.matsim.models.osm.model.OsmWayRecord;

/** Contracted-link provenance (geometry, source ways/nodes/names) must survive a writer->reader round-trip. */
final class OsmLinkProvenanceRoundTripTest {

    private static OsmNodeRecord n(String id, double x) {
        return new OsmNodeRecord(id, x, 0, new Coord(x, 0), OsmTagSet.empty());
    }

    private static OsmImportResult result() {
        Map<String, OsmNodeRecord> ns = new TreeMap<>();
        ns.put("A", n("A", 0));
        ns.put("B", n("B", 100));
        ns.put("C", n("C", 200));
        Map<String, OsmWayRecord> ws = new TreeMap<>();
        ws.put("10", new OsmWayRecord("10", List.of("A", "B"),
                OsmTagSet.of(Map.of("highway", "residential", "name", "First St"))));
        ws.put("20", new OsmWayRecord("20", List.of("B", "C"),
                OsmTagSet.of(Map.of("highway", "residential", "name", "Second Ave"))));
        return new OsmImportResult(ns, ws, new TreeMap<>(), List.of(),
                OsmProvenance.defaultFor("f.osm", "EPSG:3857"));
    }

    @Test
    void provenanceSurvivesStreamingRoundTrip() {
        CollapsedTopology built = OsmTopologyBuilder.build(result(),
                OsmNetworkBuildConfig.defaultConfig(), false);
        Network network = built.network();

        String xml = new StreamingNetworkWriter().writeToString(network);
        Network back = new NetworkXmlReader().read(xml);

        Id<Link> id = Id.create("sim_10_f_A_C", Link.class);
        Link original = network.getLinks().get(id);
        Link roundTripped = back.getLinks().get(id);
        assertNotNull(roundTripped);

        OsmLinkProvenance.Decoded decoded = OsmLinkProvenance.of(roundTripped);
        assertEquals(List.of("10", "20"), decoded.sourceWayIds());
        assertEquals(List.of("A", "B", "C"), decoded.sourceNodeIds());
        assertEquals(List.of("First St", "Second Ave"), decoded.sourceNames());
        assertEquals(List.of(new Coord(0, 0), new Coord(100, 0), new Coord(200, 0)),
                decoded.geometry());
        assertEquals(original.getAttributes().getAttribute("osm:geometry"),
                roundTripped.getAttributes().getAttribute("osm:geometry"));
        assertEquals(original.getLength(), roundTripped.getLength());
    }
}

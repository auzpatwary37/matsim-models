package com.citymodeler.matsim.models.osm.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.Node;
import com.citymodeler.matsim.models.osm.OsmImportConfig;
import com.citymodeler.matsim.models.osm.OsmImportIssue;
import com.citymodeler.matsim.models.osm.OsmImportResult;
import com.citymodeler.matsim.models.osm.OsmIssueSeverity;
import com.citymodeler.matsim.models.osm.OsmNetworkImporter;

/**
 * End-to-end integration over real bounded OpenStreetMap extracts (one per region: Luxembourg,
 * Toronto/Ontario, Seattle/Washington, Melbourne/Victoria). Each fixture is a small, pinned
 * {@code .osm.gz} cut (osmosis, {@code completeWays=true}, ODbL-1.0) read by
 * {@link OsmNetworkImporter}, built into a MATSim network, and simplified into a signal-ready
 * network.
 *
 * <p>The assertions are structural invariants that must hold for ANY real OSM cut, not
 * city-specific exact numbers. Across the whole fixture set we additionally require the special
 * features the pipeline must cope with: at least one roundabout, one {@code turn:lanes}, and one
 * turn-restriction relation.
 */
final class OsmCityFixtureIntegrationTest {

    private static final Path DIR = Path.of("src/test/resources/osm/cities");

    static Stream<Arguments> cities() {
        return Stream.of(
                Arguments.of("luxembourg"),
                Arguments.of("toronto"),
                Arguments.of("seattle"),
                Arguments.of("melbourne"));
    }

    private static OsmImportResult importCity(String city) throws IOException {
        Path file = DIR.resolve(city + ".osm.gz");
        return new OsmNetworkImporter().read(OsmImportConfig.of(file, "EPSG:3857"));
    }

    private static OsmSimplifiedNetwork simplify(OsmImportResult r) {
        return simplify(r, OsmSimplifyOptions.defaults());
    }

    private static OsmSimplifiedNetwork simplify(OsmImportResult r, OsmSimplifyOptions options) {
        OsmNetworkBuildConfig cfg = OsmNetworkBuildConfig.materializeGeometryConfig();
        OsmNetworkBuildResult mat = new OsmMatsimNetworkBuilder().build(r, cfg);
        return OsmSignalAwareSimplifier.simplify(mat, r, cfg, options);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cities")
    void importsAsSelfContainedRealNetwork(String city) throws IOException {
        OsmImportResult raw = importCity(city);

        assertTrue(raw.nodes().size() > 5000, city + ": expected a substantial real network");
        assertTrue(raw.ways().size() > 1000, city + ": expected many ways");
        assertTrue(raw.relations().size() > 50, city + ": expected relations (restrictions/routes)");

        // Self-containment: every way node reference resolves (completeWays=true).
        Set<String> missing = new HashSet<>();
        for (var way : raw.ways().values()) {
            for (String ref : way.nodeRefs()) {
                if (!raw.nodes().containsKey(ref)) {
                    missing.add(ref);
                }
            }
        }
        assertTrue(missing.isEmpty(), city + ": dangling node refs " + missing.size());

        // No fatal import issues.
        assertTrue(raw.issues().stream().noneMatch(i -> i.severity() == OsmIssueSeverity.ERROR),
                city + ": unexpected import errors");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cities")
    void buildsAndSimplifiesWithoutFatalFindings(String city) throws IOException {
        OsmSimplifiedNetwork s = simplify(importCity(city));

        assertNotNull(s.network());
        assertFalse(s.network().getLinks().isEmpty(), city + ": network has no links");
        assertFalse(s.signalizedJunctions().isEmpty(), city + ": no signalized junctions detected");

        for (OsmImportIssue issue : s.issues()) {
            assertFalse(issue.severity() == OsmIssueSeverity.ERROR,
                    city + ": fatal finding " + issue.code() + " -> " + issue.message());
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cities")
    void movementGraphIsWellFormed(String city) throws IOException {
        OsmSimplifiedNetwork s = simplify(importCity(city));
        Network net = s.network();

        int total = s.signalizedJunctions().size();
        int withBothArms = 0;
        int checkedMovements = 0;

        for (JunctionSignalDescriptor j : s.signalizedJunctions()) {
            // Real OSM signals on one-way entries (or on non-drivable ways) legitimately have no
            // incoming or outgoing arm; we tolerate those but require the links that ARE listed
            // to resolve, and every movement to be structurally consistent.
            if (!j.incomingLinks().isEmpty() && !j.outgoingLinks().isEmpty()) {
                withBothArms++;
            }
            for (String linkId : j.incomingLinks()) {
                assertTrue(net.getLinks().containsKey(com.citymodeler.matsim.models.api.Id.create(linkId, Link.class)),
                        city + ": junction incoming link not in network: " + linkId);
            }
            for (String linkId : j.outgoingLinks()) {
                assertTrue(net.getLinks().containsKey(com.citymodeler.matsim.models.api.Id.create(linkId, Link.class)),
                        city + ": junction outgoing link not in network: " + linkId);
            }

            for (SignalizedMovement m : j.movements()) {
                Link in = net.getLinks().get(com.citymodeler.matsim.models.api.Id.create(m.incomingLinkId(), Link.class));
                Link out = net.getLinks().get(com.citymodeler.matsim.models.api.Id.create(m.outgoingLinkId(), Link.class));
                assertNotNull(in, city + ": movement incoming link missing " + m.incomingLinkId());
                assertNotNull(out, city + ": movement outgoing link missing " + m.outgoingLinkId());
                // Topological reachability: the two links meet at the same junction node.
                assertEquals(in.getToNode().getId(), out.getFromNode().getId(),
                        city + ": movement is not topologically connected: " + m.movementId());
                // Movement links must be advertised by the junction itself.
                assertTrue(j.incomingLinks().contains(m.incomingLinkId()),
                        city + ": movement uses unlisted incoming link " + m.incomingLinkId());
                assertTrue(j.outgoingLinks().contains(m.outgoingLinkId()),
                        city + ": movement uses unlisted outgoing link " + m.outgoingLinkId());
                checkedMovements++;
            }
        }

        assertTrue(checkedMovements > 0, city + ": no movements enumerated at all");
        // The overwhelming majority of real signalized junctions must have both arms.
        assertTrue(withBothArms >= total * 0.9,
                city + ": only " + withBothArms + "/" + total + " junctions have both arms");
    }

    /**
     * Review #3: the default options do not cluster, so the multi-node junction logic is not
     * otherwise exercised by real data. Re-run each city with clustering enabled and assert the
     * resulting junctions (including any multi-node clusters and their cross-node movements) remain
     * structurally coherent and deterministic.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("cities")
    void clusteringEnabledOnRealDataStaysCoherent(String city) throws IOException {
        OsmImportResult raw = importCity(city);
        OsmSimplifyOptions clustering = new OsmSimplifyOptions(40.0, false, 25.0, Set.of());
        OsmSimplifiedNetwork s = simplify(raw, clustering);
        Network net = s.network();

        int multiNode = 0;
        for (JunctionSignalDescriptor j : s.signalizedJunctions()) {
            if (j.osmNodeIds().size() > 1) {
                multiNode++;
            }
            // Every advertised boundary approach/departure resolves and is not an internal connector.
            for (String linkId : j.incomingLinks()) {
                assertTrue(net.getLinks().containsKey(com.citymodeler.matsim.models.api.Id.create(linkId, Link.class)),
                        city + ": clustered junction incoming link missing " + linkId);
                assertFalse(j.internalLinks().contains(linkId), city + ": internal link advertised as approach");
            }
            for (String linkId : j.outgoingLinks()) {
                assertTrue(net.getLinks().containsKey(com.citymodeler.matsim.models.api.Id.create(linkId, Link.class)),
                        city + ": clustered junction outgoing link missing " + linkId);
                assertFalse(j.internalLinks().contains(linkId), city + ": internal link advertised as departure");
            }
            // Movements resolve; cross-node movements (arrival node != departure node) are allowed.
            for (SignalizedMovement m : j.movements()) {
                Link in = net.getLinks().get(com.citymodeler.matsim.models.api.Id.create(m.incomingLinkId(), Link.class));
                Link out = net.getLinks().get(com.citymodeler.matsim.models.api.Id.create(m.outgoingLinkId(), Link.class));
                assertNotNull(in, city + ": movement incoming missing " + m.incomingLinkId());
                assertNotNull(out, city + ": movement outgoing missing " + m.outgoingLinkId());
                if (in.getToNode().getId().equals(out.getFromNode().getId())) {
                    // same-node movement: links meet directly
                    continue;
                }
                // cross-node movement: the arrival and departure are distinct cluster members joined
                // internally, so both must be real junctions members of this descriptor.
                assertTrue(j.osmNodeIds().contains(in.getToNode().getId().toString().replace("osm_node_", "")),
                        city + ": cross-node movement arrival not a cluster member");
                assertTrue(j.osmNodeIds().contains(out.getFromNode().getId().toString().replace("osm_node_", "")),
                        city + ": cross-node movement departure not a cluster member");
            }
        }

        // Determinism under clustering too.
        OsmSimplifiedNetwork s2 = simplify(raw, clustering);
        assertEquals(s.network().getLinks().keySet(), s2.network().getLinks().keySet(),
                city + ": non-deterministic clustered links");
        assertEquals(s.signalizedJunctions().size(), s2.signalizedJunctions().size(),
                city + ": non-deterministic clustered junctions (" + multiNode + " multi-node)");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cities")
    void doesNotFabricateSignalizedJunctions(String city) throws IOException {
        OsmImportResult raw = importCity(city);
        OsmSimplifiedNetwork s = simplify(raw);

        long rawSignalNodes = raw.nodes().values().stream()
                .filter(n -> n.tags().has("traffic_signals") || n.tags().has("highway", "traffic_signals"))
                .count();
        assertTrue(rawSignalNodes > 0, city + ": fixture has no traffic-signal nodes");

        // A junction may cluster several signal nodes, but cannot exceed the raw signal-node count.
        assertTrue(s.signalizedJunctions().size() <= rawSignalNodes,
                city + ": fabricated signalized junctions (" + s.signalizedJunctions().size()
                        + ") vs raw signal nodes (" + rawSignalNodes + ")");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cities")
    void isDeterministicIncludingReport(String city) throws IOException {
        OsmImportResult raw = importCity(city);
        OsmSimplifiedNetwork a = simplify(raw);
        OsmSimplifiedNetwork b = simplify(raw);

        assertEquals(a.network().getLinks().keySet(), b.network().getLinks().keySet(), city + ": non-deterministic links");
        assertEquals(a.signalizedJunctions().size(), b.signalizedJunctions().size(), city + ": non-deterministic junctions");
        assertEquals(a.report().summary(), b.report().summary(), city + ": non-deterministic report");
        assertEquals(a.issues(), b.issues(), city + ": non-deterministic issues");
    }

    @Test
    void acrossAllFixturesSpecialFeaturesArePresent() throws IOException {
        boolean roundabout = false;
        boolean turnLanes = false;
        boolean restriction = false;

        for (var args : new String[]{"luxembourg", "toronto", "seattle", "melbourne"}) {
            OsmImportResult raw = importCity(args);
            for (var way : raw.ways().values()) {
                if (way.tags().has("junction", "roundabout") || way.tags().has("junction", "circular")) {
                    roundabout = true;
                }
                if (way.tags().has("turn:lanes")) {
                    turnLanes = true;
                }
            }
            for (var rel : raw.relations().values()) {
                if (rel.tags().has("type", "restriction")) {
                    restriction = true;
                }
            }
        }

        assertTrue(roundabout, "no roundabout across the fixture set");
        assertTrue(turnLanes, "no turn:lanes across the fixture set");
        assertTrue(restriction, "no turn-restriction relation across the fixture set");
    }
}

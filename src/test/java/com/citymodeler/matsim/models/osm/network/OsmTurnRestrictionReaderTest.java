package com.citymodeler.matsim.models.osm.network;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.osm.OsmImportResult;
import com.citymodeler.matsim.models.osm.OsmElementType;
import com.citymodeler.matsim.models.osm.OsmProvenance;
import com.citymodeler.matsim.models.osm.OsmTagSet;
import com.citymodeler.matsim.models.osm.model.OsmNodeRecord;
import com.citymodeler.matsim.models.osm.model.OsmRelationMemberRecord;
import com.citymodeler.matsim.models.osm.model.OsmRelationRecord;
import com.citymodeler.matsim.models.osm.model.OsmWayRecord;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.turnrestrictions.DisallowedNextLinks;
import com.citymodeler.matsim.models.osm.network.OsmTurnRestrictionReader.Record;

class OsmTurnRestrictionReaderTest {

    private Network threeArmJunction() {
        Network net = new Network();
        net.createNode("osm_node_1", 0, 0);
        net.createNode("osm_node_2", 100, 0);
        net.createNode("osm_node_3", 200, 0);
        net.createNode("osm_node_4", 100, 100);
        net.createLink("osm_way_10_0_f", "osm_node_1", "osm_node_2", 100, 1000, 13.9, 1, Set.of("car", "bus"));
        net.createLink("osm_way_20_0_f", "osm_node_2", "osm_node_3", 100, 1000, 13.9, 1, Set.of("car", "bus"));
        net.createLink("osm_way_30_0_f", "osm_node_2", "osm_node_4", 100, 1000, 13.9, 1, Set.of("car", "bus"));
        net.postProcess();
        return net;
    }

    private OsmImportResult restrictionFixture(OsmRelationRecord rel) {
        return new OsmImportResult(
                Map.of(),
                Map.of(
                        "10", new OsmWayRecord("10", List.of("1", "2"), OsmTagSet.of(Map.of("highway", "residential"))),
                        "20", new OsmWayRecord("20", List.of("2", "3"), OsmTagSet.of(Map.of("highway", "residential"))),
                        "30", new OsmWayRecord("30", List.of("2", "4"), OsmTagSet.of(Map.of("highway", "residential")))),
                Map.of("r1", rel),
                List.of(),
                OsmProvenance.defaultFor("test.osm", "EPSG:3857"));
    }

    private OsmNetworkBuildResult buildResult(Network net) {
        return new OsmNetworkBuildResult(
                net, List.of(),
                Map.of("osm_way_10_0_f", new OsmLinkRef("osm_way_10_0_f", "10", 0, true),
                        "osm_way_20_0_f", new OsmLinkRef("osm_way_20_0_f", "20", 0, true),
                        "osm_way_30_0_f", new OsmLinkRef("osm_way_30_0_f", "30", 0, true)),
                Map.of("10", List.of("osm_way_10_0_f"),
                        "20", List.of("osm_way_20_0_f"),
                        "30", List.of("osm_way_30_0_f")),
                List.of(), Map.of(), Map.of());
    }

    @Test
    void noLeftTurnCreatesDisallowedSequence() {
        OsmRelationRecord rel = new OsmRelationRecord("r1",
                List.of(
                        new OsmRelationMemberRecord(OsmElementType.WAY, "10", "from"),
                        new OsmRelationMemberRecord(OsmElementType.NODE, "2", "via"),
                        new OsmRelationMemberRecord(OsmElementType.WAY, "30", "to")),
                OsmTagSet.of(Map.of("type", "restriction", "restriction", "no_left_turn")));

        Network net = threeArmJunction();
        Record result = OsmTurnRestrictionReader.read(restrictionFixture(rel), buildResult(net));

        DisallowedNextLinks dnl = result.perLink().get("osm_way_10_0_f");
        assertNotNull(dnl);
        assertTrue(dnl.isDisallowed("car", List.of("osm_way_30_0_f")));
        assertFalse(dnl.isDisallowed("car", List.of("osm_way_20_0_f")));
    }

    @Test
    void onlyRightTurnDisallowsOthers() {
        OsmRelationRecord rel = new OsmRelationRecord("r1",
                List.of(
                        new OsmRelationMemberRecord(OsmElementType.WAY, "10", "from"),
                        new OsmRelationMemberRecord(OsmElementType.NODE, "2", "via"),
                        new OsmRelationMemberRecord(OsmElementType.WAY, "20", "to")),
                OsmTagSet.of(Map.of("type", "restriction", "restriction", "only_right_turn")));

        Network net = threeArmJunction();
        Record result = OsmTurnRestrictionReader.read(restrictionFixture(rel), buildResult(net));

        DisallowedNextLinks dnl = result.perLink().get("osm_way_10_0_f");
        assertNotNull(dnl);
        assertTrue(dnl.isDisallowed("car", List.of("osm_way_30_0_f")));
        assertFalse(dnl.isDisallowed("car", List.of("osm_way_20_0_f")));
    }

    @Test
    void viaWayRestrictionIsRejected() {
        OsmRelationRecord rel = new OsmRelationRecord("r1",
                List.of(
                        new OsmRelationMemberRecord(OsmElementType.WAY, "10", "from"),
                        new OsmRelationMemberRecord(OsmElementType.WAY, "20", "via"),
                        new OsmRelationMemberRecord(OsmElementType.WAY, "30", "to")),
                OsmTagSet.of(Map.of("type", "restriction", "restriction", "no_left_turn")));

        Network net = new Network();
        net.postProcess();
        OsmNetworkBuildResult br = new OsmNetworkBuildResult(
                net, List.of(), Map.of(), Map.of(), List.of(), Map.of(), Map.of());

        Record result = OsmTurnRestrictionReader.read(restrictionFixture(rel), br);

        assertTrue(result.issues().stream().anyMatch(i ->
                "restriction-via-way-unsupported".equals(i.code())));
        assertTrue(result.perLink().isEmpty());
    }

    @Test
    void exceptBusRemovesBusMode() {
        OsmRelationRecord rel = new OsmRelationRecord("r1",
                List.of(
                        new OsmRelationMemberRecord(OsmElementType.WAY, "10", "from"),
                        new OsmRelationMemberRecord(OsmElementType.NODE, "2", "via"),
                        new OsmRelationMemberRecord(OsmElementType.WAY, "30", "to")),
                OsmTagSet.of(Map.of("type", "restriction", "restriction", "no_left_turn", "except", "bus")));

        Network net = threeArmJunction();
        Record result = OsmTurnRestrictionReader.read(restrictionFixture(rel), buildResult(net));

        DisallowedNextLinks dnl = result.perLink().get("osm_way_10_0_f");
        assertNotNull(dnl);
        // car is restricted, bus is exempt
        assertTrue(dnl.isDisallowed("car", List.of("osm_way_30_0_f")));
        assertFalse(dnl.isDisallowed("bus", List.of("osm_way_30_0_f")));
    }

    /** except=psv is an OSM transport class; it must exempt the internal bus/pt modes. */
    @Test
    void exceptPsvExemptsBusMode() {
        OsmRelationRecord rel = new OsmRelationRecord("r1",
                List.of(
                        new OsmRelationMemberRecord(OsmElementType.WAY, "10", "from"),
                        new OsmRelationMemberRecord(OsmElementType.NODE, "2", "via"),
                        new OsmRelationMemberRecord(OsmElementType.WAY, "30", "to")),
                OsmTagSet.of(Map.of("type", "restriction", "restriction", "no_left_turn", "except", "psv")));

        DisallowedNextLinks dnl = OsmTurnRestrictionReader.read(restrictionFixture(rel),
                buildResult(threeArmJunction())).perLink().get("osm_way_10_0_f");
        assertNotNull(dnl);
        assertTrue(dnl.isDisallowed("car", List.of("osm_way_30_0_f")));
        assertFalse(dnl.isDisallowed("bus", List.of("osm_way_30_0_f")),
                "except=psv must exempt the internal bus mode");
    }

    /** except=motorcar must exempt the internal car mode. */
    @Test
    void exceptMotorcarExemptsCarMode() {
        OsmRelationRecord rel = new OsmRelationRecord("r1",
                List.of(
                        new OsmRelationMemberRecord(OsmElementType.WAY, "10", "from"),
                        new OsmRelationMemberRecord(OsmElementType.NODE, "2", "via"),
                        new OsmRelationMemberRecord(OsmElementType.WAY, "30", "to")),
                OsmTagSet.of(Map.of("type", "restriction", "restriction", "no_left_turn", "except", "motorcar")));

        DisallowedNextLinks dnl = OsmTurnRestrictionReader.read(restrictionFixture(rel),
                buildResult(threeArmJunction())).perLink().get("osm_way_10_0_f");
        assertNotNull(dnl);
        assertFalse(dnl.isDisallowed("car", List.of("osm_way_30_0_f")),
                "except=motorcar must exempt the internal car mode");
        assertTrue(dnl.isDisallowed("bus", List.of("osm_way_30_0_f")));
    }

    /** Semicolon-separated exception list exempts every mapped mode. */
    @Test
    void exceptSemicolonListExemptsAllMappedModes() {
        OsmRelationRecord rel = new OsmRelationRecord("r1",
                List.of(
                        new OsmRelationMemberRecord(OsmElementType.WAY, "10", "from"),
                        new OsmRelationMemberRecord(OsmElementType.NODE, "2", "via"),
                        new OsmRelationMemberRecord(OsmElementType.WAY, "30", "to")),
                OsmTagSet.of(Map.of("type", "restriction", "restriction", "no_left_turn",
                        "except", "psv;motorcar")));

        Record result = OsmTurnRestrictionReader.read(restrictionFixture(rel),
                buildResult(threeArmJunction()));
        // Every mode exempt: no enforceable restriction remains for this relation.
        assertNull(result.perLink().get("osm_way_10_0_f"));
    }

    /** A via-way restriction is counted as unimplemented (topology preserved, not enforced). */
    @Test
    void viaWayRestrictionIsCounted() {
        OsmRelationRecord rel = new OsmRelationRecord("r1",
                List.of(
                        new OsmRelationMemberRecord(OsmElementType.WAY, "10", "from"),
                        new OsmRelationMemberRecord(OsmElementType.WAY, "20", "via"),
                        new OsmRelationMemberRecord(OsmElementType.WAY, "30", "to")),
                OsmTagSet.of(Map.of("type", "restriction", "restriction", "no_left_turn")));

        Network net = new Network();
        net.postProcess();
        OsmNetworkBuildResult br = new OsmNetworkBuildResult(
                net, List.of(), Map.of(), Map.of(), List.of(), Map.of(), Map.of());

        Record result = OsmTurnRestrictionReader.read(restrictionFixture(rel), br);
        assertEquals(1, result.viaWayRestrictions());
    }

    @Test
    void nonImportedFromWayProducesNoRestriction() {
        // Relation references way "99" which is not in the import result
        OsmRelationRecord rel = new OsmRelationRecord("r1",
                List.of(
                        new OsmRelationMemberRecord(OsmElementType.WAY, "99", "from"),
                        new OsmRelationMemberRecord(OsmElementType.NODE, "2", "via"),
                        new OsmRelationMemberRecord(OsmElementType.WAY, "30", "to")),
                OsmTagSet.of(Map.of("type", "restriction", "restriction", "no_left_turn")));

        Network net = threeArmJunction();
        Record result = OsmTurnRestrictionReader.read(restrictionFixture(rel), buildResult(net));

        // No restriction should be attached to any link
        assertTrue(result.perLink().isEmpty());
        assertTrue(result.issues().stream().anyMatch(i ->
                "restriction-from-unresolved".equals(i.code())));
    }
}

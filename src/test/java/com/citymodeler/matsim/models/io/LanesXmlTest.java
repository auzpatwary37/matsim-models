package com.citymodeler.matsim.models.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.lanes.Lane;
import com.citymodeler.matsim.models.lanes.Lanes;
import com.citymodeler.matsim.models.lanes.LanesToLinkAssignment;
import com.citymodeler.matsim.models.network.Link;

class LanesXmlTest {
    @Test
    void readsWritesAndReadsLanesXml() {
        String xml = """
                <laneDefinitions xmlns="http://www.matsim.org/files/dtd">
                    <lanesToLinkAssignment linkIdRef="l1">
                        <lane id="lane-1">
                            <leadsTo>
                                <toLink refId="l2"/>
                                <toLink refId="l3"/>
                            </leadsTo>
                            <capacity vehiclesPerHour="700.0"/>
                            <startsAt meterFromLinkEnd="45.0"/>
                            <alignment>1</alignment>
                            <attributes>
                                <attribute name="lane-kind" class="java.lang.String">bus</attribute>
                            </attributes>
                        </lane>
                    </lanesToLinkAssignment>
                </laneDefinitions>
                """;

        Lanes lanes = new LanesXmlReader().read(xml);
        String roundTrippedXml = new LanesXmlWriter().writeToString(lanes);
        Lanes roundTripped = new LanesXmlReader().read(roundTrippedXml);

        assertEquals(1, roundTripped.getLanesToLinkAssignments().size());
        LanesToLinkAssignment assignment = roundTripped.getLanesToLinkAssignments().get(Id.create("l1", Link.class));
        assertEquals(1, assignment.getLanes().size());

        Lane lane = assignment.getLanes().get(Id.create("lane-1", Lane.class));
        assertEquals(2, lane.getToLinkIds().size());
        assertEquals("l2", lane.getToLinkIds().get(0).toString());
        assertEquals(700.0, lane.getCapacityVehiclesPerHour());
        assertEquals(45.0, lane.getStartsAtMeterFromLinkEnd());
        assertEquals("1", lane.getAlignment());
        assertEquals("bus", lane.getAttributes().getAttribute("lane-kind"));
    }

    @Test
    void toLaneOnlyLaneRoundTrips() {
        String xml = """
                <laneDefinitions xmlns="http://www.matsim.org/files/dtd">
                    <lanesToLinkAssignment linkIdRef="l1">
                        <lane id="lane-1">
                            <leadsTo>
                                <toLane refId="lane-2"/>
                                <toLane refId="lane-3"/>
                            </leadsTo>
                            <alignment>0</alignment>
                        </lane>
                    </lanesToLinkAssignment>
                </laneDefinitions>
                """;

        Lanes lanes = new LanesXmlReader().read(xml);
        Lanes roundTripped = new LanesXmlReader().read(new LanesXmlWriter().writeToString(lanes));

        Lane lane = roundTripped.getLanesToLinkAssignments().get(Id.create("l1", Link.class))
                .getLanes().get(Id.create("lane-1", Lane.class));
        assertEquals(0, lane.getToLinkIds().size());
        assertEquals(2, lane.getToLaneIds().size());
        assertEquals("lane-2", lane.getToLaneIds().get(0).toString());
    }

    @Test
    void bothPresentEmitsToLinkAndRehydratesDroppedToLaneIds() {
        Lanes lanes = new Lanes();
        LanesToLinkAssignment assignment = new LanesToLinkAssignment(Id.create("l1", Link.class));
        Lane lane = new Lane(Id.create("l1_l0", Lane.class));
        lane.addToLinkId(Id.create("l2", Link.class));
        lane.addToLaneId(Id.create("l1_l1", Lane.class));
        lane.addToLaneId(Id.create("l1_l2", Lane.class));
        lane.setAlignment("0");
        assignment.addLane(lane);
        lanes.addAssignment(assignment);

        String xml = new LanesXmlWriter().writeToString(lanes);
        Lanes reparsed = new LanesXmlReader().read(xml);

        Lane reparsedLane = reparsed.getLanesToLinkAssignments().get(Id.create("l1", Link.class))
                .getLanes().get(Id.create("l1_l0", Lane.class));
        assertEquals(1, reparsedLane.getToLinkIds().size());
        assertEquals("l2", reparsedLane.getToLinkIds().get(0).toString());
        // Spec Part 2: the dropped toLane ids are rehydrated from osm:lane.toLaneIds, not degraded
        // to metadata, so a mixed lane round-trips.
        assertEquals(2, reparsedLane.getToLaneIds().size());
        assertEquals("l1_l1", reparsedLane.getToLaneIds().get(0).toString());
        assertEquals("l1_l2", reparsedLane.getToLaneIds().get(1).toString());
        assertEquals("l1_l1,l1_l2", reparsedLane.getAttributes().getAttribute("osm:lane.toLaneIds"));
    }

    @Test
    void rehydratedToLaneIdsAreDeduplicated() {
        String xml = """
                <laneDefinitions xmlns="http://www.matsim.org/files/dtd">
                    <lanesToLinkAssignment linkIdRef="l1">
                        <lane id="l1_l0">
                            <leadsTo>
                                <toLane refId="l1_l1"/>
                            </leadsTo>
                            <alignment>0</alignment>
                            <attributes>
                                <attribute name="osm:lane.toLaneIds" class="java.lang.String">l1_l1,l1_l2</attribute>
                            </attributes>
                        </lane>
                    </lanesToLinkAssignment>
                </laneDefinitions>
                """;

        Lane lane = new LanesXmlReader().read(xml)
                .getLanesToLinkAssignments().get(Id.create("l1", Link.class))
                .getLanes().get(Id.create("l1_l0", Lane.class));
        assertEquals(2, lane.getToLaneIds().size(), "already-present toLane ids are not duplicated");
        assertEquals("l1_l1", lane.getToLaneIds().get(0).toString());
        assertEquals("l1_l2", lane.getToLaneIds().get(1).toString());
    }

    @Test
    void emptyLeadsToThrows() {
        Lanes lanes = new Lanes();
        LanesToLinkAssignment assignment = new LanesToLinkAssignment(Id.create("l1", Link.class));
        Lane lane = new Lane(Id.create("l1_l0", Lane.class));
        lane.setAlignment("0");
        assignment.addLane(lane);
        lanes.addAssignment(assignment);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new LanesXmlWriter().writeToString(lanes));
        assertTrue(exception.getMessage().contains("l1_l0"));
    }

    @Test
    void writtenRootHasNoAttributesChild() {
        Lanes lanes = new Lanes();
        LanesToLinkAssignment assignment = new LanesToLinkAssignment(Id.create("l1", Link.class));
        Lane lane = new Lane(Id.create("l1_l0", Lane.class));
        lane.addToLinkId(Id.create("l2", Link.class));
        lane.setAlignment("0");
        assignment.addLane(lane);
        lanes.addAssignment(assignment);
        lanes.getAttributes().putAttribute("legacy-root", "ignored");

        String xml = new LanesXmlWriter().writeToString(lanes);
        Document document = XmlSupport.parse(xml);
        Element root = document.getDocumentElement();
        assertEquals("laneDefinitions", root.getTagName());
        assertNull(XmlSupport.child(root, "attributes"));
    }

    @Test
    void zeroOptionalFieldsRoundTripStable() {
        Lanes lanes = new Lanes();
        LanesToLinkAssignment assignment = new LanesToLinkAssignment(Id.create("l1", Link.class));
        Lane lane = new Lane(Id.create("l1_l0", Lane.class));
        lane.addToLinkId(Id.create("l2", Link.class));
        lane.setCapacityVehiclesPerHour(0.0);
        lane.setStartsAtMeterFromLinkEnd(0.0);
        lane.setAlignment(null);
        assignment.addLane(lane);
        lanes.addAssignment(assignment);

        Lanes roundTripped = new LanesXmlReader().read(new LanesXmlWriter().writeToString(lanes));
        Lane reparsed = roundTripped.getLanesToLinkAssignments().get(Id.create("l1", Link.class))
                .getLanes().get(Id.create("l1_l0", Lane.class));
        assertEquals(0.0, reparsed.getCapacityVehiclesPerHour());
        assertEquals(0.0, reparsed.getStartsAtMeterFromLinkEnd());
    }

    @Test
    void writerDoesNotMutateCallerAttributesOnAlignmentNormalization() {
        Lanes lanes = new Lanes();
        LanesToLinkAssignment assignment = new LanesToLinkAssignment(Id.create("l1", Link.class));
        Lane lane = new Lane(Id.create("l1_l0", Lane.class));
        lane.addToLinkId(Id.create("l2", Link.class));
        lane.setAlignment("left");
        assignment.addLane(lane);
        lanes.addAssignment(assignment);

        new LanesXmlWriter().writeToString(lanes);

        assertNull(lane.getAttributes().getAttribute("osm:lane.alignmentProvenance"));
        assertNull(lane.getAttributes().getAttribute("osm:lane.alignmentRaw"));
        assertEquals("left", lane.getAlignment());
    }

    @Test
    void loadFromClasspathFixture() {
        String fixturePath = "fixtures/lanes.xml";
        InputStream is = getClass().getClassLoader().getResourceAsStream(fixturePath);
        Lanes lanes = new LanesXmlReader().read(is);

        assertNotNull(lanes);
        assertEquals(2, lanes.getLanesToLinkAssignments().size());

        LanesToLinkAssignment l1Assignment = lanes.getLanesToLinkAssignments().get(Id.create("l1", Link.class));
        assertEquals(2, l1Assignment.getLanes().size());

        Lane lane0 = l1Assignment.getLanes().get(Id.create("l1_l0", Lane.class));
        assertEquals("l2", lane0.getToLinkIds().get(0).toString());
        assertEquals(1800.0, lane0.getCapacityVehiclesPerHour());
        assertEquals("present", lane0.getAttributes().getAttribute("osm:lane.confidence"));

        Lane lane1 = l1Assignment.getLanes().get(Id.create("l1_l1", Lane.class));
        assertEquals("l2_l0", lane1.getToLaneIds().get(0).toString());
        assertEquals(45.0, lane1.getStartsAtMeterFromLinkEnd());
        assertEquals("1", lane1.getAlignment());

        LanesToLinkAssignment l2Assignment = lanes.getLanesToLinkAssignments().get(Id.create("l2", Link.class));
        Lane lane2 = l2Assignment.getLanes().get(Id.create("l2_l0", Lane.class));
        assertEquals("l3", lane2.getToLinkIds().get(0).toString());
        assertEquals(900.0, lane2.getCapacityVehiclesPerHour());
    }

    @Test
    void laneCollectionsAreUnmodifiable() {
        Lane lane = new Lane(Id.create("lane-1", Lane.class));
        assertThrows(UnsupportedOperationException.class, () -> lane.getToLinkIds().clear());
        assertThrows(UnsupportedOperationException.class, () -> lane.getToLaneIds().clear());
    }
}

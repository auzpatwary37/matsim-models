package com.citymodeler.matsim.models.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.InputStream;

import org.junit.jupiter.api.Test;

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
                                <toLane refId="lane-2"/>
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
        assertEquals("lane-2", lane.getToLaneIds().get(0).toString());
        assertEquals(700.0, lane.getCapacityVehiclesPerHour());
        assertEquals(45.0, lane.getStartsAtMeterFromLinkEnd());
        assertEquals("1", lane.getAlignment());
        assertEquals("bus", lane.getAttributes().getAttribute("lane-kind"));
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

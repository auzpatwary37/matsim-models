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
                <lanes>
                    <assignment linkId=\"l1\">
                        <lane id=\"lane-1\" toLinkIds=\"l2,l3\" toLaneIds=\"lane-2\" capacityVehiclesPerHour=\"700.0\" startsAtMeterFromLinkEnd=\"45.0\" alignment=\"center\">
                            <attributes>
                                <attribute name=\"lane-kind\" class=\"java.lang.String\">bus</attribute>
                            </attributes>
                        </lane>
                    </assignment>
                </lanes>
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
        assertEquals("center", lane.getAlignment());
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
        Lane lane1 = l1Assignment.getLanes().get(Id.create("lane-1", Lane.class));
        assertEquals("bus", lane1.getAttributes().getAttribute("lane-kind"));
    }

    @Test
    void laneCollectionsAreUnmodifiable() {
        Lane lane = new Lane(Id.create("lane-1", Lane.class));
        assertThrows(UnsupportedOperationException.class, () -> lane.getToLinkIds().clear());
        assertThrows(UnsupportedOperationException.class, () -> lane.getToLaneIds().clear());
    }
}

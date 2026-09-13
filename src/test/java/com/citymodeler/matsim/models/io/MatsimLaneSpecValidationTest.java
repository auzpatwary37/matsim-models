package com.citymodeler.matsim.models.io;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.StringReader;

import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.lanes.Lane;
import com.citymodeler.matsim.models.lanes.Lanes;
import com.citymodeler.matsim.models.lanes.LanesToLinkAssignment;
import com.citymodeler.matsim.models.network.Link;

/** Interoperability oracle: our lane output must validate against the published MATSim v2.0 schema. */
class MatsimLaneSpecValidationTest {

    @Test
    void canonicalWriterOutputValidatesAgainstPublishedSchema() throws Exception {
        Lanes lanes = new Lanes();
        LanesToLinkAssignment assignment = new LanesToLinkAssignment(Id.create("l1", Link.class));
        Lane lane = new Lane(Id.create("l1_l0", Lane.class));
        lane.addToLinkId(Id.create("l2", Link.class));
        lane.setCapacityVehiclesPerHour(900.0);
        lane.setAlignment("0");
        lane.getAttributes().putAttribute("osm:lane.confidence", "present");
        assignment.addLane(lane);
        lanes.addAssignment(assignment);

        validate(lanes);
    }

    @Test
    void toLaneOnlyWriterOutputValidatesAgainstPublishedSchema() throws Exception {
        Lanes lanes = new Lanes();
        LanesToLinkAssignment assignment = new LanesToLinkAssignment(Id.create("l1", Link.class));
        Lane lane = new Lane(Id.create("l1_l0", Lane.class));
        lane.addToLaneId(Id.create("l1_l1", Lane.class));
        lane.setAlignment("1");
        assignment.addLane(lane);
        lanes.addAssignment(assignment);

        validate(lanes);
    }

    @Test
    void omittedOptionalFieldsWriterOutputValidatesAgainstPublishedSchema() throws Exception {
        Lanes lanes = new Lanes();
        LanesToLinkAssignment assignment = new LanesToLinkAssignment(Id.create("l1", Link.class));
        Lane lane = new Lane(Id.create("l1_l0", Lane.class));
        lane.addToLinkId(Id.create("l2", Link.class));
        lane.setAlignment("0");
        assignment.addLane(lane);
        lanes.addAssignment(assignment);

        validate(lanes);
    }

    @Test
    void startsAtPresentWriterOutputValidatesAgainstPublishedSchema() throws Exception {
        Lanes lanes = new Lanes();
        LanesToLinkAssignment assignment = new LanesToLinkAssignment(Id.create("l1", Link.class));
        Lane lane = new Lane(Id.create("l1_l0", Lane.class));
        lane.addToLinkId(Id.create("l2", Link.class));
        lane.setStartsAtMeterFromLinkEnd(45.0);
        lane.setAlignment("2");
        assignment.addLane(lane);
        lanes.addAssignment(assignment);

        validate(lanes);
    }

    @Test
    void bothPresentLaneNormalizedOutputValidatesAgainstPublishedSchema() throws Exception {
        Lanes lanes = new Lanes();
        LanesToLinkAssignment assignment = new LanesToLinkAssignment(Id.create("l1", Link.class));
        Lane lane = new Lane(Id.create("l1_l0", Lane.class));
        lane.addToLinkId(Id.create("l2", Link.class));
        lane.addToLaneId(Id.create("l1_l1", Lane.class));
        lane.setAlignment("0");
        assignment.addLane(lane);
        lanes.addAssignment(assignment);

        validate(lanes);
    }

    @Test
    void nonIntegerAlignmentNormalizedOutputValidatesAgainstPublishedSchema() throws Exception {        Lanes lanes = new Lanes();
        LanesToLinkAssignment assignment = new LanesToLinkAssignment(Id.create("l1", Link.class));
        Lane lane = new Lane(Id.create("l1_l0", Lane.class));
        lane.addToLinkId(Id.create("l2", Link.class));
        lane.setAlignment("left");
        assignment.addLane(lane);
        lanes.addAssignment(assignment);

        String xml = validate(lanes);
        Lanes reparsed = new LanesXmlReader().read(xml);
        Lane reparsedLane = reparsed.getLanesToLinkAssignments().get(Id.create("l1", Link.class))
                .getLanes().get(Id.create("l1_l0", Lane.class));
        assertEquals("0", reparsedLane.getAlignment());
        assertEquals("default",
                reparsedLane.getAttributes().getAttribute("osm:lane.alignmentProvenance"));
        assertEquals("left",
                reparsedLane.getAttributes().getAttribute("osm:lane.alignmentRaw"));
    }

    private String validate(Lanes lanes) throws Exception {
        String xml = new LanesXmlWriter().writeToString(lanes);
        SchemaFactory factory = SchemaFactory.newInstance(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI);
        // systemId must be the resource URL so the XSD's <xs:include schemaLocation="matsimCommon.xsd"/>
        // resolves relative to it (matsimCommon.xsd is already vendored under matsim-spec/).
        var schemaUrl = getClass().getClassLoader().getResource("matsim-spec/laneDefinitions_v2.0.xsd");
        Schema schema = factory.newSchema(schemaUrl);
        assertDoesNotThrow(() -> schema.newValidator()
                .validate(new StreamSource(new StringReader(xml))));
        return xml;
    }
}

package com.citymodeler.matsim.models.io;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.StringReader;

import javax.xml.transform.stream.StreamSource;
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

        String xml = new LanesXmlWriter().writeToString(lanes);
        var factory = SchemaFactory.newInstance(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI);
        // systemId must be the resource URL so the XSD's <xs:include schemaLocation="matsimCommon.xsd"/>
        // resolves relative to it (matsimCommon.xsd is already vendored under matsim-spec/).
        var schemaUrl = getClass().getClassLoader().getResource("matsim-spec/laneDefinitions_v2.0.xsd");
        var schema = factory.newSchema(schemaUrl);
        assertDoesNotThrow(() -> schema.newValidator()
                .validate(new StreamSource(new StringReader(xml))));
    }
}

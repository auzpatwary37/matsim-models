package com.citymodeler.matsim.models.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.facilities.ActivityFacilities;
import com.citymodeler.matsim.models.facilities.ActivityFacility;

class FacilitiesXmlTest {
    @Test
    void readsWritesAndReadsFacilitiesXml() {
        String xml = """
                <facilities name=\"test-facilities\">
                    <facility id=\"f1\" x=\"10.0\" y=\"20.0\" linkId=\"l1\" desc=\"Main hub\">
                        <attributes>
                            <attribute name=\"facility-kind\" class=\"java.lang.String\">station</attribute>
                        </attributes>
                        <activity type=\"work\" capacity=\"250.0\" />
                    </facility>
                </facilities>
                """;

        ActivityFacilities facilities = new FacilitiesXmlReader().read(xml);
        String roundTrippedXml = new FacilitiesXmlWriter().writeToString(facilities);
        ActivityFacilities roundTripped = new FacilitiesXmlReader().read(roundTrippedXml);

        assertEquals("test-facilities", roundTripped.getName());
        assertEquals(1, roundTripped.getFacilities().size());
        ActivityFacility facility = roundTripped.getFacilities().get(Id.create("f1", ActivityFacility.class));
        assertEquals(10.0, facility.getCoord().getX());
        assertEquals(20.0, facility.getCoord().getY());
        assertEquals("l1", facility.getLinkId().toString());
        assertEquals("Main hub", facility.getDesc());
        assertEquals("station", facility.getAttributes().getAttribute("facility-kind"));
        assertEquals(250.0, facility.getActivityOptions().get("work").getCapacity());
    }

    @Test
    void loadFromClasspathFixture() {
        String fixturePath = "fixtures/facilities.xml";
        InputStream is = getClass().getClassLoader().getResourceAsStream(fixturePath);
        ActivityFacilities facilities = new FacilitiesXmlReader().read(is);

        assertNotNull(facilities);
        assertEquals("test-facilities", facilities.getName());
        assertEquals(3, facilities.getFacilities().size());

        ActivityFacility f1 = facilities.getFacilities().get(Id.create("f1", ActivityFacility.class));
        assertEquals("residential", f1.getAttributes().getAttribute("facility-kind"));
    }
}

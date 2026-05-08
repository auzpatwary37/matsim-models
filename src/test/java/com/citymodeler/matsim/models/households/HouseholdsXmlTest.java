package com.citymodeler.matsim.models.households;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.io.HouseholdsXmlReader;
import com.citymodeler.matsim.models.io.HouseholdsXmlWriter;

class HouseholdsXmlTest {

    @Test
    void readsWritesAndReadsHouseholdsXml() {
        String xml = """
                <households>
                    <household id="h1" income="50000">
                        <members><personId ref="p1"/><personId ref="p2"/></members>
                    </household>
                </households>
                """;

        Households households = new HouseholdsXmlReader().read(xml);
        assertEquals(1, households.getHouseholds().size());

        Household h1 = households.getHouseholds().get(Id.create("h1", Household.class));
        assertEquals("50000", h1.getIncome());
        assertEquals(2, h1.getMemberIds().size());
    }

    @Test
    void roundTripPreservesHouseholdData() {
        String xml = """
                <households>
                    <household id="h1" income="50000">
                        <members><personId ref="p1"/></members>
                    </household>
                </households>
                """;

        Households households = new HouseholdsXmlReader().read(xml);
        String roundTrippedXml = new HouseholdsXmlWriter().writeToString(households);
        Households roundTripped = new HouseholdsXmlReader().read(roundTrippedXml);

        assertEquals(1, roundTripped.getHouseholds().size());
        Household h1 = roundTripped.getHouseholds().get(Id.create("h1", Household.class));
        assertEquals("50000", h1.getIncome());
        assertEquals(1, h1.getMemberIds().size());
    }

    @Test
    void readFromClasspathFixture() {
        String fixturePath = "fixtures/households.xml";
        InputStream is = getClass().getClassLoader().getResourceAsStream(fixturePath);
        Households households = new HouseholdsXmlReader().read(is);

        assertNotNull(households);
        assertEquals(1, households.getHouseholds().size());

        Household h1 = households.getHouseholds().get(Id.create("h1", Household.class));
        assertEquals("50000", h1.getIncome());
        assertEquals(2, h1.getMemberIds().size());
    }

    @Test
    void readHouseholdsWithAttributes() {
        String xml = """
                <households>
                    <household id="h1" income="50000">
                        <members><personId ref="p1"/></members>
                        <attributes>
                            <attribute name="cars" class="java.lang.Integer">2</attribute>
                        </attributes>
                    </household>
                </households>
                """;

        Households households = new HouseholdsXmlReader().read(xml);
        Household h1 = households.getHouseholds().get(Id.create("h1", Household.class));
        assertEquals(2, h1.getAttributes().getAttribute("cars"));
    }

    @Test
    void writeHouseholdsWithAttributes() {
        Households households = new Households();
        Household household = new Household(Id.create("h1", Household.class), "50000");
        household.getMemberIds().add(Id.create("p1", Person.class));
        household.getAttributes().putAttribute("cars", 2);
        households.addHousehold(household);

        String xml = new HouseholdsXmlWriter().writeToString(households);
        Households roundTripped = new HouseholdsXmlReader().read(xml);

        Household h1 = roundTripped.getHouseholds().get(Id.create("h1", Household.class));
        assertEquals(2, h1.getAttributes().getAttribute("cars"));
    }
}
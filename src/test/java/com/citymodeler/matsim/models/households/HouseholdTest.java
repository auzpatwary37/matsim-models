package com.citymodeler.matsim.models.households;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Id;

class HouseholdTest {

    @Test
    void householdStoresIdIncomeAndMemberIds() {
        Household household = new Household(Id.create("h1", Household.class), "60000");

        assertEquals("h1", household.getId().toString());
        assertEquals("60000", household.getIncome());
    }

    @Test
    void householdManagesMemberIds() {
        Household household = new Household(Id.create("h1", Household.class), "50000");
        Id<Person> p1 = Id.create("p1", Person.class);
        Id<Person> p2 = Id.create("p2", Person.class);

        household.getMemberIds().add(p1);
        household.getMemberIds().add(p2);

        assertEquals(2, household.getMemberIds().size());
    }

    @Test
    void householdHasAttributes() {
        Household household = new Household(Id.create("h1", Household.class), "50000");

        household.getAttributes().putAttribute("cars", 2);

        assertEquals(2, household.getAttributes().getAttribute("cars"));
    }

    @Test
    void householdIncomeIsModifiable() {
        Household household = new Household(Id.create("h1", Household.class), "50000");

        household.setIncome("75000");

        assertEquals("75000", household.getIncome());
    }

    @Test
    void householdAttributesIsAccessible() {
        Household household = new Household(Id.create("h1", Household.class), "50000");

        assertNotNull(household.getAttributes());
        household.getAttributes().putAttribute("key", "value");
        assertEquals("value", household.getAttributes().getAttribute("key"));
    }

    @Test
    void householdRequiresNonNullId() {
        assertThrows(NullPointerException.class, () ->
            new Household(null, "50000"));
    }
}
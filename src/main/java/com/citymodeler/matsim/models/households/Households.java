package com.citymodeler.matsim.models.households;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.citymodeler.matsim.models.api.Id;

public final class Households {
    private final Map<Id<Household>, Household> households = new LinkedHashMap<>();

    public void addHousehold(Household household) {
        if (household == null) {
            throw new NullPointerException("household");
        }
        households.put(household.getId(), household);
    }

    public Map<Id<Household>, Household> getHouseholds() {
        return Collections.unmodifiableMap(households);
    }
}
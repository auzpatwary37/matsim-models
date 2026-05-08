package com.citymodeler.matsim.models.households;

import com.citymodeler.matsim.models.api.Id;

public final class Person {
    private final Id<Person> id;

    public Person(Id<Person> id) {
        this.id = id;
    }

    public Id<Person> getId() {
        return id;
    }
}
package com.citymodeler.matsim.models.population;

import java.util.LinkedHashMap;
import java.util.Map;

import com.citymodeler.matsim.models.api.Id;

public final class Population {
    private final Map<Id<Person>, Person> persons = new LinkedHashMap<>();

    public Map<Id<Person>, Person> getPersons() {
        return persons;
    }

    public void addPerson(Person person) {
        persons.put(person.getId(), person);
    }
}
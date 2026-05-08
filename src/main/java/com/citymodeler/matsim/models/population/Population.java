package com.citymodeler.matsim.models.population;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.citymodeler.matsim.models.api.Attributes;
import com.citymodeler.matsim.models.api.Id;

public final class Population {
    private final Map<Id<Person>, Person> persons = new LinkedHashMap<>();
    private final Attributes attributes = new Attributes();

    public Map<Id<Person>, Person> getPersons() {
        return Collections.unmodifiableMap(persons);
    }

    public Attributes getAttributes() {
        return attributes;
    }

    public void addPerson(Person person) {
        persons.put(Objects.requireNonNull(person, "person").getId(), person);
    }
}
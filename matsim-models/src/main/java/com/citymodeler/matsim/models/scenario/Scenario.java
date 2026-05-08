package com.citymodeler.matsim.models.scenario;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.citymodeler.matsim.models.api.Attributes;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.config.Config;
import com.citymodeler.matsim.models.facilities.ActivityFacilities;
import com.citymodeler.matsim.models.lanes.Lanes;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.population.Person;
import com.citymodeler.matsim.models.transit.TransitSchedule;

public final class Scenario {
    private Config config;
    private Network network;
    private TransitSchedule transitSchedule;
    private ActivityFacilities activityFacilities;
    private final Map<Id<Person>, Person> population = new LinkedHashMap<>();
    private Lanes lanes;
    private final Attributes attributes = new Attributes();

    public Config getConfig() {
        return config;
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    public Network getNetwork() {
        return network;
    }

    public void setNetwork(Network network) {
        this.network = network;
    }

    public TransitSchedule getTransitSchedule() {
        return transitSchedule;
    }

    public void setTransitSchedule(TransitSchedule transitSchedule) {
        this.transitSchedule = transitSchedule;
    }

    public ActivityFacilities getActivityFacilities() {
        return activityFacilities;
    }

    public void setActivityFacilities(ActivityFacilities activityFacilities) {
        this.activityFacilities = activityFacilities;
    }

    public Map<Id<Person>, Person> getPopulation() {
        return Collections.unmodifiableMap(population);
    }

    public void addPerson(Person person) {
        population.put(Objects.requireNonNull(person, "person").getId(), person);
    }

    public Lanes getLanes() {
        return lanes;
    }

    public void setLanes(Lanes lanes) {
        this.lanes = lanes;
    }

    public Attributes getAttributes() {
        return attributes;
    }

    public void postProcess() {
        if (network != null) {
            network.postProcess();
        }
        if (transitSchedule != null) {
            transitSchedule.postProcess();
        }
        for (Person person : population.values()) {
            person.postProcess();
        }
    }
}

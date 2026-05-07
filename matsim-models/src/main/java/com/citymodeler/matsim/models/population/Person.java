package com.citymodeler.matsim.models.population;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.citymodeler.matsim.models.api.Attributes;
import com.citymodeler.matsim.models.api.Id;

public final class Person {
    private final Id<Person> id;
    private final List<Plan> plans = new ArrayList<>();
    private Plan selectedPlan;
    private final Attributes attributes = new Attributes();

    public Person(Id<Person> id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public Id<Person> getId() {
        return id;
    }

    public List<Plan> getPlans() {
        return plans;
    }

    public void addPlan(Plan plan) {
        plans.add(plan);
    }

    public Plan getSelectedPlan() {
        return selectedPlan;
    }

    public void setSelectedPlan(Plan selectedPlan) {
        this.selectedPlan = selectedPlan;
    }

    public Attributes getAttributes() {
        return attributes;
    }

    public void postProcess() {
        for (Plan plan : plans) {
            plan.setPerson(this);
        }
        if (selectedPlan == null && !plans.isEmpty()) {
            selectedPlan = plans.get(0);
        }
    }
}

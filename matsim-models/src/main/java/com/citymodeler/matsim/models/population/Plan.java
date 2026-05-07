package com.citymodeler.matsim.models.population;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.citymodeler.matsim.models.api.Attributes;

public final class Plan {
    private final List<PlanElement> planElements = new ArrayList<>();
    private String type;
    private Double score;
    private boolean selected;
    private Person person;
    private final Attributes attributes = new Attributes();

    public List<PlanElement> getPlanElements() {
        return Collections.unmodifiableList(planElements);
    }

    public void addPlanElement(PlanElement planElement) {
        planElements.add(planElement);
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public Person getPerson() {
        return person;
    }

    public void setPerson(Person person) {
        this.person = person;
    }

    public Attributes getAttributes() {
        return attributes;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }
}

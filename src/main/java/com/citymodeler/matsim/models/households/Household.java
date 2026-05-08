package com.citymodeler.matsim.models.households;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.citymodeler.matsim.models.api.Attributes;
import com.citymodeler.matsim.models.api.Id;

public final class Household {
    private final Id<Household> id;
    private String income;
    private final List<Id<Person>> memberIds;
    private final Attributes attributes;

    public Household(Id<Household> id, String income) {
        this.id = Objects.requireNonNull(id, "id");
        this.income = income;
        this.memberIds = new ArrayList<>();
        this.attributes = new Attributes();
    }

    public Id<Household> getId() {
        return id;
    }

    public String getIncome() {
        return income;
    }

    public void setIncome(String income) {
        this.income = income;
    }

    public List<Id<Person>> getMemberIds() {
        return Collections.unmodifiableList(memberIds);
    }

    public void addMemberId(Id<Person> memberId) {
        memberIds.add(Objects.requireNonNull(memberId, "memberId"));
    }

    public Attributes getAttributes() {
        return attributes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Household that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}

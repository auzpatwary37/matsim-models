package com.citymodeler.matsim.models.lanes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;

public final class Lanes {
    private final Map<Id<Link>, LanesToLinkAssignment> lanesToLinkAssignments = new LinkedHashMap<>();

    public Map<Id<Link>, LanesToLinkAssignment> getLanesToLinkAssignments() {
        return lanesToLinkAssignments;
    }

    public void addAssignment(LanesToLinkAssignment assignment) {
        Objects.requireNonNull(assignment, "assignment");
        lanesToLinkAssignments.put(assignment.getLinkId(), assignment);
    }
}

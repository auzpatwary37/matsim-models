package com.citymodeler.matsim.models.signals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;

/**
 * The turning-move restrictions of one signal (wire family
 * signalSystems_v2.0, element {@code <turningMoveRestrictions>}), listing the
 * downstream links a movement controlled by the signal is allowed to reach.
 * Restrictions are scoped to their owning signal; ids are unique per signal
 * system, never globally.
 */
public final class TurningMoveRestrictions {
    private final List<Id<Link>> toLinks = new ArrayList<>();

    public List<Id<Link>> getToLinks() {
        return Collections.unmodifiableList(toLinks);
    }

    public void addToLink(Id<Link> toLink) {
        toLinks.add(Objects.requireNonNull(toLink, "toLink"));
    }
}

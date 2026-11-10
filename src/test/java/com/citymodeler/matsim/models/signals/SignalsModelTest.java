package com.citymodeler.matsim.models.signals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SignalsModelTest {
    @Test
    void signalSystemKeysSignalsById() {
        SignalSystem system = new SignalSystem(Id.create("rax", SignalSystem.class));
        Signal signal = new Signal(Id.create("rax_1", Signal.class), Id.createLinkId("l1"));
        signal.addLaneRef(Id.create("l1_1", com.citymodeler.matsim.models.lanes.Lane.class));
        signal.getRestrictions().addToLink(Id.createLinkId("l2"));
        system.addSignal(signal);
        assertEquals(Set.of("rax_1"), system.getSignals().keySet().stream().map(Id::toString).collect(java.util.stream.Collectors.toSet()));
        assertEquals("l1", system.getSignals().get(Id.create("rax_1", Signal.class)).getLinkIdRef().toString());
        assertEquals(List.of("l1_1"), signal.getLaneRefs().stream().map(Id::toString).toList());
        assertEquals(List.of("l2"), signal.getRestrictions().getToLinks().stream().map(Id::toString).toList());
    }

    @Test
    void signalRequiresLinkIdRef() {
        assertThrows(NullPointerException.class,
            () -> new Signal(Id.create("rax_1", Signal.class), null));
    }

    @Test
    void signalGroupKeepsSignalRefsInInsertionOrder() {
        SignalGroup group = new SignalGroup(Id.create("g1", SignalGroup.class));
        group.addSignalRef(Id.create("rax_1", Signal.class));
        group.addSignalRef(Id.create("rax_2", Signal.class));
        assertEquals(List.of("rax_1", "rax_2"),
            group.getSignalRefIds().stream().map(Id::toString).toList());
    }

    @Test
    void containersRejectNullAndExposeUnmodifiableViews() {
        SignalSystem system = new SignalSystem(Id.create("rax", SignalSystem.class));
        assertThrows(NullPointerException.class, () -> system.addSignal(null));
        assertThrows(UnsupportedOperationException.class, () -> system.getSignals().clear());
        assertTrue(new SignalSystemGroups(Id.create("rax", SignalSystem.class)).getGroups().isEmpty());
    }
}

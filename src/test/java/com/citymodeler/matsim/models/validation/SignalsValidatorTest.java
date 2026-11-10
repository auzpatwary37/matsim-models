package com.citymodeler.matsim.models.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.io.NetworkXmlReader;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.signals.Signal;
import com.citymodeler.matsim.models.signals.SignalGroup;
import com.citymodeler.matsim.models.signals.SignalSystem;
import com.citymodeler.matsim.models.signals.SignalSystemGroups;
import com.citymodeler.matsim.models.signals.control.ControlledSignalSystem;
import com.citymodeler.matsim.models.signals.control.SignalGroupSettings;
import com.citymodeler.matsim.models.signals.control.SignalPlan;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SignalsValidatorTest {
    private static final String NET = """
            <network>
                <nodes><node id="n1" x="0" y="0"/><node id="n2" x="1" y="0"/></nodes>
                <links>
                    <link id="l1" from="n1" to="n2" length="10" freespeed="10" capacity="100" permlanes="1" oneway="1" modes="car"/>
                    <link id="l2" from="n2" to="n1" length="10" freespeed="10" capacity="100" permlanes="1" oneway="1" modes="car"/>
                </links>
            </network>
            """;

    private Network network() { return new NetworkXmlReader().read(NET); }

    private SignalSystem system() {
        SignalSystem system = new SignalSystem(Id.create("rax", SignalSystem.class));
        system.addSignal(new Signal(Id.create("rax_1", Signal.class), Id.createLinkId("l1")));
        return system;
    }

    @Test
    void cleanDataValidatesWithoutErrors() {
        Map<Id<SignalSystem>, SignalSystem> systems = new LinkedHashMap<>();
        systems.put(Id.create("rax", SignalSystem.class), system());
        Map<Id<SignalSystem>, SignalSystemGroups> groups = new LinkedHashMap<>();
        SignalSystemGroups g = new SignalSystemGroups(Id.create("rax", SignalSystem.class));
        SignalGroup group = new SignalGroup(Id.create("g1", SignalGroup.class));
        group.addSignalRef(Id.create("rax_1", Signal.class));
        g.addGroup(group);
        groups.put(Id.create("rax", SignalSystem.class), g);
        Map<Id<SignalSystem>, ControlledSignalSystem> control = new LinkedHashMap<>();
        ControlledSignalSystem c = new ControlledSignalSystem(Id.create("rax", SignalSystem.class));
        SignalPlan plan = new SignalPlan();
        plan.addSetting(new SignalGroupSettings(Id.create("g1", SignalGroup.class), 0, 60));
        c.addPlan(plan);
        control.put(Id.create("rax", SignalSystem.class), c);

        ValidationReport report = new SignalsValidator(network())
            .validate(systems, groups, control, null, null);
        assertTrue(report.getErrors().isEmpty(), () -> report.getIssues().toString());
    }

    @Test
    void danglingRefsAreErrors() {
        Map<Id<SignalSystem>, SignalSystem> systems = new LinkedHashMap<>();
        systems.put(Id.create("rax", SignalSystem.class), system());
        Map<Id<SignalSystem>, SignalSystemGroups> groups = new LinkedHashMap<>();
        SignalSystemGroups g = new SignalSystemGroups(Id.create("rax", SignalSystem.class));
        SignalGroup group = new SignalGroup(Id.create("g1", SignalGroup.class));
        group.addSignalRef(Id.create("ghost", Signal.class));   // dangling
        g.addGroup(group);
        groups.put(Id.create("rax", SignalSystem.class), g);

        ValidationReport report = new SignalsValidator(network())
            .validate(systems, groups, Map.of(), null, null);
        assertTrue(report.getErrors().stream().anyMatch(i -> i.getCode().equals("DANGLING_SIGNAL_REF")));
        ValidationReport withUnknown = new SignalsValidator(network())
            .validate(systems, groups, Map.of(), null, null, List.of("signalGroups:g1:mystery"));
        assertTrue(withUnknown.getWarnings().stream().anyMatch(i -> i.getCode().equals("UNKNOWN_ELEMENT")));
        assertEquals(1, report.getErrors().size(), "only DANGLING_SIGNAL_REF here — groups name rax which IS in systems");
    }

    @Test
    void groupsWithUnknownSystemWarnsWhenSystemsEmptyErrorsOtherwise() {
        Map<Id<SignalSystem>, SignalSystemGroups> groups = new LinkedHashMap<>();
        SignalSystemGroups g = new SignalSystemGroups(Id.create("ghost-system", SignalSystem.class));
        g.addGroup(new SignalGroup(Id.create("g1", SignalGroup.class)));
        groups.put(Id.create("ghost-system", SignalSystem.class), g);

        ValidationReport emptySystems = new SignalsValidator(network())
            .validate(Map.of(), groups, Map.of(), null, null);
        assertTrue(emptySystems.getWarnings().stream().anyMatch(i -> i.getCode().equals("UNKNOWN_SYSTEM")),
            "systems not provided -> WARNING (partial-file validation is legitimate)");

        ValidationReport nonEmptySystems = new SignalsValidator(network())
            .validate(Map.of(Id.create("rax", SignalSystem.class), system()), groups, Map.of(), null, null);
        assertTrue(nonEmptySystems.getErrors().stream().anyMatch(i -> i.getCode().equals("UNKNOWN_SYSTEM")),
            "systems provided but missing the referenced id -> ERROR");
    }

    @Test
    void linkRefOutsideNetworkIsError() {
        SignalSystem system = new SignalSystem(Id.create("rax", SignalSystem.class));
        system.addSignal(new Signal(Id.create("rax_1", Signal.class), Id.createLinkId("ghost-link")));
        Map<Id<SignalSystem>, SignalSystem> systems = new LinkedHashMap<>();
        systems.put(Id.create("rax", SignalSystem.class), system);
        ValidationReport report = new SignalsValidator(network())
            .validate(systems, Map.of(), Map.of(), null, null);
        assertTrue(report.getErrors().stream().anyMatch(i -> i.getCode().equals("LINK_REF_NOT_IN_NETWORK")));
    }
}

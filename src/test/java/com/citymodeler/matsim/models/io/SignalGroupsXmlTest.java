package com.citymodeler.matsim.models.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.signals.SignalGroup;
import com.citymodeler.matsim.models.signals.SignalSystem;
import com.citymodeler.matsim.models.signals.SignalSystemGroups;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SignalGroupsXmlTest {
    @Test
    void groupMembershipIsSignalRefsNotLanes() {
        String xml = """
                <signalGroups>
                    <signalSystem refId="rax">
                        <signalGroup id="g1">
                            <signal refId="rax_1"/>
                            <signal refId="rax_2"/>
                            <attributes>
                                <attribute name="protected-left" class="java.lang.Boolean">true</attribute>
                            </attributes>
                        </signalGroup>
                    </signalSystem>
                </signalGroups>
                """;
        Map<Id<SignalSystem>, SignalSystemGroups> groups = new SignalGroupsXmlReader().read(xml);
        Map<Id<SignalSystem>, SignalSystemGroups> again =
            new SignalGroupsXmlReader().read(new SignalGroupsXmlWriter().writeToString(groups));

        SignalSystemGroups rax = again.get(Id.create("rax", SignalSystem.class));
        assertEquals("rax", rax.getId().toString());
        SignalGroup g1 = rax.getGroups().get(Id.create("g1", SignalGroup.class));
        assertEquals(java.util.List.of("rax_1", "rax_2"),
            g1.getSignalRefIds().stream().map(Id::toString).toList());
        assertEquals(Boolean.TRUE, g1.getAttributes().getAttribute("protected-left"));
    }

    @Test
    void wrongRootElementIsRejected() {
        String wrongRoot = "<signalSystems><signalSystem id=\"rax\"/></signalSystems>";
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> new SignalGroupsXmlReader().read(wrongRoot));
    }
}

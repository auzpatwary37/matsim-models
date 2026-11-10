package com.citymodeler.matsim.models.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.signals.SignalGroup;
import com.citymodeler.matsim.models.signals.SignalSystem;
import com.citymodeler.matsim.models.signals.control.ControlledSignalSystem;
import com.citymodeler.matsim.models.signals.control.SignalPlan;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SignalControlXmlTest {
    @Test
    void readsWritesAndReadsSignalControlXml() {
        String xml = """
                <signalControl>
                    <signalSystem refId="rax">
                        <signalSystemController>
                            <controllerIdentifier>DefaultActuatedController</controllerIdentifier>
                            <signalPlan id="p1">
                                <start daytime="06:00:00"/>
                                <stop daytime="22:00:00"/>
                                <cycleTime sec="90"/>
                                <offset sec="5"/>
                                <signalGroupSettings refId="g1">
                                    <onset sec="0"/>
                                    <dropping sec="40"/>
                                    <attributes>
                                        <attribute name="note" class="java.lang.String">am</attribute>
                                    </attributes>
                                </signalGroupSettings>
                                <signalGroupSettings refId="g2">
                                    <onset sec="40"/>
                                    <dropping sec="90"/>
                                </signalGroupSettings>
                            </signalPlan>
                            <signalPlan>
                                <cycleTime sec="60"/>
                                <signalGroupSettings refId="g1"><onset sec="0"/><dropping sec="60"/></signalGroupSettings>
                            </signalPlan>
                        </signalSystemController>
                    </signalSystem>
                </signalControl>
                """;
        Map<Id<SignalSystem>, ControlledSignalSystem> control = new SignalControlXmlReader().read(xml);
        Map<Id<SignalSystem>, ControlledSignalSystem> again =
            new SignalControlXmlReader().read(new SignalControlXmlWriter().writeToString(control));

        ControlledSignalSystem rax = again.get(Id.create("rax", SignalSystem.class));
        assertEquals("DefaultActuatedController", rax.getControllerIdentifier());
        assertEquals(2, rax.getPlans().size(), "plans keep file order, second plan has no id");
        SignalPlan am = rax.getPlans().get(0);
        assertEquals("p1", am.getId());
        assertEquals("06:00:00", am.getStartDaytime());
        assertEquals("22:00:00", am.getStopDaytime());
        assertEquals(90, am.getCycleTimeSec());
        assertEquals(5, am.getOffsetSec());
        assertEquals(java.util.List.of("g1", "g2"), am.getSettings().keySet().stream().map(Id::toString).toList());
        assertEquals(40, am.getSettings().get(Id.create("g1", SignalGroup.class)).getDroppingSec());
        assertEquals("am", am.getSettings().get(Id.create("g1", SignalGroup.class)).getAttributes().getAttribute("note"));
        assertEquals(null, rax.getPlans().get(1).getId());
        assertEquals(null, rax.getPlans().get(1).getStartDaytime());
    }

    @Test
    void negativeSecondsRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> new com.citymodeler.matsim.models.signals.control.SignalGroupSettings(
                Id.create("g1", SignalGroup.class), -1, 5));
    }
}

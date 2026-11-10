package com.citymodeler.matsim.models.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.signals.Signal;
import com.citymodeler.matsim.models.signals.SignalSystem;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SignalSystemsXmlTest {
    @Test
    void readsWritesAndReadsSignalSystemsXml() {
        String xml = """
                <signalSystems>
                    <signalSystem id="rax">
                        <attributes>
                            <attribute name="network" class="java.lang.String">berlin</attribute>
                        </attributes>
                        <signals>
                            <signal id="rax_1" linkIdRef="l1">
                                <lane idRef="l1_1"/>
                                <turningMoveRestrictions>
                                    <toLink idRef="l2"/>
                                    <toLink idRef="l3"/>
                                </turningMoveRestrictions>
                                <attributes>
                                    <attribute name="kind" class="java.lang.String">trafficLight</attribute>
                                </attributes>
                            </signal>
                        </signals>
                    </signalSystem>
                </signalSystems>
                """;
        Map<Id<SignalSystem>, SignalSystem> systems = new SignalSystemsXmlReader().read(xml);
        String roundTripped = new SignalSystemsXmlWriter().writeToString(systems);
        Map<Id<SignalSystem>, SignalSystem> again = new SignalSystemsXmlReader().read(roundTripped);

        assertEquals(1, again.size());
        SignalSystem system = again.get(Id.create("rax", SignalSystem.class));
        assertEquals("berlin", system.getAttributes().getAttribute("network"));
        Signal signal = system.getSignals().get(Id.create("rax_1", Signal.class));
        assertEquals("l1", signal.getLinkIdRef().toString());
        assertEquals(1, signal.getLaneRefs().size());
        assertEquals("l1_1", signal.getLaneRefs().get(0).toString());
        assertEquals(2, signal.getRestrictions().getToLinks().size());
        assertEquals("trafficLight", signal.getAttributes().getAttribute("kind"));
    }

    @Test
    void recordsUnmodeledChildrenAndClearsThemOnEachRead() {
        String withUnknowns = """
                <signalSystems>
                    <signalSystem id="s1">
                        <mysterySystem/>
                        <signals>
                            <signal id="sig1" linkIdRef="l1">
                                <mysterySignal/>
                            </signal>
                            <mysterySibling/>
                        </signals>
                    </signalSystem>
                    <mysteryRoot/>
                </signalSystems>
                """;
        SignalSystemsXmlReader reader = new SignalSystemsXmlReader();
        reader.read(withUnknowns);

        assertEquals(4, reader.getUnknownElements().size());
        assertTrue(reader.getUnknownElements().contains("signalSystems:signalSystem:mysterySystem"));
        assertTrue(reader.getUnknownElements().contains("signalSystems:signal:mysterySignal"));
        assertTrue(reader.getUnknownElements().contains("signalSystems:signals:mysterySibling"));
        assertTrue(reader.getUnknownElements().contains("signalSystems:signalSystems:mysteryRoot"));
        assertThrows(UnsupportedOperationException.class,
                () -> reader.getUnknownElements().add("not-allowed"));

        reader.read("<signalSystems><signalSystem id=\"s1\"><signals/></signalSystem></signalSystems>");
        assertTrue(reader.getUnknownElements().isEmpty());
    }

    @Test
    void wrongRootElementIsRejected() {
        String wrongRoot = "<signalControl><signalSystem refId=\"rax\"/></signalControl>";
        assertThrows(IllegalArgumentException.class, () -> new SignalSystemsXmlReader().read(wrongRoot));
    }
}

package com.citymodeler.matsim.models.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.signals.Signal;
import com.citymodeler.matsim.models.signals.SignalSystem;
import com.citymodeler.matsim.models.signals.timings.AmberTimes;
import com.citymodeler.matsim.models.signals.timings.SystemAmberTimes;
import org.junit.jupiter.api.Test;

class AmberTimesXmlTest {
    @Test
    void globalDefaultsAndPerSignalOverridesRoundTrip() {
        String xml = """
                <amberTimes>
                    <globalDefaults>
                        <redAmber seconds="1"/>
                        <amber seconds="3"/>
                    </globalDefaults>
                    <signalSystem refId="rax">
                        <systemDefaults>
                            <amber seconds="4"/>
                        </systemDefaults>
                        <signal refId="rax_1">
                            <redAmber seconds="2"/>
                            <attributes>
                                <attribute name="policy" class="java.lang.String">nema</attribute>
                            </attributes>
                        </signal>
                    </signalSystem>
                </amberTimes>
                """;
        AmberTimes times = new AmberTimesXmlReader().read(xml);
        AmberTimes again = new AmberTimesXmlReader().read(new AmberTimesXmlWriter().writeToString(times));

        assertEquals(1, again.getGlobalDefaults().getRedAmberSec());
        assertEquals(3, again.getGlobalDefaults().getAmberSec());
        SystemAmberTimes rax = again.getSystems().get(Id.create("rax", SignalSystem.class));
        assertEquals(4, rax.getSystemDefaults().getAmberSec());
        assertEquals(null, rax.getSystemDefaults().getRedAmberSec());
        assertEquals(2, rax.getSignalOverrides().get(Id.create("rax_1", Signal.class)).getRedAmberSec());
        assertEquals("nema", rax.getSignalOverrides().get(Id.create("rax_1", Signal.class)).getAttributes().getAttribute("policy"));
    }
}

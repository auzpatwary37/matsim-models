package com.citymodeler.matsim.models.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class SignalConfigGroupTest {
    @Test
    void typedAccessorsRoundTripThroughGenericConfig() {
        Config config = new Config();
        SignalConfigGroup signals = SignalConfigGroup.of(config);
        signals.setSignalSystemsPath("signals/systems.xml");
        signals.setSignalGroupsPath("signals/groups.xml");
        signals.setSignalControlPath("signals/control.xml");
        signals.setHandler("SignalSystemConfigGroup");
        signals.addParam("futureMatSimParam", "42");

        String xml = new com.citymodeler.matsim.models.io.ConfigXmlWriter().writeToString(config);
        Config reparsed = new com.citymodeler.matsim.models.io.ConfigXmlReader().readString(xml);
        SignalConfigGroup again = SignalConfigGroup.of(reparsed);

        assertEquals("signals/systems.xml", again.getSignalSystemsPath().orElseThrow());
        assertEquals("signals/control.xml", again.getSignalControlPath().orElseThrow());
        assertEquals(Optional.of("SignalSystemConfigGroup"), again.getHandler());
        assertEquals("42", again.getGroup().getParam("futureMatSimParam").orElseThrow(),
            "unknown params survive via the generic module");
        assertTrue(again.getSignalAmberTimesPath().isEmpty());
    }

    @Test
    void settersGettersAndRemoversAreSymmetric() {
        Config config = new Config();
        SignalConfigGroup signals = SignalConfigGroup.of(config);
        signals.setOffset("7");
        assertEquals(Optional.of("7"), signals.getOffset());
        signals.removeOffset();
        assertTrue(signals.getOffset().isEmpty());
        signals.removeOffset();
        assertTrue(signals.getOffset().isEmpty());
        assertEquals("signals", signals.getGroup().getName());
    }
}

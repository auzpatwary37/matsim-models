package com.citymodeler.matsim.models.signals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.citymodeler.matsim.models.io.AmberTimesXmlReader;
import com.citymodeler.matsim.models.io.AmberTimesXmlWriter;
import com.citymodeler.matsim.models.io.IntergreenTimesXmlReader;
import com.citymodeler.matsim.models.io.IntergreenTimesXmlWriter;
import com.citymodeler.matsim.models.io.SignalControlXmlReader;
import com.citymodeler.matsim.models.io.SignalControlXmlWriter;
import com.citymodeler.matsim.models.io.SignalGroupsXmlReader;
import com.citymodeler.matsim.models.io.SignalGroupsXmlWriter;
import com.citymodeler.matsim.models.io.SignalSystemsXmlReader;
import com.citymodeler.matsim.models.io.SignalSystemsXmlWriter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SignalsFixtureRoundTripTest {
    private static String fixture(String name) throws Exception {
        try (InputStream in = SignalsFixtureRoundTripTest.class.getResourceAsStream("/signals/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void systemsFixpoint() throws Exception {
        var xml = fixture("systems.xml");
        var r1 = new SignalSystemsXmlReader(); var once = r1.read(xml);
        assertTrue(r1.getUnknownElements().isEmpty());
        var twice = new SignalSystemsXmlReader().read(new SignalSystemsXmlWriter().writeToString(once));
        assertEquals(once.keySet(), twice.keySet());
        for (var e : once.entrySet()) {
            var a = e.getValue(); var b = twice.get(e.getKey());
            assertEquals(a.getSignals().size(), b.getSignals().size());
            assertEquals(a.getAttributes().getAsMap(), b.getAttributes().getAsMap());
        }
    }

    @Test
    void groupsFixpoint() throws Exception {
        var xml = fixture("groups.xml");
        var r1 = new SignalGroupsXmlReader(); var once = r1.read(xml);
        assertTrue(r1.getUnknownElements().isEmpty());
        var twice = new SignalGroupsXmlReader().read(new SignalGroupsXmlWriter().writeToString(once));
        assertEquals(once.keySet(), twice.keySet());
        for (var e : once.entrySet()) {
            var a = e.getValue(); var b = twice.get(e.getKey());
            assertEquals(a.getGroups().size(), b.getGroups().size());
            var g1 = a.getGroups().values().iterator().next();
            var g2 = b.getGroups().values().iterator().next();
            assertEquals(g1.getSignalRefIds().size(), g2.getSignalRefIds().size());
        }
    }

    @Test
    void controlFixpoint() throws Exception {
        var xml = fixture("control.xml");
        var r1 = new SignalControlXmlReader(); var once = r1.read(xml);
        assertTrue(r1.getUnknownElements().isEmpty());
        var twice = new SignalControlXmlReader().read(new SignalControlXmlWriter().writeToString(once));
        assertEquals(once.keySet(), twice.keySet());
        for (var e : once.entrySet()) {
            var a = e.getValue(); var b = twice.get(e.getKey());
            assertEquals(a.getControllerIdentifier(), b.getControllerIdentifier());
            assertEquals(a.getPlans().size(), b.getPlans().size());
        }
    }

    @Test
    void amberFixpoint() throws Exception {
        var xml = fixture("amber.xml");
        var r1 = new AmberTimesXmlReader(); var once = r1.read(xml);
        assertTrue(r1.getUnknownElements().isEmpty());
        var twice = new AmberTimesXmlReader().read(new AmberTimesXmlWriter().writeToString(once));
        assertEquals(once.getSystems().size(), twice.getSystems().size());
        for (var e : once.getSystems().entrySet()) {
            assertEquals(e.getValue().getSignalOverrides().size(),
                twice.getSystems().get(e.getKey()).getSignalOverrides().size());
        }
    }

    @Test
    void intergreenFixpoint() throws Exception {
        var xml = fixture("intergreen.xml");
        var r1 = new IntergreenTimesXmlReader(); var once = r1.read(xml);
        assertTrue(r1.getUnknownElements().isEmpty());
        var twice = new IntergreenTimesXmlReader().read(new IntergreenTimesXmlWriter().writeToString(once));
        assertEquals(once.getSystems().size(), twice.getSystems().size());
    }
}

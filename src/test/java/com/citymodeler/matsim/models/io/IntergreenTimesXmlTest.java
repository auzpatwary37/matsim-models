package com.citymodeler.matsim.models.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.signals.SignalGroup;
import com.citymodeler.matsim.models.signals.SignalSystem;
import com.citymodeler.matsim.models.signals.timings.IntergreenTimes;
import com.citymodeler.matsim.models.signals.timings.SystemIntergreens;
import org.junit.jupiter.api.Test;

class IntergreenTimesXmlTest {
    @Test
    void groupPairMatrixRoundTrips() {
        String xml = """
                <intergreenTimes>
                    <signalSystem refId="rax">
                        <endingSignalGroup refId="g1">
                            <beginningSignalGroup refId="g2" timeSeconds="3"/>
                            <beginningSignalGroup refId="g3" timeSeconds="5"/>
                        </endingSignalGroup>
                        <endingSignalGroup refId="g2">
                            <beginningSignalGroup refId="g1" timeSeconds="4"/>
                        </endingSignalGroup>
                    </signalSystem>
                </intergreenTimes>
                """;
        IntergreenTimes times = new IntergreenTimesXmlReader().read(xml);
        IntergreenTimes again = new IntergreenTimesXmlReader().read(new IntergreenTimesXmlWriter().writeToString(times));

        SystemIntergreens rax = again.getSystems().get(Id.create("rax", SignalSystem.class));
        assertEquals(3, rax.getEndingToBeginningSec().get(Id.create("g1", SignalGroup.class)).get(Id.create("g2", SignalGroup.class)));
        assertEquals(5, rax.getEndingToBeginningSec().get(Id.create("g1", SignalGroup.class)).get(Id.create("g3", SignalGroup.class)));
        assertEquals(4, rax.getEndingToBeginningSec().get(Id.create("g2", SignalGroup.class)).get(Id.create("g1", SignalGroup.class)));
    }

    @Test
    void unknownChildrenAreRecordedAtEveryLevel() {
        String xml = """
                <intergreenTimes>
                    <signalSystem refId="rax">
                        <endingSignalGroup refId="g1">
                            <beginningSignalGroup refId="g2" timeSeconds="3">
                                <mystery/>
                            </beginningSignalGroup>
                        </endingSignalGroup>
                    </signalSystem>
                </intergreenTimes>
                """;
        IntergreenTimesXmlReader reader = new IntergreenTimesXmlReader();
        reader.read(xml);
        assertEquals(java.util.List.of("intergreenTimes:beginningSignalGroup:mystery"),
            reader.getUnknownElements());
    }

    @Test
    void wrongRootElementIsRejected() {
        String wrongRoot = "<amberTimes><signalSystem refId=\"rax\"/></amberTimes>";
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> new IntergreenTimesXmlReader().read(wrongRoot));
    }
}

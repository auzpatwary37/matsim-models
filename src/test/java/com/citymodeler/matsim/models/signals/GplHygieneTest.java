package com.citymodeler.matsim.models.signals;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class GplHygieneTest {
    @Test
    void stubSchemasStayStubs() throws IOException {
        List<String> stubs = List.of("signalSystems", "signalGroups", "signalControl",
                "amberTimes", "intergreenTimes");
        for (String name : stubs) {
            Path schema = Path.of("src/main/resources/schemas/" + name + ".xsd");
            assertTrue(Files.exists(schema), "missing stub schema " + schema);
            String content = Files.readString(schema);
            assertFalse(content.contains("complexType"),
                name + ".xsd must be an xs:anyType STUB — faithful MATSim schema copies are GPL derivatives");
            assertFalse(content.contains("GPL"), name + ".xsd must not embed the GPL-annotated original");
        }
    }

    @Test
    void noMatsimClassNamesInSignalsSources() throws IOException {
        List<Path> files = new java.util.ArrayList<>();
        try (var paths = Files.walk(Path.of("src/main/java/com/citymodeler/matsim/models/signals"))) {
            files.addAll(paths.filter(f -> f.toString().endsWith(".java")).toList());
        }
        for (String n : List.of("SignalSystemsXmlReader", "SignalSystemsXmlWriter",
                "SignalGroupsXmlReader", "SignalGroupsXmlWriter",
                "SignalControlXmlReader", "SignalControlXmlWriter",
                "AmberTimesXmlReader", "AmberTimesXmlWriter",
                "IntergreenTimesXmlReader", "IntergreenTimesXmlWriter")) {
            files.add(Path.of("src/main/java/com/citymodeler/matsim/models/io/" + n + ".java"));
        }
        files.add(Path.of("src/main/java/com/citymodeler/matsim/models/config/SignalConfigGroup.java"));
        for (Path p : files) {
            if (!Files.exists(p)) continue;
            String content = Files.readString(p);
            assertFalse(content.contains("XMLSignal") || content.contains("jaxb"),
                p + " smells like copied JAXB/GPL code: naming must be our own vocabulary");
        }
    }
}

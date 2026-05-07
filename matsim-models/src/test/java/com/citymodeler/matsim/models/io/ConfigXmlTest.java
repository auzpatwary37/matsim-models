package com.citymodeler.matsim.models.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.citymodeler.matsim.models.config.Config;
import com.citymodeler.matsim.models.config.ConfigGroup;
import com.citymodeler.matsim.models.config.ConfigUtils;

class ConfigXmlTest {

    @TempDir
    Path tempDir;

    @Test
    void roundTrip_config() {
        Config config = ConfigUtils.createConfig();
        config.global().addParam("configVersion", "1.0");
        config.controller().addParam("runsPath", "/tmp/runs");

        ConfigGroup scoring = config.scoring();
        scoring.addParam("learningRate", "1.0");
        Map<String, String> paramSet = Map.of("name", "car", "weight", "1.0");
        scoring.addParamSet("mode", paramSet);

        ConfigGroup subModule = new ConfigGroup("parameterset");
        subModule.addParam("mutationRate", "0.1");
        scoring.addSubModule(subModule);

        ConfigXmlWriter writer = new ConfigXmlWriter();
        Path path = tempDir.resolve("config.xml");
        writer.write(config, path);

        ConfigXmlReader reader = new ConfigXmlReader();
        Config result = reader.read(path);

        assertNotNull(result);
        assertTrue(result.getModule("global").isPresent());
        assertEquals("1.0", result.getModule("global").get().getParam("configVersion").orElse(null));
        assertTrue(result.getModule("controller").isPresent());
        assertEquals("/tmp/runs", result.getModule("controller").get().getParam("runsPath").orElse(null));
        assertTrue(result.getModule("scoring").isPresent());
        assertEquals("1.0", result.getModule("scoring").get().getParam("learningRate").orElse(null));

        ConfigGroup scoringResult = result.getModule("scoring").get();
        assertTrue(scoringResult.getParamSets().containsKey("mode"));
        assertEquals("car", scoringResult.getParamSets().get("mode").get(0).get("name"));
        assertTrue(scoringResult.getSubModules().containsKey("parameterset"));
        assertEquals("0.1", scoringResult.getSubModules().get("parameterset").getParam("mutationRate").orElse(null));
    }

    @Test
    void readFromString() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<config>" +
                "  <module name=\"test\">" +
                "    <param name=\"value\" value=\"42\"/>" +
                "  </module>" +
                "</config>";
        ConfigXmlReader reader = new ConfigXmlReader();
        Config result = reader.readString(xml);
        assertNotNull(result);
        assertTrue(result.getModule("test").isPresent());
        assertEquals("42", result.getModule("test").get().getParam("value").orElse(null));
    }

    @Test
    void standaloneLoadConfig() {
        Config config = ConfigUtils.createConfig();
        config.global().addParam("testKey", "testValue");

        ConfigXmlWriter writer = new ConfigXmlWriter();
        Path path = tempDir.resolve("standalone_config.xml");
        writer.write(config, path);

        Config loaded = ConfigUtils.loadConfig(path);
        assertNotNull(loaded);
        assertEquals("testValue", loaded.getModule("global").get().getParam("testKey").orElse(null));
    }
}
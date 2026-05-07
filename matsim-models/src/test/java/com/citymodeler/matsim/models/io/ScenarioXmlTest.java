package com.citymodeler.matsim.models.io;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.citymodeler.matsim.models.scenario.Scenario;
import com.citymodeler.matsim.models.scenario.ScenarioUtils;

class ScenarioXmlTest {

    @TempDir
    Path tempDir;

    @Test
    void loadScenario_fromInlineConfig() {
        String configXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<config>" +
                "  <module name=\"global\">" +
                "    <param name=\"configVersion\" value=\"1.0\"/>" +
                "  </module>" +
                "  <module name=\"network\">" +
                "    <param name=\"inputNetworkFile\" value=\"network.xml\"/>" +
                "  </module>" +
                "  <module name=\"plans\">" +
                "    <param name=\"inputPlansFile\" value=\"plans.xml\"/>" +
                "  </module>" +
                "</config>";

        String networkXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<network name=\"test\">" +
                "  <nodes>" +
                "    <node id=\"1\" x=\"0.0\" y=\"0.0\"/>" +
                "    <node id=\"2\" x=\"100.0\" y=\"0.0\"/>" +
                "  </nodes>" +
                "  <links>" +
                "    <link id=\"1\" from=\"1\" to=\"2\" length=\"100.0\" capacity=\"3600\" freespeed=\"10.0\" permlanes=\"1\"/>" +
                "  </links>" +
                "</network>";

        String plansXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<population>" +
                "  <person id=\"1\">" +
                "    <plan selected=\"yes\">" +
                "      <activity facility=\"home\" type=\"home\" x=\"0.0\" y=\"0.0\"/>" +
                "      <leg mode=\"car\"/>" +
                "      <activity facility=\"work\" type=\"work\" x=\"100.0\" y=\"0.0\"/>" +
                "    </plan>" +
                "  </person>" +
                "</population>";

        Path configPath = tempDir.resolve("config.xml");
        Path networkPath = tempDir.resolve("network.xml");
        Path plansPath = tempDir.resolve("plans.xml");

        try {
            java.nio.file.Files.writeString(configPath, configXml);
            java.nio.file.Files.writeString(networkPath, networkXml);
            java.nio.file.Files.writeString(plansPath, plansXml);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }

        Scenario scenario = ScenarioUtils.loadScenario(configPath);

        assertNotNull(scenario);
        assertNotNull(scenario.getConfig());
        assertTrue(scenario.getConfig().getModule("network").isPresent());
        assertNotNull(scenario.getNetwork());
        assertNotNull(scenario.getPopulation());
        assertTrue(scenario.getPopulation().size() > 0);
    }

    @Test
    void createAndLoadScenario() {
        com.citymodeler.matsim.models.config.Config config = com.citymodeler.matsim.models.config.ConfigUtils.createConfig();
        config.global().addParam("testParam", "testValue");

        Scenario scenario = ScenarioUtils.createScenario(config);
        assertNotNull(scenario);
        assertNotNull(scenario.getConfig());
    }
}
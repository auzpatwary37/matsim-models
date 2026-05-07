package com.citymodeler.matsim.models.scenario;

import java.nio.file.Path;

import com.citymodeler.matsim.models.config.Config;
import com.citymodeler.matsim.models.config.ConfigUtils;
import com.citymodeler.matsim.models.io.ScenarioXmlReader;

public final class ScenarioUtils {
    private ScenarioUtils() {
    }

    public static Scenario createScenario(Config config) {
        Scenario scenario = new Scenario();
        scenario.setConfig(config);
        return scenario;
    }

    public static Scenario loadScenario(Path configPath) {
        return new ScenarioXmlReader().read(configPath);
    }
}

package com.citymodeler.matsim.models.scenario;

import com.citymodeler.matsim.models.config.Config;

public final class ScenarioUtils {
    private ScenarioUtils() {
    }

    public static Scenario createScenario(Config config) {
        Scenario scenario = new Scenario();
        scenario.setConfig(config);
        return scenario;
    }
}

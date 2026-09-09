package com.citymodeler.matsim.models.scenario;

import java.nio.file.Path;

import com.citymodeler.matsim.models.config.Config;
import com.citymodeler.matsim.models.config.ConfigUtils;
import com.citymodeler.matsim.models.io.NetworkXmlWriter;
import com.citymodeler.matsim.models.io.ScenarioXmlReader;
import com.citymodeler.matsim.models.io.TransitScheduleXmlWriter;
import com.citymodeler.matsim.models.io.VehiclesXmlWriter;

public final class ScenarioUtils {
    public static final String NETWORK_FILE = "network.xml";
    public static final String TRANSIT_SCHEDULE_FILE = "transitSchedule.xml";
    public static final String VEHICLES_FILE = "vehicles.xml";

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

    /** Writes the non-null network, transit schedule and vehicles modules into the given directory. */
    public static void saveScenario(Scenario scenario, Path directory) {
        if (scenario.getNetwork() != null) {
            new NetworkXmlWriter().write(scenario.getNetwork(), directory.resolve(NETWORK_FILE));
        }
        if (scenario.getTransitSchedule() != null) {
            new TransitScheduleXmlWriter().write(scenario.getTransitSchedule(), directory.resolve(TRANSIT_SCHEDULE_FILE));
        }
        if (scenario.getVehicleDefinitions() != null) {
            new VehiclesXmlWriter().write(scenario.getVehicleDefinitions(), directory.resolve(VEHICLES_FILE));
        }
    }
}

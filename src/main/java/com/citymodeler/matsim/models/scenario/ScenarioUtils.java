package com.citymodeler.matsim.models.scenario;

import java.nio.file.Path;
import java.util.Objects;

import com.citymodeler.matsim.models.config.Config;
import com.citymodeler.matsim.models.io.ConfigXmlWriter;
import com.citymodeler.matsim.models.io.NetworkXmlWriter;
import com.citymodeler.matsim.models.io.ScenarioXmlReader;
import com.citymodeler.matsim.models.io.TransitScheduleXmlWriter;
import com.citymodeler.matsim.models.io.VehiclesXmlWriter;

public final class ScenarioUtils {
    public static final String CONFIG_FILE = "config.xml";
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

    /**
     * Saves a reloadable scenario bundle into the given directory: the
     * non-null network, transit schedule and vehicles modules are written to
     * fixed file names, and the scenario's config is written to
     * {@value #CONFIG_FILE} with its module file parameters pointing at the
     * written artifacts. The result reloads through
     * {@link #loadScenario(Path)} with the config file path.
     */
    public static void saveScenario(Scenario scenario, Path directory) {
        Config config = Objects.requireNonNull(scenario.getConfig(),
                "scenario has no config; a reloadable scenario bundle requires a config");
        if (scenario.getNetwork() != null) {
            new NetworkXmlWriter().write(scenario.getNetwork(), directory.resolve(NETWORK_FILE));
            config.network().addParam("inputNetworkFile", NETWORK_FILE);
        }
        if (scenario.getTransitSchedule() != null) {
            new TransitScheduleXmlWriter().write(scenario.getTransitSchedule(), directory.resolve(TRANSIT_SCHEDULE_FILE));
            config.transit().addParam("transitScheduleFile", TRANSIT_SCHEDULE_FILE);
        }
        if (scenario.getVehicleDefinitions() != null) {
            new VehiclesXmlWriter().write(scenario.getVehicleDefinitions(), directory.resolve(VEHICLES_FILE));
            config.vehicles().addParam("inputFile", VEHICLES_FILE);
        }
        new ConfigXmlWriter().write(config, directory.resolve(CONFIG_FILE));
    }
}

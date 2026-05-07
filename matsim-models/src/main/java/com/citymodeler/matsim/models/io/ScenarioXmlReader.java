package com.citymodeler.matsim.models.io;

import java.nio.file.Path;

import com.citymodeler.matsim.models.config.Config;
import com.citymodeler.matsim.models.scenario.Scenario;

public final class ScenarioXmlReader {
    public Scenario read(Path configPath) {
        Config config = new ConfigXmlReader().read(configPath);

        Scenario scenario = new Scenario();
        scenario.setConfig(config);

        config.getModule("network").ifPresent(networkModule -> {
            String inputFile = networkModule.getParam("inputNetworkFile").orElse(null);
            if (inputFile != null) {
                Path networkPath = configPath.getParent().resolve(inputFile);
                scenario.setNetwork(new NetworkXmlReader().read(networkPath));
            }
        });

        config.getModule("plans").ifPresent(plansModule -> {
            String inputFile = plansModule.getParam("inputPlansFile").orElse(null);
            if (inputFile != null) {
                Path plansPath = configPath.getParent().resolve(inputFile);
                for (var person : new PopulationXmlReader().read(plansPath).getPersons().entrySet()) {
                    scenario.addPerson(person.getValue());
                }
            }
        });

        config.getModule("facilities").ifPresent(facilitiesModule -> {
            String inputFile = facilitiesModule.getParam("inputFacilitiesFile").orElse(null);
            if (inputFile != null) {
                Path facilitiesPath = configPath.getParent().resolve(inputFile);
                scenario.setActivityFacilities(new FacilitiesXmlReader().read(facilitiesPath));
            }
        });

        config.getModule("transit").ifPresent(transitModule -> {
            String inputFile = transitModule.getParam("transitScheduleFile").orElse(null);
            if (inputFile != null) {
                Path transitPath = configPath.getParent().resolve(inputFile);
                scenario.setTransitSchedule(new TransitScheduleXmlReader().read(transitPath));
            }
        });

        config.getModule("lanes").ifPresent(lanesModule -> {
            String inputFile = lanesModule.getParam("inputFile").orElse(null);
            if (inputFile != null) {
                Path lanesPath = configPath.getParent().resolve(inputFile);
                scenario.setLanes(new LanesXmlReader().read(lanesPath));
            }
        });

        scenario.postProcess();
        return scenario;
    }
}
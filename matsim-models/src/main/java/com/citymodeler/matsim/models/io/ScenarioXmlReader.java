package com.citymodeler.matsim.models.io;

import java.io.InputStream;
import java.nio.file.Path;

import com.citymodeler.matsim.models.config.Config;
import com.citymodeler.matsim.models.scenario.Scenario;

public final class ScenarioXmlReader {
    private final boolean validateSchema;

    public ScenarioXmlReader() {
        this(false);
    }

    public ScenarioXmlReader(boolean validateSchema) {
        this.validateSchema = validateSchema;
    }

    public Scenario read(Path configPath) {
        Config config = new ConfigXmlReader(validateSchema).read(configPath);
        return read(config, configPath.getParent());
    }

    public Scenario read(InputStream inputStream, Path baseDirectory) {
        Config config = new ConfigXmlReader(validateSchema).read(inputStream);
        return read(config, baseDirectory);
    }

    public Scenario readString(String xml, Path baseDirectory) {
        Config config = new ConfigXmlReader(validateSchema).readString(xml);
        return read(config, baseDirectory);
    }

    private Scenario read(Config config, Path baseDirectory) {
        Scenario scenario = new Scenario();
        scenario.setConfig(config);

        config.getModule("network").ifPresent(networkModule -> {
            String inputFile = networkModule.getParam("inputNetworkFile").orElse(null);
            if (inputFile != null) {
                Path networkPath = baseDirectory.resolve(inputFile);
                scenario.setNetwork(new NetworkXmlReader(validateSchema).read(networkPath));
            }
        });

        config.getModule("plans").ifPresent(plansModule -> {
            String inputFile = plansModule.getParam("inputPlansFile").orElse(null);
            if (inputFile != null) {
                Path plansPath = baseDirectory.resolve(inputFile);
                for (var person : new PopulationXmlReader(validateSchema).read(plansPath).getPersons().entrySet()) {
                    scenario.addPerson(person.getValue());
                }
            }
        });

        config.getModule("facilities").ifPresent(facilitiesModule -> {
            String inputFile = facilitiesModule.getParam("inputFacilitiesFile").orElse(null);
            if (inputFile != null) {
                Path facilitiesPath = baseDirectory.resolve(inputFile);
                scenario.setActivityFacilities(new FacilitiesXmlReader(validateSchema).read(facilitiesPath));
            }
        });

        config.getModule("transit").ifPresent(transitModule -> {
            String inputFile = transitModule.getParam("transitScheduleFile").orElse(null);
            if (inputFile != null) {
                Path transitPath = baseDirectory.resolve(inputFile);
                scenario.setTransitSchedule(new TransitScheduleXmlReader(validateSchema).read(transitPath));
            }
        });

        config.getModule("lanes").ifPresent(lanesModule -> {
            String inputFile = lanesModule.getParam("inputFile").orElse(null);
            if (inputFile != null) {
                Path lanesPath = baseDirectory.resolve(inputFile);
                scenario.setLanes(new LanesXmlReader(validateSchema).read(lanesPath));
            }
        });

        scenario.postProcess();
        return scenario;
    }
}

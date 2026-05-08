package com.citymodeler.matsim.models.config;

import java.nio.file.Path;

public final class ConfigUtils {
    private ConfigUtils() {
    }

    public static Config createConfig() {
        Config config = new Config();
        config.global();
        config.controller();
        config.qsim();
        config.network();
        config.plans();
        config.transit();
        config.facilities();
        config.vehicles();
        config.households();
        config.scoring();
        config.replanning();
        return config;
    }

    public static Config loadConfig(Path path) {
        return new com.citymodeler.matsim.models.io.ConfigXmlReader().read(path);
    }
}

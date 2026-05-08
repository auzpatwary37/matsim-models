package com.citymodeler.matsim.models.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.citymodeler.matsim.models.api.Attributes;

public final class Config {
    private final Map<String, ConfigGroup> modules = new LinkedHashMap<>();
    private final Attributes attributes = new Attributes();

    public Map<String, ConfigGroup> getModules() {
        return modules;
    }

    public void addModule(ConfigGroup module) {
        modules.put(module.getName(), module);
    }

    public ConfigGroup removeModule(String name) {
        return modules.remove(name);
    }

    public Optional<ConfigGroup> getModule(String name) {
        return Optional.ofNullable(modules.get(name));
    }

    public ConfigGroup createModule(String name) {
        return modules.computeIfAbsent(name, ConfigGroup::new);
    }

    public Attributes getAttributes() {
        return attributes;
    }

    public ConfigGroup global() {
        return createModule("global");
    }

    public ConfigGroup controller() {
        return createModule("controller");
    }

    public ConfigGroup qsim() {
        return createModule("qsim");
    }

    public ConfigGroup network() {
        return createModule("network");
    }

    public ConfigGroup plans() {
        return createModule("plans");
    }

    public ConfigGroup transit() {
        return createModule("transit");
    }

    public ConfigGroup facilities() {
        return createModule("facilities");
    }

    public ConfigGroup vehicles() {
        return createModule("vehicles");
    }

    public ConfigGroup households() {
        return createModule("households");
    }

    public ConfigGroup scoring() {
        return createModule("scoring");
    }

    public ConfigGroup replanning() {
        return createModule("replanning");
    }
}

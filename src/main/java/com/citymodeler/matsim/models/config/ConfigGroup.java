package com.citymodeler.matsim.models.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ConfigGroup {
    private final String name;
    private final Map<String, String> params = new LinkedHashMap<>();
    private final Map<String, List<Map<String, String>>> paramSets = new LinkedHashMap<>();
    private final Map<String, ConfigGroup> subModules = new LinkedHashMap<>();

    public ConfigGroup(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    public String getName() {
        return name;
    }

    public void addParam(String name, String value) {
        params.put(name, value);
    }

    public Optional<String> getParam(String name) {
        return Optional.ofNullable(params.get(name));
    }

    public Map<String, String> getParams() {
        return Collections.unmodifiableMap(params);
    }

    public void addParamSet(String name, Map<String, String> params) {
        paramSets.computeIfAbsent(name, ignored -> new ArrayList<>()).add(Collections.unmodifiableMap(new LinkedHashMap<>(params)));
    }

    public Map<String, List<Map<String, String>>> getParamSets() {
        return Collections.unmodifiableMap(paramSets);
    }

    public void addSubModule(ConfigGroup subModule) {
        subModules.put(subModule.getName(), subModule);
    }

    public Map<String, ConfigGroup> getSubModules() {
        return Collections.unmodifiableMap(subModules);
    }
}

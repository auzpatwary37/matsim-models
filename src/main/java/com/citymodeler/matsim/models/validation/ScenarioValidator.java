package com.citymodeler.matsim.models.validation;

import com.citymodeler.matsim.models.facilities.ActivityFacilities;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.population.Population;
import com.citymodeler.matsim.models.scenario.Scenario;
import com.citymodeler.matsim.models.transit.TransitSchedule;

public final class ScenarioValidator {
    private final Scenario scenario;
    private final ValidationReport report;

    public ScenarioValidator(Scenario scenario) {
        this.scenario = scenario;
        this.report = new ValidationReport();
    }

    public static ValidationReport validate(Scenario scenario) {
        ScenarioValidator validator = new ScenarioValidator(scenario);
        validator.validate();
        return validator.report;
    }

    public void validate() {
        if (scenario.getConfig() == null) {
            report.addIssue(new ValidationIssue(
                    ValidationSeverity.ERROR,
                    "scenario",
                    "scenario-no-config",
                    "Scenario has no config",
                    null,
                    "Provide a config for the scenario"));
        }

        if (scenario.getNetwork() != null) {
            NetworkValidator networkValidator = new NetworkValidator(scenario.getNetwork());
            networkValidator.validate();
            report.addIssues(networkValidator.getReport().getIssues());
        }

        if (scenario.getPopulation() != null && !scenario.getPopulation().isEmpty()) {
            Population pop = new Population();
            scenario.getPopulation().values().forEach(pop::addPerson);
            PopulationValidator populationValidator = new PopulationValidator(
                    pop,
                    scenario.getNetwork());
            populationValidator.validate();
            report.addIssues(populationValidator.getReport().getIssues());
        }

        validateCrossDomains();
    }

    private void validateCrossDomains() {
        Network network = scenario.getNetwork();
        ActivityFacilities facilities = scenario.getActivityFacilities();
        TransitSchedule transitSchedule = scenario.getTransitSchedule();

        if (network != null && facilities != null) {
            for (var entry : facilities.getFacilities().entrySet()) {
                String facilityId = entry.getKey().toString();
                var facility = entry.getValue();
                if (facility.getLinkId() != null
                        && !network.getLinks().containsKey(facility.getLinkId())) {
                    report.addIssue(new ValidationIssue(
                            ValidationSeverity.WARNING,
                            "scenario",
                            "facility-link-not-in-network",
                            "Facility " + facilityId + " references link not in network: " + facility.getLinkId(),
                            facilityId,
                            "Ensure facility link exists in network"));
                }
            }
        }

        if (network != null && transitSchedule != null) {
            for (var line : transitSchedule.getTransitLines().values()) {
                for (var route : line.getRoutes().values()) {
                    for (var stop : route.getStops()) {
                        var stopFacility = stop.getStopFacility();
                        if (stopFacility != null && stopFacility.getLinkId() != null
                                && !network.getLinks().containsKey(stopFacility.getLinkId())) {
                            report.addIssue(new ValidationIssue(
                                    ValidationSeverity.WARNING,
                                    "scenario",
                                    "transit-stop-link-not-in-network",
                                    "Transit stop " + stopFacility.getId() + " references link not in network: " + stopFacility.getLinkId(),
                                    stopFacility.getId().toString(),
                                    "Ensure transit stop link exists in network"));
                        }
                    }
                }
            }
        }
    }

    public ValidationReport getReport() {
        return report;
    }
}
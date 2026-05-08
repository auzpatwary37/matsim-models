package com.citymodeler.matsim.models.validation;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.population.Activity;
import com.citymodeler.matsim.models.population.Leg;
import com.citymodeler.matsim.models.population.Person;
import com.citymodeler.matsim.models.population.Plan;
import com.citymodeler.matsim.models.population.Population;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PopulationValidator {
    private final Population population;
    private final Network network;
    private final ValidationReport report;

    public PopulationValidator(Population population, Network network) {
        this.population = population;
        this.network = network;
        this.report = new ValidationReport();
    }

    public static ValidationReport validate(Population population, Network network) {
        PopulationValidator validator = new PopulationValidator(population, network);
        validator.validate();
        return validator.report;
    }

    public void validate() {
        validatePersons();
    }

    private void validatePersons() {
        for (var entry : population.getPersons().entrySet()) {
            String personId = entry.getKey().toString();
            Person person = entry.getValue();

            if (person.getPlans().isEmpty()) {
                report.addIssue(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        "population",
                        "person-no-plans",
                        "Person " + personId + " has no plans",
                        personId,
                        "Add at least one plan to person"));
            }

            if (person.getSelectedPlan() == null && !person.getPlans().isEmpty()) {
                report.addIssue(new ValidationIssue(
                        ValidationSeverity.WARNING,
                        "population",
                        "person-no-selected-plan",
                        "Person " + personId + " has no selected plan; first plan will be used",
                        personId,
                        "Set a selected plan explicitly"));
            }

            Set<Plan> selectedPlans = new HashSet<>();
            for (Plan plan : person.getPlans()) {
                if (plan.isSelected()) {
                    selectedPlans.add(plan);
                }
                validatePlan(plan, personId);
            }

            if (selectedPlans.size() > 1) {
                report.addIssue(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        "population",
                        "person-multiple-selected-plans",
                        "Person " + personId + " has " + selectedPlans.size() + " selected plans",
                        personId,
                        "Ensure only one plan is selected per person"));
            }

            if (!selectedPlans.isEmpty() && person.getSelectedPlan() != null
                    && !selectedPlans.contains(person.getSelectedPlan())) {
                report.addIssue(new ValidationIssue(
                        ValidationSeverity.WARNING,
                        "population",
                        "person-selected-plan-inconsistent",
                        "Person " + personId + " has selected plan that is not marked selected",
                        personId,
                        "Ensure plan.isSelected() matches person.getSelectedPlan()"));
            }
        }
    }

    private void validatePlan(Plan plan, String personId) {
        List<PlanElement> elements = plan.getPlanElements();
        for (int i = 0; i < elements.size(); i++) {
            PlanElement element = elements.get(i);
            if (element instanceof Activity activity) {
                validateActivity(activity, personId, i);
            } else if (element instanceof Leg leg) {
                validateLeg(leg, personId, i);
            }
        }

        for (int i = 0; i < elements.size() - 1; i++) {
            if (elements.get(i) instanceof Leg && elements.get(i + 1) instanceof Leg) {
                report.addIssue(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        "population",
                        "plan-consecutive-legs",
                        "Person " + personId + " plan has consecutive legs at positions " + i + " and " + (i + 1),
                        personId,
                        "Ensure activities separate all legs"));
            }
        }
    }

    private void validateActivity(Activity activity, String personId, int index) {
        String activityId = activity.getFacilityId() != null ? activity.getFacilityId().toString() : null;

        if (activity.hasEndTime() && activity.hasStartTime()
                && activity.getEndTime() < activity.getStartTime()) {
            report.addIssue(new ValidationIssue(
                    ValidationSeverity.ERROR,
                    "population",
                    "activity-end-before-start",
                    "Person " + personId + " activity at index " + index + " has end_time before start_time",
                    activityId,
                    "Ensure end_time >= start_time"));
        }

        if (activity.hasMaximumDuration() && activity.getMaximumDuration() < 0) {
            report.addIssue(new ValidationIssue(
                    ValidationSeverity.ERROR,
                    "population",
                    "activity-negative-max-duration",
                    "Person " + personId + " activity at index " + index + " has negative maximumDuration",
                    activityId,
                    "Set maximumDuration to a non-negative value"));
        }

        if (network != null && activity.getLinkId() != null
                && !network.getLinks().containsKey(activity.getLinkId())) {
            report.addIssue(new ValidationIssue(
                    ValidationSeverity.WARNING,
                    "population",
                    "activity-link-not-in-network",
                    "Person " + personId + " activity at index " + index + " references link not in network: " + activity.getLinkId(),
                    activityId,
                    "Ensure link exists in network"));
        }
    }

    private void validateLeg(Leg leg, String personId, int index) {
        String mode = leg.getMode();
        if (mode == null || mode.isBlank()) {
            report.addIssue(new ValidationIssue(
                    ValidationSeverity.ERROR,
                    "population",
                    "leg-missing-mode",
                    "Person " + personId + " leg at index " + index + " has no mode",
                    personId + "-leg-" + index,
                    "Set leg mode (e.g., car, walk, pt)"));
        }
    }

    public ValidationReport getReport() {
        return report;
    }
}
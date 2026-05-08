package com.citymodeler.matsim.models.validation;

import static org.junit.jupiter.api.Assertions.*;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.population.Activity;
import com.citymodeler.matsim.models.population.Leg;
import com.citymodeler.matsim.models.population.Person;
import com.citymodeler.matsim.models.population.Plan;
import com.citymodeler.matsim.models.population.Population;

import org.junit.jupiter.api.Test;

class PopulationValidatorTest {

    @Test
    void validPopulation_noIssues() {
        Population population = new Population();
        Person person = new Person(Id.create("p1", Person.class));
        Plan plan = new Plan();
        plan.setSelected(true);
        plan.addPlanElement(new Activity(Id.create("f1", com.citymodeler.matsim.models.facilities.ActivityFacility.class), "home", "08:00:00", 0.0, 0.0));
        plan.addPlanElement(new Leg("car"));
        plan.addPlanElement(new Activity(Id.create("f2", com.citymodeler.matsim.models.facilities.ActivityFacility.class), "work", "17:00:00", 0.0, 0.0));
        person.addPlan(plan);
        person.setSelectedPlan(plan);
        population.addPerson(person);

        ValidationReport report = PopulationValidator.validate(population, null);

        assertFalse(report.hasErrors());
        assertFalse(report.hasWarnings());
    }

    @Test
    void personNoPlans_reportsError() {
        Population population = new Population();
        Person person = new Person(Id.create("p1", Person.class));
        population.addPerson(person);

        ValidationReport report = PopulationValidator.validate(population, null);

        assertTrue(report.hasErrors());
        assertEquals("person-no-plans", report.getErrors().get(0).getCode());
    }

    @Test
    void personMultipleSelectedPlans_reportsError() {
        Population population = new Population();
        Person person = new Person(Id.create("p1", Person.class));
        Plan plan1 = new Plan();
        plan1.setSelected(true);
        plan1.addPlanElement(new Activity(Id.create("f1", com.citymodeler.matsim.models.facilities.ActivityFacility.class), "home", null, 0.0, 0.0));
        Plan plan2 = new Plan();
        plan2.setSelected(true);
        plan2.addPlanElement(new Activity(Id.create("f1", com.citymodeler.matsim.models.facilities.ActivityFacility.class), "home", null, 0.0, 0.0));
        person.addPlan(plan1);
        person.addPlan(plan2);
        person.setSelectedPlan(plan1);
        population.addPerson(person);

        ValidationReport report = PopulationValidator.validate(population, null);

        assertTrue(report.hasErrors());
        assertEquals("person-multiple-selected-plans", report.getErrors().get(0).getCode());
    }

    @Test
    void planConsecutiveLegs_reportsError() {
        Population population = new Population();
        Person person = new Person(Id.create("p1", Person.class));
        Plan plan = new Plan();
        plan.setSelected(true);
        plan.addPlanElement(new Activity(Id.create("f1", com.citymodeler.matsim.models.facilities.ActivityFacility.class), "home", null, 0.0, 0.0));
        plan.addPlanElement(new Leg("car"));
        plan.addPlanElement(new Leg("walk"));
        plan.addPlanElement(new Activity(Id.create("f2", com.citymodeler.matsim.models.facilities.ActivityFacility.class), "work", null, 0.0, 0.0));
        person.addPlan(plan);
        person.setSelectedPlan(plan);
        population.addPerson(person);

        ValidationReport report = PopulationValidator.validate(population, null);

        assertTrue(report.hasErrors());
        assertEquals("plan-consecutive-legs", report.getErrors().get(0).getCode());
    }

    @Test
    void legMissingMode_reportsError() {
        Population population = new Population();
        Person person = new Person(Id.create("p1", Person.class));
        Plan plan = new Plan();
        plan.setSelected(true);
        plan.addPlanElement(new Activity(Id.create("f1", com.citymodeler.matsim.models.facilities.ActivityFacility.class), "home", null, 0.0, 0.0));
        Leg leg = new Leg(null);
        plan.addPlanElement(leg);
        plan.addPlanElement(new Activity(Id.create("f2", com.citymodeler.matsim.models.facilities.ActivityFacility.class), "work", null, 0.0, 0.0));
        person.addPlan(plan);
        person.setSelectedPlan(plan);
        population.addPerson(person);

        ValidationReport report = PopulationValidator.validate(population, null);

        assertTrue(report.hasErrors());
        assertEquals("leg-missing-mode", report.getErrors().get(0).getCode());
    }

    @Test
    void activityEndBeforeStart_reportsError() {
        Population population = new Population();
        Person person = new Person(Id.create("p1", Person.class));
        Plan plan = new Plan();
        plan.setSelected(true);
        Activity activity = new Activity(Id.create("f1", com.citymodeler.matsim.models.facilities.ActivityFacility.class), "home", null, 0.0, 0.0);
        activity.setStartTime(9.0 * 3600);
        activity.setEndTime(8.0 * 3600);
        plan.addPlanElement(activity);
        person.addPlan(plan);
        person.setSelectedPlan(plan);
        population.addPerson(person);

        ValidationReport report = PopulationValidator.validate(population, null);

        assertTrue(report.hasErrors());
        assertEquals("activity-end-before-start", report.getErrors().get(0).getCode());
    }

    @Test
    void activityLinkNotInNetwork_reportsWarning() {
        Network network = new Network();
        com.citymodeler.matsim.models.network.Node n1 = new com.citymodeler.matsim.models.network.Node(
                Id.create("n1", com.citymodeler.matsim.models.network.Node.class), new com.citymodeler.matsim.models.api.Coord(0, 0));
        com.citymodeler.matsim.models.network.Node n2 = new com.citymodeler.matsim.models.network.Node(
                Id.create("n2", com.citymodeler.matsim.models.network.Node.class), new com.citymodeler.matsim.models.api.Coord(100, 0));
        network.addNode(n1);
        network.addNode(n2);
        network.addLink(new com.citymodeler.matsim.models.network.Link(
                Id.create("l1", com.citymodeler.matsim.models.network.Link.class),
                Id.create("n1", com.citymodeler.matsim.models.network.Node.class),
                Id.create("n2", com.citymodeler.matsim.models.network.Node.class),
                100, 3600, 13.9, 1));
        network.postProcess();

        Population population = new Population();
        Person person = new Person(Id.create("p1", Person.class));
        Plan plan = new Plan();
        plan.setSelected(true);
        Activity activity = new Activity(Id.create("f1", com.citymodeler.matsim.models.facilities.ActivityFacility.class), "home", null, 0.0, 0.0);
        activity.setLinkId(Id.create("l999", com.citymodeler.matsim.models.network.Link.class));
        plan.addPlanElement(activity);
        person.addPlan(plan);
        person.setSelectedPlan(plan);
        population.addPerson(person);

        ValidationReport report = PopulationValidator.validate(population, network);

        assertTrue(report.hasWarnings());
        assertEquals("activity-link-not-in-network", report.getWarnings().get(0).getCode());
    }
}
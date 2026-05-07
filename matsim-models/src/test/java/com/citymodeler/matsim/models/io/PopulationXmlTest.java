package com.citymodeler.matsim.models.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.InputStream;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.population.Activity;
import com.citymodeler.matsim.models.population.Leg;
import com.citymodeler.matsim.models.population.NetworkRoute;
import com.citymodeler.matsim.models.population.Person;
import com.citymodeler.matsim.models.population.Plan;
import com.citymodeler.matsim.models.population.Population;
import com.citymodeler.matsim.models.population.TransitPassengerRoute;
import com.citymodeler.matsim.models.transit.TransitRoute;

class PopulationXmlTest {

    @TempDir
    Path tempDir;

    @Test
    void roundTrip_carLeg() {
        Population population = new Population();
        Person person = new Person(Id.create("1", Person.class));
        Plan plan = new Plan();
        plan.setSelected(true);
        plan.addPlanElement(new Activity(
                Id.create("home", com.citymodeler.matsim.models.facilities.ActivityFacility.class),
                "home", "08:00:00", 0.0));
        Leg leg = new Leg("car");
        NetworkRoute route = new NetworkRoute();
        route.setStartLinkId(Id.create("1", com.citymodeler.matsim.models.network.Link.class));
        route.setEndLinkId(Id.create("3", com.citymodeler.matsim.models.network.Link.class));
        route.getLinkIds().add(Id.create("2", com.citymodeler.matsim.models.network.Link.class));
        leg.setRoute(route);
        plan.addPlanElement(leg);
        plan.addPlanElement(new Activity(
                Id.create("work", com.citymodeler.matsim.models.facilities.ActivityFacility.class),
                "work", "09:00:00", 0.0));
        person.addPlan(plan);
        person.setSelectedPlan(plan);
        population.addPerson(person);

        PopulationXmlWriter writer = new PopulationXmlWriter();
        Path path = tempDir.resolve("population.xml");
        writer.write(population, path);

        Population result = new PopulationXmlReader().read(path);

        assertNotNull(result);
        assertEquals(1, result.getPersons().size());
        Person roundTripped = result.getPersons().get(Id.create("1", Person.class));
        assertNotNull(roundTripped);
        Plan roundTrippedPlan = roundTripped.getSelectedPlan();
        assertNotNull(roundTrippedPlan);
        assertTrue(roundTrippedPlan.isSelected());
        assertEquals(3, roundTrippedPlan.getPlanElements().size());
        Leg roundTrippedLeg = (Leg) roundTrippedPlan.getPlanElements().get(1);
        assertEquals("car", roundTrippedLeg.getMode());
        assertTrue(roundTrippedLeg.getRoute() instanceof NetworkRoute);
        NetworkRoute roundTrippedRoute = (NetworkRoute) roundTrippedLeg.getRoute();
        assertEquals("1", roundTrippedRoute.getStartLinkId().toString());
        assertEquals("3", roundTrippedRoute.getEndLinkId().toString());
        assertEquals(1, roundTrippedRoute.getLinkIds().size());
        assertEquals("2", roundTrippedRoute.getLinkIds().get(0).toString());
    }

    @Test
    void roundTrip_ptLeg() {
        Population population = new Population();
        Person person = new Person(Id.create("2", Person.class));
        Plan plan = new Plan();
        plan.setSelected(true);
        plan.addPlanElement(new Activity(
                Id.create("stop1", com.citymodeler.matsim.models.facilities.ActivityFacility.class),
                "home", "08:00:00", 0.0));
        Leg leg = new Leg("pt");
        TransitPassengerRoute route = new TransitPassengerRoute();
        route.setAccessStopId(Id.create("stop1", com.citymodeler.matsim.models.transit.TransitStopFacility.class));
        route.setEgressStopId(Id.create("stop2", com.citymodeler.matsim.models.transit.TransitStopFacility.class));
        route.setLineId(Id.create("Blue", com.citymodeler.matsim.models.transit.TransitLine.class));
        route.setRouteId(Id.create("Blue-1", com.citymodeler.matsim.models.transit.TransitRoute.class));
        route.setDepartureId(Id.create("d1", com.citymodeler.matsim.models.transit.Departure.class));
        leg.setRoute(route);
        plan.addPlanElement(leg);
        plan.addPlanElement(new Activity(
                Id.create("stop2", com.citymodeler.matsim.models.facilities.ActivityFacility.class),
                "work", "09:00:00", 0.0));
        person.addPlan(plan);
        person.setSelectedPlan(plan);
        population.addPerson(person);

        PopulationXmlWriter writer = new PopulationXmlWriter();
        Path path = tempDir.resolve("population_pt.xml");
        writer.write(population, path);

        Population result = new PopulationXmlReader().read(path);

        assertNotNull(result);
        assertEquals(1, result.getPersons().size());
        Person roundTripped = result.getPersons().get(Id.create("2", Person.class));
        assertNotNull(roundTripped);
        Plan roundTrippedPlan = roundTripped.getSelectedPlan();
        assertNotNull(roundTrippedPlan);
        Leg roundTrippedLeg = (Leg) roundTrippedPlan.getPlanElements().get(1);
        assertEquals("pt", roundTrippedLeg.getMode());
        assertTrue(roundTrippedLeg.getRoute() instanceof TransitPassengerRoute);
        TransitPassengerRoute roundTrippedRoute = (TransitPassengerRoute) roundTrippedLeg.getRoute();
        assertEquals("stop1", roundTrippedRoute.getAccessStopId().toString());
        assertEquals("stop2", roundTrippedRoute.getEgressStopId().toString());
        assertEquals("Blue", roundTrippedRoute.getLineId().toString());
        assertEquals("Blue-1", roundTrippedRoute.getRouteId().toString());
        assertEquals("d1", roundTrippedRoute.getDepartureId().toString());
    }

    @Test
    void readFromString() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<population>" +
                "  <person id=\"3\">" +
                "    <plan selected=\"yes\">" +
                "      <activity facility=\"fac1\" type=\"home\" x=\"0.0\" y=\"0.0\"/>" +
                "      <leg mode=\"car\"/>" +
                "      <activity facility=\"fac2\" type=\"work\" x=\"100.0\" y=\"100.0\"/>" +
                "    </plan>" +
                "  </person>" +
                "</population>";
        PopulationXmlReader reader = new PopulationXmlReader();
        Population result = reader.readString(xml);
        assertNotNull(result);
        assertEquals(1, result.getPersons().size());
    }

    @Test
    void loadFromClasspathFixture() {
        String fixturePath = "fixtures/population.xml";
        InputStream is = getClass().getClassLoader().getResourceAsStream(fixturePath);
        Population population = new PopulationXmlReader().read(is);

        assertNotNull(population);
        assertEquals(2, population.getPersons().size());

        Person person1 = population.getPersons().get(Id.create("person-1", Person.class));
        assertNotNull(person1);
        Plan plan1 = person1.getSelectedPlan();
        assertNotNull(plan1);
        assertEquals(3, plan1.getPlanElements().size());
    }

    @Test
    void fixture_carNetworkRoute_parsedCorrectly() {
        String fixturePath = "fixtures/population.xml";
        InputStream is = getClass().getClassLoader().getResourceAsStream(fixturePath);
        Population population = new PopulationXmlReader().read(is);

        Person person1 = population.getPersons().get(Id.create("person-1", Person.class));
        Plan plan1 = person1.getSelectedPlan();
        Leg leg = (Leg) plan1.getPlanElements().get(1);
        assertEquals("car", leg.getMode());

        NetworkRoute route = (NetworkRoute) leg.getRoute();
        assertEquals("l1", route.getStartLinkId().toString());
        assertEquals("l2", route.getEndLinkId().toString());
        assertEquals(2, route.getLinkIds().size());
        assertEquals("l1", route.getLinkIds().get(0).toString());
        assertEquals("l2", route.getLinkIds().get(1).toString());
    }

    @Test
    void fixture_ptTransitRoute_parsedCorrectly() {
        String fixturePath = "fixtures/population.xml";
        InputStream is = getClass().getClassLoader().getResourceAsStream(fixturePath);
        Population population = new PopulationXmlReader().read(is);

        Person person2 = population.getPersons().get(Id.create("person-2", Person.class));
        Plan plan2 = person2.getSelectedPlan();
        Leg leg = (Leg) plan2.getPlanElements().get(1);
        assertEquals("pt", leg.getMode());

        TransitPassengerRoute route = (TransitPassengerRoute) leg.getRoute();
        assertEquals("line-1", route.getLineId().toString());
        assertEquals("route-1", route.getRouteId().toString());
        assertEquals("dep-1", route.getDepartureId().toString());
        assertEquals("stop-1", route.getAccessStopId().toString());
        assertEquals("stop-3", route.getEgressStopId().toString());
    }

    @Test
    void fixture_activityEndTime_parsedCorrectly() {
        String fixturePath = "fixtures/population.xml";
        InputStream is = getClass().getClassLoader().getResourceAsStream(fixturePath);
        Population population = new PopulationXmlReader().read(is);

        Person person1 = population.getPersons().get(Id.create("person-1", Person.class));
        Plan plan1 = person1.getSelectedPlan();
        Activity activity = (Activity) plan1.getPlanElements().get(0);
        assertTrue(activity.hasEndTime());
        assertEquals(8.0 * 3600, activity.getEndTime(), 0.01);

        Activity activity2 = (Activity) plan1.getPlanElements().get(2);
        assertTrue(activity2.hasEndTime());
        assertEquals(17.0 * 3600, activity2.getEndTime(), 0.01);
    }

    @Test
    void fixture_activityWithoutEndTime_hasNaNEndTime() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<population>" +
                "  <person id=\"p1\">" +
                "    <plan selected=\"yes\">" +
                "      <activity facility=\"f1\" type=\"home\" x=\"0.0\" y=\"0.0\"/>" +
                "    </plan>" +
                "  </person>" +
                "</population>";
        Population population = new PopulationXmlReader().readString(xml);

        Person person = population.getPersons().get(Id.create("p1", Person.class));
        Activity activity = (Activity) person.getSelectedPlan().getPlanElements().get(0);
        assertFalse(activity.hasEndTime());
        assertTrue(Double.isNaN(activity.getEndTime()));
    }

    @Test
    void writer_skipsNullFacilityId() {
        Population population = new Population();
        Person person = new Person(Id.create("1", Person.class));
        Plan plan = new Plan();
        plan.setSelected(true);
        Activity activity = new Activity("home");
        plan.addPlanElement(activity);
        person.addPlan(plan);
        person.setSelectedPlan(plan);
        population.addPerson(person);

        String xml = new PopulationXmlWriter().writeToString(population);
        assertFalse(xml.contains("facility="));
    }

    @Test
    void writer_usesNestedTransitRouteFormat() {
        Population population = new Population();
        Person person = new Person(Id.create("1", Person.class));
        Plan plan = new Plan();
        plan.setSelected(true);
        plan.addPlanElement(new Activity(
                Id.create("stop1", com.citymodeler.matsim.models.facilities.ActivityFacility.class),
                "home", null, 0.0));
        Leg leg = new Leg("pt");
        TransitPassengerRoute route = new TransitPassengerRoute();
        route.setAccessStopId(Id.create("stop1", com.citymodeler.matsim.models.transit.TransitStopFacility.class));
        route.setEgressStopId(Id.create("stop2", com.citymodeler.matsim.models.transit.TransitStopFacility.class));
        route.setLineId(Id.create("Blue", com.citymodeler.matsim.models.transit.TransitLine.class));
        route.setRouteId(Id.create("Blue-1", com.citymodeler.matsim.models.transit.TransitRoute.class));
        route.setDepartureId(Id.create("d1", com.citymodeler.matsim.models.transit.Departure.class));
        leg.setRoute(route);
        plan.addPlanElement(leg);
        plan.addPlanElement(new Activity(
                Id.create("stop2", com.citymodeler.matsim.models.facilities.ActivityFacility.class),
                "work", null, 0.0));
        person.addPlan(plan);
        person.setSelectedPlan(plan);
        population.addPerson(person);

        String xml = new PopulationXmlWriter().writeToString(population);
        assertTrue(xml.contains("<transitRoute"));
        assertTrue(xml.contains("line=\"Blue\""));
        assertTrue(xml.contains("route=\"Blue-1\""));
        assertTrue(xml.contains("departure=\"d1\""));
    }

    @Test
    void writer_activityTimesNotWrittenWhenUnset() {
        Population population = new Population();
        Person person = new Person(Id.create("1", Person.class));
        Plan plan = new Plan();
        plan.setSelected(true);
        Activity activity = new Activity(
                Id.create("fac1", com.citymodeler.matsim.models.facilities.ActivityFacility.class),
                "home", null, 0.0);
        plan.addPlanElement(activity);
        person.addPlan(plan);
        person.setSelectedPlan(plan);
        population.addPerson(person);

        String xml = new PopulationXmlWriter().writeToString(population);
        assertFalse(xml.contains("start_time"));
        assertFalse(xml.contains("end_time"));
        assertFalse(xml.contains("maximumDuration"));
    }
}
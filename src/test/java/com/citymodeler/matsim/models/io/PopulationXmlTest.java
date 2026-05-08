package com.citymodeler.matsim.models.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.citymodeler.matsim.models.population.UnknownRoute;
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
        route.addLinkId(Id.create("2", com.citymodeler.matsim.models.network.Link.class));
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
    void readActivityFieldsFixture_linkStartTimeMaximumDuration() {
        InputStream is = getClass().getClassLoader().getResourceAsStream("fixtures/population-activity-fields.xml");
        Population population = new PopulationXmlReader().read(is);

        Person p1 = population.getPersons().get(Id.create("person-link-start", Person.class));
        Plan plan = p1.getSelectedPlan();

        Activity home = (Activity) plan.getPlanElements().get(0);
        assertEquals("l1", home.getLinkId().toString());
        assertEquals(7.0 * 3600, home.getStartTime(), 0.01);
        assertEquals(8.5 * 3600, home.getEndTime(), 0.01);
        assertEquals(5400.0, home.getMaximumDuration(), 0.01);
        assertEquals(0.0, home.getCoord().getX(), 0.01);
        assertEquals(0.0, home.getCoord().getY(), 0.01);

        Activity work = (Activity) plan.getPlanElements().get(2);
        assertEquals("l5", work.getLinkId().toString());
        assertEquals(9.0 * 3600, work.getStartTime(), 0.01);
        assertEquals(17.0 * 3600, work.getEndTime(), 0.01);
        assertEquals(28800.0, work.getMaximumDuration(), 0.01);
        assertEquals(1000.0, work.getCoord().getX(), 0.01);
        assertEquals(500.0, work.getCoord().getY(), 0.01);
    }

    @Test
    void readActivityFacilityOnly_noCoords() {
        InputStream is = getClass().getClassLoader().getResourceAsStream("fixtures/population-activity-fields.xml");
        Population population = new PopulationXmlReader().read(is);

        Person p2 = population.getPersons().get(Id.create("person-facility-only", Person.class));
        Activity home = (Activity) p2.getSelectedPlan().getPlanElements().get(0);
        assertNotNull(home.getFacilityId());
        assertEquals("f1", home.getFacilityId().toString());
        assertEquals(9.0 * 3600, home.getEndTime(), 0.01);
        assertNull(home.getCoord());
    }

    @Test
    void readActivityLinkOnly_noFacility() {
        InputStream is = getClass().getClassLoader().getResourceAsStream("fixtures/population-activity-fields.xml");
        Population population = new PopulationXmlReader().read(is);

        Person p3 = population.getPersons().get(Id.create("person-link-only", Person.class));
        Activity home = (Activity) p3.getSelectedPlan().getPlanElements().get(0);
        assertEquals("l1", home.getLinkId().toString());
        assertNull(home.getFacilityId());
        assertFalse(home.hasStartTime());
        assertFalse(home.hasEndTime());
    }

    @Test
    void readActivitySecondsTimeFormat() {
        InputStream is = getClass().getClassLoader().getResourceAsStream("fixtures/population-activity-fields.xml");
        Population population = new PopulationXmlReader().read(is);

        Person p5 = population.getPersons().get(Id.create("person-seconds-time", Person.class));
        Activity home = (Activity) p5.getSelectedPlan().getPlanElements().get(0);
        assertEquals(25200.0, home.getStartTime(), 0.01);
        assertEquals(32400.0, home.getEndTime(), 0.01);

        Activity work = (Activity) p5.getSelectedPlan().getPlanElements().get(2);
        assertEquals(36000.0, work.getStartTime(), 0.01);
        assertEquals(61200.0, work.getEndTime(), 0.01);
    }

    @Test
    void writeAndReadBack_activityLinkStartTimeMaximumDuration() {
        Population population = new Population();
        Person person = new Person(Id.create("test", Person.class));
        Plan plan = new Plan();
        plan.setSelected(true);

        Activity activity = new Activity(
                Id.create("f1", com.citymodeler.matsim.models.facilities.ActivityFacility.class),
                "home", null, 0.0, 0.0);
        activity.setLinkId(Id.createLinkId("l1"));
        activity.setStartTime(7.0 * 3600);
        activity.setEndTime(8.5 * 3600);
        activity.setMaximumDuration(5400);
        plan.addPlanElement(activity);
        person.addPlan(plan);
        person.setSelectedPlan(plan);
        population.addPerson(person);

        Path path = tempDir.resolve("activity-fields.xml");
        new PopulationXmlWriter().write(population, path);
        Population result = new PopulationXmlReader().read(path);

        Activity roundTripped = (Activity) result.getPersons().get(Id.create("test", Person.class))
                .getSelectedPlan().getPlanElements().get(0);
        assertEquals("l1", roundTripped.getLinkId().toString());
        assertEquals(7.0 * 3600, roundTripped.getStartTime(), 0.01);
        assertEquals(8.5 * 3600, roundTripped.getEndTime(), 0.01);
        assertEquals(5400.0, roundTripped.getMaximumDuration(), 0.01);
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

    @Test
    void personGetPlans_returnsUnmodifiableView() {
        Person person = new Person(Id.create("p1", Person.class));
        assertThrows(UnsupportedOperationException.class, () -> person.getPlans().clear());
    }

    @Test
    void populationAddPerson_rejectsNull() {
        Population population = new Population();
        assertThrows(NullPointerException.class, () -> population.addPerson(null));
    }

    @Test
    void personAddPlan_rejectsNull() {
        Person person = new Person(Id.create("p1", Person.class));
        assertThrows(NullPointerException.class, () -> person.addPlan(null));
    }

    @Test
    void networkRouteGetLinkIds_returnsUnmodifiableView() {
        NetworkRoute route = new NetworkRoute();
        route.setStartLinkId(Id.create("1", com.citymodeler.matsim.models.network.Link.class));
        route.setEndLinkId(Id.create("3", com.citymodeler.matsim.models.network.Link.class));
        assertThrows(UnsupportedOperationException.class, () -> route.getLinkIds().clear());
    }

    @Test
    void networkRouteAddLinkId_works() {
        NetworkRoute route = new NetworkRoute();
        route.setStartLinkId(Id.create("1", com.citymodeler.matsim.models.network.Link.class));
        route.setEndLinkId(Id.create("3", com.citymodeler.matsim.models.network.Link.class));
        route.addLinkId(Id.create("2", com.citymodeler.matsim.models.network.Link.class));
        assertEquals(1, route.getLinkIds().size());
        assertEquals("2", route.getLinkIds().get(0).toString());
    }

    @Test
    void activityParseTime_rejectsInvalidFormat() {
        MatsimModelException ex = assertThrows(MatsimModelException.class,
                () -> Activity.parseTime("not-a-time"));
        assertTrue(ex.getMessage().contains("Invalid time format"));
        assertTrue(ex.getMessage().contains("not-a-time"));
    }

    @Test
    void activityParseTime_rejectsNonNumericComponent() {
        MatsimModelException ex = assertThrows(MatsimModelException.class,
                () -> Activity.parseTime("ab:cd:ef"));
        assertTrue(ex.getMessage().contains("Invalid time format"));
    }

    @Test
    void activityParseTime_acceptsNullReturnsNaN() {
        assertTrue(Double.isNaN(Activity.parseTime(null)));
    }

    @Test
    void activityParseTime_acceptsBlankReturnsNaN() {
        assertTrue(Double.isNaN(Activity.parseTime("   ")));
    }

    @Test
    void activityParseTime_acceptsNumericSeconds() {
        assertEquals(3661.0, Activity.parseTime("3661"), 0.01);
    }

    @Test
    void activityParseTime_acceptsHHMMSS() {
        assertEquals(8 * 3600 + 30 * 60 + 15, Activity.parseTime("08:30:15"), 0.01);
    }

    @Test
    void activityParseTime_acceptsHHMM() {
        assertEquals(9 * 3600 + 5 * 60, Activity.parseTime("09:05"), 0.01);
    }

    @Test
    void planScoreNullRoundtrip() {
        Population population = new Population();
        Person person = new Person(Id.create("p1", Person.class));
        Plan plan = new Plan();
        plan.setSelected(true);
        plan.setScore(null);
        plan.addPlanElement(new Activity(
                Id.create("f1", com.citymodeler.matsim.models.facilities.ActivityFacility.class),
                "home", null, 0.0, 0.0));
        person.addPlan(plan);
        person.setSelectedPlan(plan);
        population.addPerson(person);

        String xml = new PopulationXmlWriter().writeToString(population);
        assertFalse(xml.contains("score="));

        Population result = new PopulationXmlReader().readString(xml);
        Person roundTripped = result.getPersons().get(Id.create("p1", Person.class));
        assertNull(roundTripped.getSelectedPlan().getScore());
    }

    @Test
    void fixture_unknownRoute_parsedAndPreserved() {
        InputStream is = getClass().getClassLoader().getResourceAsStream("fixtures/population-activity-fields.xml");
        Population population = new PopulationXmlReader().read(is);

        Person person = population.getPersons().get(Id.create("person-unknown-route", Person.class));
        Leg leg = (Leg) person.getSelectedPlan().getPlanElements().get(1);
        assertEquals("bike", leg.getMode());
        assertTrue(leg.getRoute() instanceof UnknownRoute);
        UnknownRoute route = (UnknownRoute) leg.getRoute();
        assertEquals("GenericRoute", route.getRouteType());
        assertEquals("bike-001", route.getAttributes().get("vehicle"));
        assertEquals("1500", route.getAttributes().get("distance"));
        assertEquals("300", route.getChildren().get("travel_time"));
    }

    @Test
    void unknownRoute_roundTrip() {
        Population population = new Population();
        Person person = new Person(Id.create("p1", Person.class));
        Plan plan = new Plan();
        plan.setSelected(true);
        plan.addPlanElement(new Activity(
                Id.create("f1", com.citymodeler.matsim.models.facilities.ActivityFacility.class),
                "home", null, 0.0, 0.0));
        Leg leg = new Leg("bike");
        UnknownRoute route = new UnknownRoute("GenericRoute");
        route.setAttribute("vehicle", "bike-001");
        route.setAttribute("distance", "1500");
        route.setChild("travel_time", "300");
        leg.setRoute(route);
        plan.addPlanElement(leg);
        plan.addPlanElement(new Activity(
                Id.create("f2", com.citymodeler.matsim.models.facilities.ActivityFacility.class),
                "work", null, 0.0, 0.0));
        person.addPlan(plan);
        person.setSelectedPlan(plan);
        population.addPerson(person);

        String xml = new PopulationXmlWriter().writeToString(population);
        assertTrue(xml.contains("type=\"GenericRoute\""));
        assertTrue(xml.contains("vehicle=\"bike-001\""));
        assertTrue(xml.contains("distance=\"1500\""));
        assertTrue(xml.contains("<travel_time>300</travel_time>"));

        Population result = new PopulationXmlReader().readString(xml);
        Leg roundTrippedLeg = (Leg) result.getPersons().get(Id.create("p1", Person.class))
                .getSelectedPlan().getPlanElements().get(1);
        assertTrue(roundTrippedLeg.getRoute() instanceof UnknownRoute);
        UnknownRoute roundTrippedRoute = (UnknownRoute) roundTrippedLeg.getRoute();
        assertEquals("GenericRoute", roundTrippedRoute.getRouteType());
        assertEquals("bike-001", roundTrippedRoute.getAttributes().get("vehicle"));
        assertEquals("1500", roundTrippedRoute.getAttributes().get("distance"));
        assertEquals("300", roundTrippedRoute.getChildren().get("travel_time"));
    }
}

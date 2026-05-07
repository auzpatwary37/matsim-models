package com.citymodeler.matsim.models.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.config.Config;
import com.citymodeler.matsim.models.config.ConfigGroup;
import com.citymodeler.matsim.models.config.ConfigUtils;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.Node;
import com.citymodeler.matsim.models.population.Activity;
import com.citymodeler.matsim.models.population.Leg;
import com.citymodeler.matsim.models.population.Person;
import com.citymodeler.matsim.models.population.Plan;
import com.citymodeler.matsim.models.population.TransitPassengerRoute;
import com.citymodeler.matsim.models.transit.TransitLine;
import com.citymodeler.matsim.models.transit.TransitRoute;
import com.citymodeler.matsim.models.transit.TransitRouteStop;
import com.citymodeler.matsim.models.transit.TransitSchedule;
import com.citymodeler.matsim.models.transit.TransitStopFacility;

class ScenarioModelTest {
    @Test
    void planElementsPreserveOrderAndPersonPostProcessWiresPlanBackrefAndDefaultSelectedPlan() {
        Person person = new Person(Id.create("person-1", Person.class));
        Plan plan = new Plan();
        Activity home = new Activity("home");
        Leg leg = new Leg("car");
        Activity work = new Activity("work");

        plan.addPlanElement(home);
        plan.addPlanElement(leg);
        plan.addPlanElement(work);
        person.addPlan(plan);
        person.postProcess();

        assertEquals(List.of(home, leg, work), plan.getPlanElements());
        assertSame(person, plan.getPerson());
        assertSame(plan, person.getSelectedPlan());
    }

    @Test
    void transitPassengerRouteReturnsConfiguredDepartureId() {
        TransitPassengerRoute route = new TransitPassengerRoute();
        Id<com.citymodeler.matsim.models.transit.Departure> departureId = Id.create(
                "departure-1",
                com.citymodeler.matsim.models.transit.Departure.class);

        route.setDepartureId(departureId);

        assertEquals(departureId, route.getDepartureId());
    }

    @Test
    void configGroupStoresParamsAndParamSets() {
        ConfigGroup group = new ConfigGroup("qsim");
        group.addParam("flowCapFactor", "1.0");
        group.addParamSet("parameterset", Map.of("mode", "car"));

        assertEquals("qsim", group.getName());
        assertEquals("1.0", group.getParam("flowCapFactor").orElseThrow());
        assertEquals(List.of(Map.of("mode", "car")), group.getParamSets().get("parameterset"));
    }

    @Test
    void configTypedAccessorsCreateStandardModules() {
        Config config = ConfigUtils.createConfig();

        assertSame(config.global(), config.getModule("global").orElseThrow());
        assertSame(config.controller(), config.getModule("controller").orElseThrow());
        assertSame(config.qsim(), config.getModule("qsim").orElseThrow());
        assertSame(config.network(), config.getModule("network").orElseThrow());
        assertSame(config.plans(), config.getModule("plans").orElseThrow());
        assertSame(config.transit(), config.getModule("transit").orElseThrow());
        assertSame(config.facilities(), config.getModule("facilities").orElseThrow());
        assertSame(config.vehicles(), config.getModule("vehicles").orElseThrow());
        assertSame(config.households(), config.getModule("households").orElseThrow());
        assertSame(config.scoring(), config.getModule("scoring").orElseThrow());
        assertSame(config.replanning(), config.getModule("replanning").orElseThrow());
    }

    @Test
    void scenarioPostProcessDelegatesToNetworkTransitAndPerson() {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

        Network network = new Network();
        Id<Node> fromNodeId = Id.create("n1", Node.class);
        Id<Node> toNodeId = Id.create("n2", Node.class);
        Link link = new Link(
                Id.createLinkId("l1"),
                fromNodeId,
                toNodeId,
                100.0,
                1000.0,
                13.9,
                1.0,
                Set.of("car"));
        network.addNode(new Node(fromNodeId, new Coord(0.0, 0.0)));
        network.addNode(new Node(toNodeId, new Coord(1.0, 1.0)));
        network.addLink(link);
        scenario.setNetwork(network);

        TransitSchedule transitSchedule = new TransitSchedule();
        Id<TransitStopFacility> stopId = Id.create("stop-1", TransitStopFacility.class);
        transitSchedule.addStopFacility(new TransitStopFacility(stopId, new Coord(0.0, 0.0), false));
        TransitLine line = new TransitLine(Id.create("line-1", TransitLine.class));
        TransitRoute transitRoute = new TransitRoute(Id.create("route-1", TransitRoute.class));
        TransitRouteStop stop = new TransitRouteStop(stopId, 0.0, 0.0, false);
        transitRoute.addStop(stop);
        line.addRoute(transitRoute);
        transitSchedule.addTransitLine(line);
        scenario.setTransitSchedule(transitSchedule);

        Person person = new Person(Id.create("person-1", Person.class));
        Plan plan = new Plan();
        person.addPlan(plan);
        scenario.addPerson(person);

        scenario.postProcess();

        assertSame(link, network.getNodes().get(fromNodeId).getOutLinks().get(link.getId()));
        assertSame(transitSchedule.getFacilities().get(stopId), stop.getStopFacility());
        assertSame(person, plan.getPerson());
    }
}

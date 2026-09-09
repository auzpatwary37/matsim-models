package com.citymodeler.matsim.models.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.citymodeler.matsim.models.api.Coord;
import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.config.Config;
import com.citymodeler.matsim.models.config.ConfigGroup;
import com.citymodeler.matsim.models.config.ConfigUtils;
import com.citymodeler.matsim.models.io.NetworkXmlReader;
import com.citymodeler.matsim.models.io.ScenarioXmlReader;
import com.citymodeler.matsim.models.io.TransitScheduleXmlReader;
import com.citymodeler.matsim.models.io.VehiclesXmlReader;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.network.Node;
import com.citymodeler.matsim.models.population.Activity;
import com.citymodeler.matsim.models.population.Leg;
import com.citymodeler.matsim.models.population.Person;
import com.citymodeler.matsim.models.population.Plan;
import com.citymodeler.matsim.models.population.TransitPassengerRoute;
import com.citymodeler.matsim.models.transit.Departure;
import com.citymodeler.matsim.models.transit.TransitLine;
import com.citymodeler.matsim.models.transit.TransitRoute;
import com.citymodeler.matsim.models.transit.TransitRouteStop;
import com.citymodeler.matsim.models.transit.TransitSchedule;
import com.citymodeler.matsim.models.transit.TransitStopFacility;
import com.citymodeler.matsim.models.vehicles.Vehicle;
import com.citymodeler.matsim.models.vehicles.VehicleDefinitions;
import com.citymodeler.matsim.models.vehicles.VehicleType;
import com.citymodeler.matsim.models.validation.ScenarioValidator;
import com.citymodeler.matsim.models.validation.ValidationReport;

class ScenarioModelTest {

    @TempDir
    Path tempDir;
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

    @Test
    void scenarioGetPopulation_returnsUnmodifiableView() {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        assertThrows(UnsupportedOperationException.class, () -> scenario.getPopulation().clear());
    }

    @Test
    void planGetPlanElements_returnsUnmodifiableView() {
        Plan plan = new Plan();
        plan.addPlanElement(new Activity("home"));
        assertThrows(UnsupportedOperationException.class, () -> plan.getPlanElements().clear());
    }

    @Test
    void scenarioStoresVehicleDefinitions() {
        Scenario scenario = new Scenario();
        assertNull(scenario.getVehicleDefinitions());
        VehicleDefinitions definitions = new VehicleDefinitions();
        scenario.setVehicleDefinitions(definitions);
        assertSame(definitions, scenario.getVehicleDefinitions());
    }

    @Test
    void scenarioXmlReaderLoadsVehiclesModule() throws Exception {
        Files.writeString(tempDir.resolve("vehicles.xml"), """
                <vehicleDefinitions xmlns="http://www.matsim.org/files/dtd"
                                    xsi:schemaLocation="http://www.matsim.org/files/dtd http://www.matsim.org/files/dtd/vehicleDefinitions_v2.0.xsd">
                    <vehicleType id="bus">
                        <attributes>
                            <attribute name="accessTimeInSecondsPerPerson" class="java.lang.Double">1.5</attribute>
                            <attribute name="egressTimeInSecondsPerPerson" class="java.lang.Double">0.75</attribute>
                        </attributes>
                        <capacity seats="40" standingRoomInPersons="60"/>
                    </vehicleType>
                    <vehicle id="v1" type="bus"/>
                </vehicleDefinitions>
                """);
        String configXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<config>" +
                "  <module name=\"vehicles\">" +
                "    <param name=\"inputFile\" value=\"vehicles.xml\"/>" +
                "  </module>" +
                "</config>";

        Scenario scenario = new ScenarioXmlReader().readString(configXml, tempDir);

        assertNotNull(scenario.getVehicleDefinitions());
        VehicleType bus = scenario.getVehicleDefinitions().getVehicleTypes().get(Id.create("bus", VehicleType.class));
        assertEquals(40, bus.getSeatingCapacity());
        assertEquals(60, bus.getStandingCapacity());
        assertEquals(1.5, bus.getAccessTimeSeconds());
        assertEquals(0.75, bus.getEgressTimeSeconds());
        assertEquals("bus", scenario.getVehicleDefinitions().getVehicles().get(Id.create("v1", Vehicle.class)).getType());
    }

    @Test
    void saveScenarioWritesNetworkTransitAndVehiclesFiles() throws Exception {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        Network network = new Network();
        Id<Node> fromNodeId = Id.create("n1", Node.class);
        Id<Node> toNodeId = Id.create("n2", Node.class);
        network.addNode(new Node(fromNodeId, new Coord(0.0, 0.0)));
        network.addNode(new Node(toNodeId, new Coord(100.0, 0.0)));
        network.addLink(new Link(Id.createLinkId("l1"), fromNodeId, toNodeId, 100.0, 1000.0, 13.9, 1.0, Set.of("car")));
        scenario.setNetwork(network);

        TransitSchedule transitSchedule = new TransitSchedule();
        Id<TransitStopFacility> stopId = Id.create("stop-1", TransitStopFacility.class);
        transitSchedule.addStopFacility(new TransitStopFacility(stopId, new Coord(0.0, 0.0), false));
        scenario.setTransitSchedule(transitSchedule);

        VehicleDefinitions definitions = new VehicleDefinitions();
        definitions.addVehicle(new Vehicle(Id.create("v1", Vehicle.class), "car"));
        scenario.setVehicleDefinitions(definitions);

        ScenarioUtils.saveScenario(scenario, tempDir);

        assertEquals(1, new NetworkXmlReader().read(tempDir.resolve("network.xml")).getLinks().size());
        assertEquals(1, new TransitScheduleXmlReader().read(tempDir.resolve("transitSchedule.xml")).getFacilities().size());
        assertEquals(1, new VehiclesXmlReader().read(tempDir.resolve("vehicles.xml")).getVehicles().size());
    }

    @Test
    void saveScenarioSkipsNullModules() throws Exception {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        VehicleDefinitions definitions = new VehicleDefinitions();
        definitions.addVehicle(new Vehicle(Id.create("v1", Vehicle.class), "car"));
        scenario.setVehicleDefinitions(definitions);

        ScenarioUtils.saveScenario(scenario, tempDir);

        assertFalse(Files.exists(tempDir.resolve("network.xml")));
        assertFalse(Files.exists(tempDir.resolve("transitSchedule.xml")));
        assertTrue(Files.exists(tempDir.resolve("vehicles.xml")));
    }

    @Test
    void validatorFlagsMissingVehicleReferences() {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        VehicleDefinitions definitions = new VehicleDefinitions();
        definitions.addVehicle(new Vehicle(Id.create("v1", Vehicle.class), "missing-type"));
        scenario.setVehicleDefinitions(definitions);

        TransitSchedule transitSchedule = new TransitSchedule();
        TransitLine line = new TransitLine(Id.create("line-1", TransitLine.class));
        TransitRoute route = new TransitRoute(Id.create("route-1", TransitRoute.class));
        Departure departure = new Departure(Id.create("dep-1", Departure.class), 0.0);
        departure.setVehicleId("ghost-vehicle");
        route.addDeparture(departure);
        line.addRoute(route);
        transitSchedule.addTransitLine(line);
        scenario.setTransitSchedule(transitSchedule);

        ValidationReport report = ScenarioValidator.validate(scenario);

        assertTrue(report.getIssues().stream().anyMatch(issue -> "vehicle-type-missing".equals(issue.getCode())),
                String.valueOf(report.getIssues()));
        assertTrue(report.getIssues().stream().anyMatch(issue -> "departure-vehicle-missing".equals(issue.getCode())),
                String.valueOf(report.getIssues()));
    }

    @Test
    void validatorFlagsDepartureVehicleReferencesWhenVehicleModuleMissing() {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

        TransitSchedule transitSchedule = new TransitSchedule();
        TransitLine line = new TransitLine(Id.create("line-1", TransitLine.class));
        TransitRoute route = new TransitRoute(Id.create("route-1", TransitRoute.class));
        Departure departure = new Departure(Id.create("dep-1", Departure.class), 0.0);
        departure.setVehicleId("ghost-vehicle");
        route.addDeparture(departure);
        line.addRoute(route);
        transitSchedule.addTransitLine(line);
        scenario.setTransitSchedule(transitSchedule);

        ValidationReport report = ScenarioValidator.validate(scenario);

        assertTrue(report.getIssues().stream().anyMatch(issue -> "departure-vehicle-missing".equals(issue.getCode())),
                String.valueOf(report.getIssues()));
    }

    @Test
    void validatorAcceptsResolvableVehicleReferences() {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        VehicleDefinitions definitions = new VehicleDefinitions();
        definitions.addVehicleType(new VehicleType(Id.create("bus", VehicleType.class)));
        definitions.addVehicle(new Vehicle(Id.create("v1", Vehicle.class), "bus"));
        scenario.setVehicleDefinitions(definitions);

        TransitSchedule transitSchedule = new TransitSchedule();
        TransitLine line = new TransitLine(Id.create("line-1", TransitLine.class));
        TransitRoute route = new TransitRoute(Id.create("route-1", TransitRoute.class));
        Departure departure = new Departure(Id.create("dep-1", Departure.class), 0.0);
        departure.setVehicleId("v1");
        route.addDeparture(departure);
        line.addRoute(route);
        transitSchedule.addTransitLine(line);
        scenario.setTransitSchedule(transitSchedule);

        ValidationReport report = ScenarioValidator.validate(scenario);

        assertFalse(report.getIssues().stream().anyMatch(issue -> "vehicle-type-missing".equals(issue.getCode())),
                String.valueOf(report.getIssues()));
        assertFalse(report.getIssues().stream().anyMatch(issue -> "departure-vehicle-missing".equals(issue.getCode())),
                String.valueOf(report.getIssues()));
    }
}

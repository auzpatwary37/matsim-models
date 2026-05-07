package com.citymodeler.matsim.models.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.population.Activity;
import com.citymodeler.matsim.models.population.Leg;
import com.citymodeler.matsim.models.population.NetworkRoute;
import com.citymodeler.matsim.models.population.Person;
import com.citymodeler.matsim.models.population.Plan;
import com.citymodeler.matsim.models.population.TransitPassengerRoute;

class PopulationStaxReaderTest {

    @TempDir
    Path tempDir;

    @Test
    void callback_emitsBothPersonsWithRoutes() {
        Path path = tempDir.resolve("population.xml");
        copyFixtureToTemp(path);

        List<Person> emittedPersons = new ArrayList<>();
        PopulationStaxReader reader = new PopulationStaxReader();
        reader.read(path, emittedPersons::add);

        assertEquals(2, emittedPersons.size());

        Person person1 = emittedPersons.stream()
                .filter(p -> p.getId().toString().equals("person-1"))
                .findFirst()
                .orElseThrow();
        assertNotNull(person1);
        Plan plan1 = person1.getSelectedPlan();
        assertNotNull(plan1);
        assertTrue(plan1.getPlanElements().size() >= 3);

        Person person2 = emittedPersons.stream()
                .filter(p -> p.getId().toString().equals("person-2"))
                .findFirst()
                .orElseThrow();
        assertNotNull(person2);
        Plan plan2 = person2.getSelectedPlan();
        assertNotNull(plan2);

        Leg leg1 = (Leg) plan1.getPlanElements().get(1);
        assertEquals("car", leg1.getMode());
        assertTrue(leg1.getRoute() instanceof NetworkRoute);
        NetworkRoute route1 = (NetworkRoute) leg1.getRoute();
        assertEquals("l1", route1.getStartLinkId().toString());
        assertEquals("l2", route1.getEndLinkId().toString());
        assertEquals(2, route1.getLinkIds().size());

        Leg leg2 = (Leg) plan2.getPlanElements().get(1);
        assertEquals("pt", leg2.getMode());
        assertTrue(leg2.getRoute() instanceof TransitPassengerRoute);
        TransitPassengerRoute route2 = (TransitPassengerRoute) leg2.getRoute();
        assertEquals("line-1", route2.getLineId().toString());
        assertEquals("route-1", route2.getRouteId().toString());
    }

    @Test
    void stream_emitsBothPersonsWithRoutes() throws IOException {
        Path path = tempDir.resolve("population.xml");
        copyFixtureToTemp(path);

        PopulationStaxReader reader = new PopulationStaxReader();
        List<Person> persons = new ArrayList<>();
        try (var stream = reader.stream(path)) {
            stream.forEach(persons::add);
        }

        assertEquals(2, persons.size());

        Person person1 = persons.get(0);
        assertNotNull(person1);
        Plan plan1 = person1.getSelectedPlan();
        assertNotNull(plan1);
        assertTrue(plan1.getPlanElements().size() >= 3);

        Leg leg1 = (Leg) plan1.getPlanElements().get(1);
        assertEquals("car", leg1.getMode());
        assertTrue(leg1.getRoute() instanceof NetworkRoute);

        Person person2 = persons.get(1);
        assertNotNull(person2);
        Plan plan2 = person2.getSelectedPlan();
        assertNotNull(plan2);

        Leg leg2 = (Leg) plan2.getPlanElements().get(1);
        assertEquals("pt", leg2.getMode());
        assertTrue(leg2.getRoute() instanceof TransitPassengerRoute);
    }

    private void copyFixtureToTemp(Path target) {
        try (var input = getClass().getClassLoader().getResourceAsStream("fixtures/population.xml")) {
            Files.copy(input, target);
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy fixture", e);
        }
    }
}
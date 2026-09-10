package com.citymodeler.matsim.models.gtfs;

import static org.junit.jupiter.api.Assertions.*;

import com.citymodeler.matsim.models.gtfs.GtfsDepartureBuilder.DepartureProfile;
import com.citymodeler.matsim.models.gtfs.GtfsDepartureBuilder.StopOffsets;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

class GtfsDepartureBuilderTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 10);

    @Test
    void singleScheduledDepartureWithAnchoredOffsets() {
        GtfsTrip trip = new GtfsTrip("T1", "R1", "SVC", null, null, 0, true, null, null, false, false);
        List<GtfsStopTime> stops = List.of(
                new GtfsStopTime("T1", "S1", 1, 28800, 28800, true, 0, 0),
                new GtfsStopTime("T1", "S2", 2, 28830, 28845, true, 0, 0),
                new GtfsStopTime("T1", "S3", 3, 28920, 28920, true, 0, 0));

        List<DepartureProfile> profiles = GtfsDepartureBuilder.build(trip, stops, List.of(), DATE);
        assertEquals(1, profiles.size());
        assertEquals(28800, profiles.get(0).departureTime());
        List<StopOffsets> offsets = profiles.get(0).stopOffsets();
        assertEquals(3, offsets.size());
        assertEquals(0, offsets.get(0).departureOffset());
        assertEquals(45, offsets.get(1).departureOffset());
        assertEquals(120, offsets.get(2).departureOffset());
    }

    @Test
    void frequencyDoesNotDoubleEmit() {
        GtfsTrip trip = new GtfsTrip("T1", "R1", "SVC", null, null, 0, true, null, null, false, false);
        List<GtfsStopTime> stops = List.of(
                new GtfsStopTime("T1", "S1", 1, 28800, 28800, true, 0, 0),
                new GtfsStopTime("T1", "S2", 2, 28860, 28860, true, 0, 0));
        List<GtfsFrequencyRow> freqs = List.of(
                new GtfsFrequencyRow("T1", 28800, 28980, 60, 1));

        List<DepartureProfile> profiles = GtfsDepartureBuilder.build(trip, stops, freqs, DATE);
        // 3 departures: 28800, 28860, 28920 (half-open [28800, 28980) step 60)
        assertEquals(3, profiles.size());
        assertEquals(28800, profiles.get(0).departureTime());
        assertEquals(28860, profiles.get(1).departureTime());
        assertEquals(28920, profiles.get(2).departureTime());
        // No extra "scheduled" departure beyond the frequency ones
        assertTrue(profiles.get(0).stopOffsets() != null);
    }

    @Test
    void frequencyHalfOpenInterval() {
        GtfsTrip trip = new GtfsTrip("T1", "R1", "SVC", null, null, 0, true, null, null, false, false);
        // No stop times — frequency only
        List<GtfsFrequencyRow> freqs = List.of(
                new GtfsFrequencyRow("T1", 3600, 3660, 30, 1));

        List<DepartureProfile> profiles = GtfsDepartureBuilder.build(trip, null, freqs, DATE);
        // [3600, 3660) step 30: 3600, 3630 → 2 departures (3660 excluded)
        assertEquals(2, profiles.size());
        assertEquals(3600, profiles.get(0).departureTime());
        assertEquals(3630, profiles.get(1).departureTime());
    }

    @Test
    void timepointSemantics_oneIsExact() {
        GtfsStopTime exact = new GtfsStopTime("T1", "S1", 1, 28800, 28800, true, 0, 0);
        GtfsStopTime approx = new GtfsStopTime("T1", "S2", 2, null, null, false, 0, 0);
        assertTrue(exact.isTimepoint());
        assertFalse(approx.isTimepoint());
    }

    @Test
    void interpolationAcrossMultipleNonTimepoints() {
        GtfsTrip trip = new GtfsTrip("T1", "R1", "SVC", null, null, 0, true, null, null, false, false);
        // Anchor at 0, three approximates, anchor at 120
        List<GtfsStopTime> stops = List.of(
                new GtfsStopTime("T1", "S1", 1, 28800, 28800, true, 0, 0),
                new GtfsStopTime("T1", "S2", 2, null, null, false, 0, 0),
                new GtfsStopTime("T1", "S3", 3, null, null, false, 0, 0),
                new GtfsStopTime("T1", "S4", 4, null, null, false, 0, 0),
                new GtfsStopTime("T1", "S5", 5, 28920, 28920, true, 0, 0));

        List<DepartureProfile> profiles = GtfsDepartureBuilder.build(trip, stops, List.of(), DATE);
        List<StopOffsets> offsets = profiles.get(0).stopOffsets();
        // Linear interpolation: 30, 60, 90 between anchors at 0 and 120
        assertEquals(0, offsets.get(0).departureOffset(), 0.1);
        assertEquals(30, offsets.get(1).departureOffset(), 0.1);
        assertEquals(60, offsets.get(2).departureOffset(), 0.1);
        assertEquals(90, offsets.get(3).departureOffset(), 0.1);
        assertEquals(120, offsets.get(4).departureOffset(), 0.1);
    }

    @Test
    void over24HourTimes() {
        GtfsTrip trip = new GtfsTrip("T1", "R1", "SVC", null, null, 0, true, null, null, false, false);
        List<GtfsStopTime> stops = List.of(
                new GtfsStopTime("T1", "S1", 1, 90000, 90000, true, 0, 0),
                new GtfsStopTime("T1", "S2", 2, 90300, 90300, true, 0, 0));

        List<DepartureProfile> profiles = GtfsDepartureBuilder.build(trip, stops, List.of(), DATE);
        assertEquals(90000, profiles.get(0).departureTime());
        assertEquals(300, profiles.get(0).stopOffsets().get(1).departureOffset());
    }

    @Test
    void monotonicityViolationIsFatal() {
        GtfsTrip trip = new GtfsTrip("T1", "R1", "SVC", null, null, 0, true, null, null, false, false);
        // Arrival after departure at stop 2
        List<GtfsStopTime> stops = List.of(
                new GtfsStopTime("T1", "S1", 1, 28800, 28800, true, 0, 0),
                new GtfsStopTime("T1", "S2", 2, 28900, 28850, true, 0, 0));

        assertThrows(IllegalStateException.class, () ->
                GtfsDepartureBuilder.build(trip, stops, List.of(), DATE));
    }
}

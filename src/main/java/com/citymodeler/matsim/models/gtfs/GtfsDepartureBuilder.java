package com.citymodeler.matsim.models.gtfs;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Constructs departure profiles (stop offsets) from GTFS stop_times.
 *
 * <p>Semantics:
 * <ul>
 *   <li>If a trip has frequency rows, stop_times serve as a template; only frequency
 *       departures are emitted (no double-emission of the scheduled departure).</li>
 *   <li>Non-timepoint stops are linearly interpolated between the nearest anchored
 *       timepoints by position within the run of approximates.</li>
 *   <li>{@code exact_times=0} means approximate (flexible), {@code 1} means exact.</li>
 * </ul>
 */
public final class GtfsDepartureBuilder {

    public record StopOffsets(double arrivalOffset, double departureOffset, boolean awaitDeparture) {
    }

    public record DepartureProfile(
            String departureId,
            double departureTime,
            List<StopOffsets> stopOffsets) {
    }

    /**
     * Build departure profiles for a single trip on a given date.
     */
    public static List<DepartureProfile> build(GtfsTrip trip, List<GtfsStopTime> stopTimes,
                                                 List<GtfsFrequencyRow> frequencyRows,
                                                 LocalDate date) {
        List<DepartureProfile> profiles = new ArrayList<>();
        String dateStr = date.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);

        boolean hasFrequency = frequencyRows != null && frequencyRows.stream()
                .anyMatch(f -> trip.id().equals(f.tripId()) && f.headwaySecs() > 0);

        if (stopTimes != null && !stopTimes.isEmpty()) {
            if (!hasFrequency) {
                // Pure schedule trip: emit one departure from first stop_time
                GtfsStopTime first = stopTimes.get(0);
                Integer firstDep = first.departureTime();
                if (firstDep != null) {
                    double base = firstDep;
                    List<StopOffsets> offsets = interpolateOffsets(stopTimes, base);
                    String depId = trip.id() + ":" + dateStr;
                    profiles.add(new DepartureProfile(depId, base, offsets));
                }
            } else {
                // Frequency trip: stop_times are a template; emit frequency departures
                // Use the stop_times as the offset template for each frequency departure
                GtfsStopTime first = stopTimes.get(0);
                if (first.departureTime() != null) {
                    double templateBase = first.departureTime();
                    List<StopOffsets> template = interpolateOffsets(stopTimes, templateBase);
                    for (GtfsFrequencyRow freq : frequencyRows) {
                        if (!trip.id().equals(freq.tripId()) || freq.headwaySecs() <= 0) continue;
                        for (int t = freq.startTime(); t < freq.endTime(); t += freq.headwaySecs()) {
                            String depId = trip.id() + ":" + dateStr + ":" + formatTime(t);
                            profiles.add(new DepartureProfile(depId, t, template));
                        }
                    }
                }
            }
        }

        // Frequency-only trips (no stop_times): emit departures with null offsets
        if (hasFrequency && (stopTimes == null || stopTimes.isEmpty())) {
            for (GtfsFrequencyRow freq : frequencyRows) {
                if (!trip.id().equals(freq.tripId()) || freq.headwaySecs() <= 0) continue;
                for (int t = freq.startTime(); t < freq.endTime(); t += freq.headwaySecs()) {
                    String depId = trip.id() + ":" + dateStr + ":" + formatTime(t);
                    profiles.add(new DepartureProfile(depId, t, null));
                }
            }
        }

        return profiles;
    }

    /**
     * Interpolate offsets for all stops. Non-timepoint stops are linearly interpolated
     * between the nearest anchored timepoints by their position within the approximate run.
     */
    static List<StopOffsets> interpolateOffsets(List<GtfsStopTime> stopTimes, double base) {
        int n = stopTimes.size();
        double[] arrivals = new double[n];
        double[] departures = new double[n];

        for (int i = 0; i < n; i++) {
            GtfsStopTime st = stopTimes.get(i);
            if (st.isTimepoint()) {
                arrivals[i] = st.arrivalTime() != null ? st.arrivalTime() : (i == 0 ? base : arrivals[i - 1]);
                departures[i] = st.departureTime() != null ? st.departureTime() : arrivals[i];
            }
        }

        // Fill non-timepoints by linear interpolation between nearest anchors
        int i = 0;
        while (i < n) {
            if (stopTimes.get(i).isTimepoint()) {
                i++;
                continue;
            }
            // Found start of approximate run at i
            int runStart = i;
            while (i < n && !stopTimes.get(i).isTimepoint()) i++;
            int runEnd = i; // exclusive; anchor at runEnd (or past end)

            double prevDep = runStart > 0 ? departures[runStart - 1] : base;
            double prevArr = runStart > 0 ? arrivals[runStart - 1] : base;
            double nextArr;
            double nextDep;
            if (runEnd < n) {
                nextArr = arrivals[runEnd];
                nextDep = departures[runEnd];
            } else {
                // Past end: extrapolate from last anchor
                nextArr = prevArr + 60;
                nextDep = prevDep + 60;
            }

            int runLen = runEnd - runStart;
            for (int k = 0; k < runLen; k++) {
                double frac = (k + 1.0) / (runLen + 1.0);
                int idx = runStart + k;
                arrivals[idx] = prevArr + frac * (nextArr - prevArr);
                departures[idx] = prevDep + frac * (nextDep - prevDep);
            }
        }

        // Build result
        List<StopOffsets> offsets = new ArrayList<>();
        for (int j = 0; j < n; j++) {
            double arrOff = arrivals[j] - base;
            double depOff = departures[j] - base;
            if (arrOff < -0.001 || depOff < -0.001) {
                throw new IllegalStateException(
                        "Negative offset at stop index " + j + " (arr=" + arrOff + ", dep=" + depOff + ")");
            }
            if (depOff < arrOff - 0.001) {
                throw new IllegalStateException(
                        "Departure before arrival at stop index " + j);
            }
            boolean await = Math.abs(departures[j] - arrivals[j]) > 0.5;
            offsets.add(new StopOffsets(arrOff, depOff, await));
        }
        return offsets;
    }

    static String formatTime(int secs) {
        int h = secs / 3600;
        int m = (secs % 3600) / 60;
        int s = secs % 60;
        return String.format("%02d%02d%02d", h, m, s);
    }
}

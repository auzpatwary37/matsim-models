package com.citymodeler.matsim.models.gtfs;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Constructs departure profiles (stop offsets) from GTFS stop_times.
 */
public final class GtfsDepartureBuilder {

    public record StopOffsets(double arrivalOffset, double departureOffset, boolean awaitDeparture) {
    }

    public record DepartureProfile(
            String departureId,
            double departureTime,
            List<StopOffsets> stopOffsets) {
    }

    public static List<DepartureProfile> build(GtfsTrip trip, List<GtfsStopTime> stopTimes,
                                                 List<GtfsFrequencyRow> frequencyRows,
                                                 LocalDate date) {
        List<DepartureProfile> profiles = new ArrayList<>();
        String dateStr = date.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);

        if (stopTimes != null && !stopTimes.isEmpty()) {
            GtfsStopTime first = stopTimes.get(0);
            Integer firstDep = first.departureTime();
            if (firstDep != null) {
                double base = firstDep;
                List<StopOffsets> offsets = new ArrayList<>();
                for (int i = 0; i < stopTimes.size(); i++) {
                    GtfsStopTime st = stopTimes.get(i);
                    double arr, dep;
                    if (i == 0) {
                        arr = st.arrivalTime() != null ? st.arrivalTime() : base;
                        dep = st.departureTime() != null ? st.departureTime() : base;
                    } else if (!st.isTimepoint()) {
                        double prevDep = prevDeparture(stopTimes, i - 1, base);
                        double nextBase = nextTimepointDep(stopTimes, i, base);
                        arr = (prevDep + nextBase) / 2.0;
                        dep = arr;
                    } else {
                        arr = st.arrivalTime() != null ? st.arrivalTime() : base;
                        dep = st.departureTime() != null ? st.departureTime() : arr;
                    }
                    double arrOff = arr - base;
                    double depOff = dep - base;
                    if (arrOff < -0.001 || depOff < -0.001) {
                        throw new IllegalStateException(
                                "Negative offset in trip " + trip.id() + " at stop " + st.stopId());
                    }
                    if (depOff < arrOff - 0.001) {
                        throw new IllegalStateException(
                                "Departure before arrival in trip " + trip.id() + " at stop " + st.stopId());
                    }
                    boolean await = Math.abs(dep - arr) > 0.5;
                    offsets.add(new StopOffsets(arrOff, depOff, await));
                }
                String depId = trip.id() + ":" + dateStr;
                profiles.add(new DepartureProfile(depId, base, offsets));
            }
        }

        if (frequencyRows != null) {
            for (GtfsFrequencyRow freq : frequencyRows) {
                if (!trip.id().equals(freq.tripId())) continue;
                if (freq.headwaySecs() <= 0) continue;
                for (int t = freq.startTime(); t < freq.endTime(); t += freq.headwaySecs()) {
                    String depId = trip.id() + ":" + dateStr + ":" + formatTime(t);
                    profiles.add(new DepartureProfile(depId, t, null));
                }
            }
        }

        return profiles;
    }

    private static double prevDeparture(List<GtfsStopTime> stopTimes, int idx, double base) {
        GtfsStopTime st = stopTimes.get(idx);
        return st.departureTime() != null ? st.departureTime() : base;
    }

    private static double nextTimepointDep(List<GtfsStopTime> stopTimes, int i, double base) {
        for (int j = i; j < stopTimes.size(); j++) {
            GtfsStopTime st = stopTimes.get(j);
            if (st.isTimepoint() && st.departureTime() != null) {
                return st.departureTime();
            }
        }
        return base + 60;
    }

    static String formatTime(int secs) {
        int h = secs / 3600;
        int m = (secs % 3600) / 60;
        int s = secs % 60;
        return String.format("%02d%02d%02d", h, m, s);
    }
}

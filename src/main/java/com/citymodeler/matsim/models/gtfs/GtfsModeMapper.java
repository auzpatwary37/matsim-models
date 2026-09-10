package com.citymodeler.matsim.models.gtfs;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps GTFS route_type integers to MATSim transit mode strings.
 */
public final class GtfsModeMapper {

    private static final Map<Integer, String> MODE_MAP = new LinkedHashMap<>();

    static {
        MODE_MAP.put(0, "tram");
        MODE_MAP.put(1, "subway");
        MODE_MAP.put(2, "rail");
        MODE_MAP.put(3, "pt");       // bus
        MODE_MAP.put(4, "ferry");    // ferry
        MODE_MAP.put(5, "cable_car");// cable tram
        MODE_MAP.put(6, "aerial_lift"); // aerial lift
        MODE_MAP.put(7, "funicular"); // funicular
        MODE_MAP.put(8, "aerial_lift"); // aerial tramway
        MODE_MAP.put(9, "funicular"); // subway (trolley)
        MODE_MAP.put(11, "trolleybus");
        MODE_MAP.put(12, "pt");       // monorail
    }

    private GtfsModeMapper() {
    }

    public static String map(int routeType) {
        return MODE_MAP.getOrDefault(routeType, "pt");
    }

    /** Returns true if the route_type is recognized. */
    public static boolean isKnown(int routeType) {
        return MODE_MAP.containsKey(routeType);
    }
}

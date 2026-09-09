package com.citymodeler.matsim.models.osm.network;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.citymodeler.matsim.models.osm.OsmImportIssue;
import com.citymodeler.matsim.models.osm.OsmIssueSeverity;
import com.citymodeler.matsim.models.osm.model.OsmWayRecord;

public final class OsmModeAccessResolver {

    public enum AccessState { FORBIDDEN, LEGALLY_RESTRICTED, ALLOWED }

    public record DirectionDecision(Set<String> allowedModes, boolean forward, boolean backward) {
        public DirectionDecision {
            allowedModes = Set.copyOf(allowedModes);
        }
    }

    public List<DirectionDecision> resolve(OsmWayRecord way, Set<String> ruleAllowedModes) {
        List<DirectionDecision> decisions = new ArrayList<>();
        Set<String> baseModes = applyAccessRestrictions(way.tags(), ruleAllowedModes);

        boolean forwardOnly = resolveOneway(way.tags());
        boolean backwardOnly = resolveOnewayReverse(way.tags());

        if (forwardOnly) {
            decisions.add(new DirectionDecision(filterByDirectionalAccess(way.tags(), baseModes, "forward"), true, false));
        } else if (backwardOnly) {
            decisions.add(new DirectionDecision(filterByDirectionalAccess(way.tags(), baseModes, "backward"), false, true));
        } else {
            decisions.add(new DirectionDecision(baseModes, true, true));
        }
        return decisions;
    }

    private Set<String> applyAccessRestrictions(com.citymodeler.matsim.models.osm.OsmTagSet tags, Set<String> ruleModes) {
        String access = tags.get("access");
        if (isForbidden(access)) {
            Set<String> surviving = new LinkedHashSet<>();
            for (String mode : ruleModes) {
                if (isModeAllowed(tags, mode)) {
                    surviving.add(mode);
                }
            }
            return surviving;
        }
        Set<String> result = new LinkedHashSet<>(ruleModes);
        for (String mode : result) {
            if (isModeForbidden(tags, mode)) {
                result.remove(mode);
            }
        }
        return result;
    }

    private static boolean isModeAllowed(com.citymodeler.matsim.models.osm.OsmTagSet tags, String mode) {
        String modeTag = tags.get(mode);
        if (modeTag != null && !isForbidden(modeTag)) {
            return true;
        }
        if (("bus".equals(mode) || "pt".equals(mode)) && allowsBus(tags)) {
            return true;
        }
        return false;
    }

    private static boolean isModeForbidden(com.citymodeler.matsim.models.osm.OsmTagSet tags, String mode) {
        String modeTag = tags.get(mode);
        if (isForbidden(modeTag)) {
            return true;
        }
        if ("car".equals(mode) && (isForbidden(tags.get("motor_vehicle")) || isForbidden(tags.get("vehicle")))) {
            return true;
        }
        return false;
    }

    private Set<String> filterByDirectionalAccess(com.citymodeler.matsim.models.osm.OsmTagSet tags, Set<String> baseModes, String direction) {
        String directionalKey = "access:" + direction;
        String value = tags.get(directionalKey);
        if (value != null && isForbidden(value)) {
            return Set.of();
        }
        return baseModes;
    }

    private static boolean allowsCars(com.citymodeler.matsim.models.osm.OsmTagSet tags) {
        String motorVehicle = tags.get("motor_vehicle");
        if (isForbidden(motorVehicle)) {
            return false;
        }
        String car = tags.get("car");
        if (isForbidden(car)) {
            return false;
        }
        return true;
    }

    private static boolean allowsBus(com.citymodeler.matsim.models.osm.OsmTagSet tags) {
        String bus = tags.get("bus");
        if (bus != null && !isForbidden(bus)) {
            return true;
        }
        String psv = tags.get("psv");
        if (psv != null && !isForbidden(psv)) {
            return true;
        }
        return false;
    }

    private static boolean isForbidden(String value) {
        return "no".equals(value) || "never".equals(value);
    }

    private static boolean resolveOneway(com.citymodeler.matsim.models.osm.OsmTagSet tags) {
        String oneway = tags.get("oneway");
        if (oneway != null) {
            if ("yes".equals(oneway) || "true".equals(oneway) || "1".equals(oneway)) {
                return true;
            }
            if ("no".equals(oneway) || "false".equals(oneway) || "0".equals(oneway)) {
                return false;
            }
            if ("-1".equals(oneway) || "reverse".equals(oneway)) {
                return false;
            }
            if ("alternating".equals(oneway) || "reversible".equals(oneway)) {
                return true;
            }
        }
        if ("roundabout".equals(tags.get("junction"))) {
            return true;
        }
        if ("motorway".equals(tags.get("highway"))) {
            return true;
        }
        return false;
    }

    private static boolean resolveOnewayReverse(com.citymodeler.matsim.models.osm.OsmTagSet tags) {
        String oneway = tags.get("oneway");
        return "-1".equals(oneway) || "reverse".equals(oneway);
    }
}

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

    private static final java.util.Set<String> FORBIDDEN_VALUES = java.util.Set.of("no", "never");
    private static final java.util.Set<String> RESTRICTED_VALUES = java.util.Set.of("private", "destination", "customers", "delivery");
    private static final java.util.Set<String> ALLOWED_VALUES = java.util.Set.of("yes", "designated", "permissive", "permitted");

    private Set<String> applyAccessRestrictions(com.citymodeler.matsim.models.osm.OsmTagSet tags, Set<String> ruleModes) {
        String access = tags.get("access");
        if (access != null && FORBIDDEN_VALUES.contains(access)) {
            Set<String> surviving = new LinkedHashSet<>();
            for (String mode : ruleModes) {
                if (isModeExplicitlyAllowed(tags, mode)) {
                    surviving.add(mode);
                }
            }
            return surviving;
        }
        Set<String> result = new LinkedHashSet<>(ruleModes);
        for (String mode : result) {
            if (isModeRestricted(tags, mode)) {
                result.remove(mode);
            }
        }
        return result;
    }

    private static AccessState resolveAccessState(com.citymodeler.matsim.models.osm.OsmTagSet tags, String mode) {
        // Most specific key wins: mode-specific > vehicle class > general
        String modeValue = tags.get(mode);
        if (modeValue != null) {
            AccessState s = stateOf(modeValue);
            if (s != null) return s;
        }
        if ("car".equals(mode)) {
            String mv = tags.get("motor_vehicle");
            if (mv != null) {
                AccessState s = stateOf(mv);
                if (s != null) return s;
            }
            String veh = tags.get("vehicle");
            if (veh != null) {
                AccessState s = stateOf(veh);
                if (s != null) return s;
            }
        }
        if (("bus".equals(mode) || "pt".equals(mode))) {
            String psv = tags.get("psv");
            if (psv != null) {
                AccessState s = stateOf(psv);
                if (s != null) return s;
            }
        }
        String access = tags.get("access");
        if (access != null) {
            AccessState s = stateOf(access);
            if (s != null) return s;
        }
        return null;
    }

    private static AccessState stateOf(String value) {
        if (value == null) return null;
        if (FORBIDDEN_VALUES.contains(value)) return AccessState.FORBIDDEN;
        if (RESTRICTED_VALUES.contains(value)) return AccessState.LEGALLY_RESTRICTED;
        if (ALLOWED_VALUES.contains(value)) return AccessState.ALLOWED;
        return null;
    }

    private static boolean isModeExplicitlyAllowed(com.citymodeler.matsim.models.osm.OsmTagSet tags, String mode) {
        String modeTag = tags.get(mode);
        if (modeTag != null && ALLOWED_VALUES.contains(modeTag)) {
            return true;
        }
        if ("bus".equals(mode) || "pt".equals(mode)) {
            String psv = tags.get("psv");
            if (psv != null && ALLOWED_VALUES.contains(psv)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isModeRestricted(com.citymodeler.matsim.models.osm.OsmTagSet tags, String mode) {
        AccessState state = resolveAccessState(tags, mode);
        return state == AccessState.FORBIDDEN;
    }

    private Set<String> filterByDirectionalAccess(com.citymodeler.matsim.models.osm.OsmTagSet tags, Set<String> baseModes, String direction) {
        String generalDirectional = tags.get("access:" + direction);
        if (isForbidden(generalDirectional)) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>(baseModes);
        for (String mode : baseModes) {
            String modeDirectional = tags.get(mode + ":" + direction);
            if (isForbidden(modeDirectional)) {
                result.remove(mode);
                continue;
            }
            String mvDirectional = "car".equals(mode) ? tags.get("motor_vehicle:" + direction) : null;
            if (isForbidden(mvDirectional)) {
                result.remove(mode);
            }
        }
        return result;
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

    public static boolean hasDynamicOneway(com.citymodeler.matsim.models.osm.OsmTagSet tags) {
        String oneway = tags.get("oneway");
        return "alternating".equals(oneway) || "reversible".equals(oneway);
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
                return false;
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

package com.citymodeler.matsim.models.osm.network;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

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
        return resolve(way, ruleAllowedModes, false);
    }

    /**
     * @param ruleDefaultOneway the way-rule's default oneway flag, applied when OSM carries no
     *                          explicit {@code oneway} tag (e.g. tram tracks default to oneway).
     */
    /**
     * Rule candidate modes presented to access resolution. When {@code addBusToCarRoads} is set, a
     * car road also offers {@code bus} as a <b>default candidate</b> so the bus routable subnetwork
     * exists without a separate tag; explicit OSM access tags are still resolved afterwards and have
     * final authority ({@code bus=no}, {@code psv=no}, {@code motor_vehicle=no + bus=yes}, ...).
     *
     * <p>This is deliberately done before resolution rather than by cloning {@code car} into
     * {@code bus} after the fact: a post-hoc clone would override OSM access semantics and could
     * fabricate bus access on a road tagged {@code bus=no}.
     */
    public static Set<String> candidateModes(Set<String> ruleAllowedModes, boolean addBusToCarRoads) {
        if (!addBusToCarRoads || !ruleAllowedModes.contains("car") || ruleAllowedModes.contains("bus")) {
            return ruleAllowedModes;
        }
        Set<String> modes = new TreeSet<>(ruleAllowedModes);
        modes.add("bus");
        return modes;
    }

    /**
     * Maps an OSM exclusion token used by a turn-restriction {@code except=*} to the internal network
     * modes it exempts, so the restriction layer reuses the same ontology as access resolution
     * instead of doing raw string subtraction. The class hierarchy mirrors {@link #accessKeys}: a bus
     * is a public-service vehicle, a motor vehicle and a vehicle, so {@code except=motor_vehicle} must
     * exempt a bus too, not just a car. Unknown tokens pass through unchanged.
     */
    public static Set<String> internalModesForExclusion(String osmMode) {
        return switch (osmMode) {
            case "motorcar", "car" -> Set.of("car");
            case "motor_vehicle" -> Set.of("car", "bus", "pt");
            case "vehicle" -> Set.of("car", "bus", "pt");
            case "psv", "bus" -> Set.of("bus", "pt");
            default -> Set.of(osmMode);
        };
    }

    public List<DirectionDecision> resolve(OsmWayRecord way, Set<String> ruleAllowedModes,
                                           boolean ruleDefaultOneway) {
        List<DirectionDecision> decisions = new ArrayList<>();
        Set<String> baseModes = applyAccessRestrictions(way.tags(), ruleAllowedModes);

        boolean forwardOnly = resolveOneway(way.tags(), ruleDefaultOneway);
        boolean backwardOnly = resolveOnewayReverse(way.tags());

        if (forwardOnly) {
            decisions.add(new DirectionDecision(filterByDirectionalAccess(way.tags(), baseModes, "forward"), true, false));
        } else if (backwardOnly) {
            decisions.add(new DirectionDecision(filterByDirectionalAccess(way.tags(), baseModes, "backward"), false, true));
        } else {
            Set<String> forwardModes = filterByDirectionalAccess(way.tags(), baseModes, "forward");
            Set<String> reverseModes = filterByDirectionalAccess(way.tags(), baseModes, "backward");
            if (forwardModes.equals(reverseModes)) {
                decisions.add(new DirectionDecision(forwardModes, true, true));
            } else {
                if (!forwardModes.isEmpty()) {
                    decisions.add(new DirectionDecision(forwardModes, true, false));
                }
                if (!reverseModes.isEmpty()) {
                    decisions.add(new DirectionDecision(reverseModes, false, true));
                }
            }
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
        result.removeIf(mode -> isModeRestricted(tags, mode));
        return result;
    }

    private static AccessState resolveAccessState(com.citymodeler.matsim.models.osm.OsmTagSet tags, String mode) {
        // Most specific key wins: mode-specific > vehicle class > general.
        for (String key : accessKeys(mode)) {
            String value = tags.get(key);
            if (value != null) {
                AccessState s = stateOf(value);
                if (s != null) {
                    return s;
                }
            }
        }
        return null;
    }

    /**
     * OSM access keys that govern a mode, from most to least specific. A bus is a public-service
     * vehicle, a motor vehicle and a vehicle, so {@code motor_vehicle=no} forbids it too unless a
     * more specific key allows it; likewise a car is a motor vehicle.
     */
    private static List<String> accessKeys(String mode) {
        List<String> keys = new ArrayList<>();
        keys.add(mode);
        if ("bus".equals(mode) || "pt".equals(mode)) {
            keys.add("psv");
        }
        if ("bus".equals(mode) || "pt".equals(mode) || "car".equals(mode)) {
            keys.add("motor_vehicle");
            keys.add("vehicle");
        }
        keys.add("access");
        return keys;
    }

    private static AccessState resolveDirectionalState(
            com.citymodeler.matsim.models.osm.OsmTagSet tags, String mode, String direction) {
        List<String> keys = new ArrayList<>();
        for (String key : accessKeys(mode)) {
            keys.add(key.equals("access") ? "access:" + direction : key + ":" + direction);
        }
        for (String key : keys) {
            String value = tags.get(key);
            if (value != null) {
                AccessState s = stateOf(value);
                if (s != null) {
                    return s;
                }
            }
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
        Set<String> result = new LinkedHashSet<>(baseModes);
        for (String mode : baseModes) {
            // Resolve each mode exclusively through the specificity ladder so a more specific
            // directional override (e.g. access:forward=no + bus:forward=yes) wins, exactly as in the
            // non-directional resolveAccessState(). No global short-circuit.
            if (resolveDirectionalState(tags, mode, direction) == AccessState.FORBIDDEN) {
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
        return resolveOneway(tags, false);
    }

    private static boolean resolveOneway(com.citymodeler.matsim.models.osm.OsmTagSet tags,
                                         boolean ruleDefaultOneway) {
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
        return ruleDefaultOneway;
    }

    private static boolean resolveOnewayReverse(com.citymodeler.matsim.models.osm.OsmTagSet tags) {
        String oneway = tags.get("oneway");
        return "-1".equals(oneway) || "reverse".equals(oneway);
    }
}

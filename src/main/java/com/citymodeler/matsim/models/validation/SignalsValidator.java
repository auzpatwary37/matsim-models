package com.citymodeler.matsim.models.validation;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.network.Link;
import com.citymodeler.matsim.models.network.Network;
import com.citymodeler.matsim.models.signals.Signal;
import com.citymodeler.matsim.models.signals.SignalGroup;
import com.citymodeler.matsim.models.signals.SignalSystem;
import com.citymodeler.matsim.models.signals.SignalSystemGroups;
import com.citymodeler.matsim.models.signals.control.ControlledSignalSystem;
import com.citymodeler.matsim.models.signals.control.SignalGroupSettings;
import com.citymodeler.matsim.models.signals.control.SignalPlan;
import com.citymodeler.matsim.models.signals.timings.AmberIntervals;
import com.citymodeler.matsim.models.signals.timings.AmberTimes;
import com.citymodeler.matsim.models.signals.timings.IntergreenTimes;
import com.citymodeler.matsim.models.signals.timings.SystemAmberTimes;
import com.citymodeler.matsim.models.signals.timings.SystemIntergreens;

/**
 * Cross-file and network-reference validation for the five signal families.
 * Input maps are exactly what the readers produce; a missing (null) family is
 * skipped, and its cross-file checks are skipped too — partial-scenario
 * validation is legitimate. When the {@code systems} family is empty, every
 * other family's system ids are reported as {@code UNKNOWN_SYSTEM} WARNINGs
 * (they cannot be resolved); when non-empty, unresolvable system ids are
 * ERRORs.
 *
 * <p>Codes (ERROR unless noted): {@code DANGLING_SIGNAL_REF},
 * {@code UNKNOWN_SYSTEM}, {@code DANGLING_GROUP_REF},
 * {@code MISSING_CONTROLLER_IDENTIFIER} (WARNING),
 * {@code LINK_REF_NOT_IN_NETWORK}, {@code LANE_REF_LINK_MISMATCH} (WARNING,
 * id-prefix heuristic only), {@code TO_LINK_NOT_IN_NETWORK},
 * {@code DANGLING_AMBER_REF}, {@code DANGLING_INTERGREEN_GROUP_REF},
 * {@code NEGATIVE_SECONDS} (defensive: the model constructors validate, so
 * this fires only for hand-built legacy holders), and {@code UNKNOWN_ELEMENT}
 * (WARNING) per entry of the unmodeled-children channel reported by the
 * readers via {@code getUnknownElements()}.</p>
 */
public final class SignalsValidator {
    private static final String DOMAIN = "signals";

    private final Network network;

    public SignalsValidator(Network network) {
        this.network = Objects.requireNonNull(network, "network");
    }

    public ValidationReport validate(Map<Id<SignalSystem>, SignalSystem> systems,
                                     Map<Id<SignalSystem>, SignalSystemGroups> groups,
                                     Map<Id<SignalSystem>, ControlledSignalSystem> control,
                                     AmberTimes amberTimes, IntergreenTimes intergreenTimes) {
        return validate(systems, groups, control, amberTimes, intergreenTimes, List.of());
    }

    public ValidationReport validate(Map<Id<SignalSystem>, SignalSystem> systems,
                                     Map<Id<SignalSystem>, SignalSystemGroups> groups,
                                     Map<Id<SignalSystem>, ControlledSignalSystem> control,
                                     AmberTimes amberTimes, IntergreenTimes intergreenTimes,
                                     List<String> unknownElements) {
        ValidationReport report = new ValidationReport();
        Set<String> linkIds = new HashSet<>();
        for (Id<Link> linkId : network.getLinks().keySet()) {
            linkIds.add(linkId.toString());
        }
        if (systems != null) {
            validateSystems(systems, linkIds, report);
        }
        if (groups != null) {
            validateGroups(systems, groups, report);
        }
        if (control != null) {
            validateControl(systems, groups, control, report);
        }
        if (amberTimes != null) {
            validateAmber(systems, amberTimes, report);
        }
        if (intergreenTimes != null) {
            validateIntergreen(systems, groups, intergreenTimes, report);
        }
        if (unknownElements != null) {
            for (String entry : unknownElements) {
                report.addIssue(issue(ValidationSeverity.WARNING, "UNKNOWN_ELEMENT",
                        "unmodeled element recorded by the reader: " + entry, entry));
            }
        }
        return report;
    }

    private void validateSystems(Map<Id<SignalSystem>, SignalSystem> systems,
                                 Set<String> linkIds, ValidationReport report) {
        for (SignalSystem system : systems.values()) {
            for (Signal signal : system.getSignals().values()) {
                if (!linkIds.contains(signal.getLinkIdRef().toString())) {
                    report.addIssue(issue(ValidationSeverity.ERROR, "LINK_REF_NOT_IN_NETWORK",
                            "signal " + signal.getId() + " references link "
                                    + signal.getLinkIdRef() + " which is not in the network",
                            signal.getId().toString()));
                }
                for (Id<com.citymodeler.matsim.models.lanes.Lane> laneRef : signal.getLaneRefs()) {
                    if (!laneRef.toString().startsWith(signal.getLinkIdRef() + "_")) {
                        report.addIssue(issue(ValidationSeverity.WARNING, "LANE_REF_LINK_MISMATCH",
                                "lane ref " + laneRef + " on signal " + signal.getId()
                                        + " is not prefixed with the signal's link "
                                        + signal.getLinkIdRef() + " (convention: <link>_<index>)",
                                        laneRef.toString()));
                    }
                }
                for (Id<Link> toLink : signal.getRestrictions().getToLinks()) {
                    if (!linkIds.contains(toLink.toString())) {
                        report.addIssue(issue(ValidationSeverity.ERROR, "TO_LINK_NOT_IN_NETWORK",
                                "restriction toLink " + toLink + " on signal " + signal.getId()
                                        + " is not in the network",
                                toLink.toString()));
                    }
                }
            }
        }
    }

    private void validateGroups(Map<Id<SignalSystem>, SignalSystem> systems,
                                Map<Id<SignalSystem>, SignalSystemGroups> groups,
                                ValidationReport report) {
        for (SignalSystemGroups systemGroups : groups.values()) {
            Id<SignalSystem> systemId = systemGroups.getId();
            if (systems == null) {
                continue; // systems family absent: signal refs cannot be resolved, skip
            }
            if (!systems.containsKey(systemId)) {
                unknownSystem(systems, systemId, "signalGroups", report);
                continue;
            }
            SignalSystem system = systems.get(systemId);
            for (SignalGroup group : systemGroups.getGroups().values()) {
                for (Id<Signal> signalRef : group.getSignalRefIds()) {
                    if (!system.getSignals().containsKey(signalRef)) {
                        report.addIssue(issue(ValidationSeverity.ERROR, "DANGLING_SIGNAL_REF",
                                "group " + group.getId() + " references signal " + signalRef
                                        + " which is not in system " + systemId,
                                signalRef.toString()));
                    }
                }
            }
        }
    }

    private void validateControl(Map<Id<SignalSystem>, SignalSystem> systems,
                                 Map<Id<SignalSystem>, SignalSystemGroups> groups,
                                 Map<Id<SignalSystem>, ControlledSignalSystem> control,
                                 ValidationReport report) {
        for (ControlledSignalSystem system : control.values()) {
            Id<SignalSystem> systemId = system.getId();
            if (systems != null && !systems.containsKey(systemId)) {
                unknownSystem(systems, systemId, "signalControl", report);
            }
            if (system.isControllerPresent() && isBlank(system.getControllerIdentifier())) {
                report.addIssue(issue(ValidationSeverity.WARNING, "MISSING_CONTROLLER_IDENTIFIER",
                        "system " + systemId + " has a signalSystemController without a"
                                + " controllerIdentifier",
                        systemId.toString()));
            }
            SignalSystemGroups systemGroups = groups == null ? null : groups.get(systemId);
            for (SignalPlan plan : system.getPlans()) {
                for (SignalGroupSettings settings : plan.getSettings().values()) {
                    if (settings.getOnsetSec() < 0 || settings.getDroppingSec() < 0) {
                        report.addIssue(issue(ValidationSeverity.ERROR, "NEGATIVE_SECONDS",
                                "group settings for " + settings.getSignalGroupRef() + " in system "
                                        + systemId + " carry negative seconds",
                                settings.getSignalGroupRef().toString()));
                    }
                    if (systemGroups != null && !systemGroups.getGroups()
                            .containsKey(settings.getSignalGroupRef())) {
                        report.addIssue(issue(ValidationSeverity.ERROR, "DANGLING_GROUP_REF",
                                "control settings reference group " + settings.getSignalGroupRef()
                                        + " in system " + systemId + " which has no such group",
                                settings.getSignalGroupRef().toString()));
                    }
                }
            }
        }
    }

    private void validateAmber(Map<Id<SignalSystem>, SignalSystem> systems,
                               AmberTimes amberTimes, ValidationReport report) {
        checkAmberIntervals(amberTimes.getGlobalDefaults(), "globalDefaults", report);
        for (SystemAmberTimes system : amberTimes.getSystems().values()) {
            Id<SignalSystem> systemId = system.getId();
            if (systems == null || !systems.containsKey(systemId)) {
                unknownSystem(systems, systemId, "amberTimes", report);
                continue;
            }
            checkAmberIntervals(system.getSystemDefaults(), systemId.toString(), report);
            SignalSystem target = systems.get(systemId);
            for (Id<Signal> signalId : system.getSignalOverrides().keySet()) {
                if (!target.getSignals().containsKey(signalId)) {
                    report.addIssue(issue(ValidationSeverity.ERROR, "DANGLING_AMBER_REF",
                            "amber override for signal " + signalId + " in system " + systemId
                                    + " which has no such signal",
                            signalId.toString()));
                }
            }
        }
    }

    private void checkAmberIntervals(AmberIntervals intervals, String where, ValidationReport report) {
        if (intervals == null) {
            return;
        }
        if ((intervals.getRedAmberSec() != null && intervals.getRedAmberSec() < 0)
                || (intervals.getAmberSec() != null && intervals.getAmberSec() < 0)) {
            report.addIssue(issue(ValidationSeverity.ERROR, "NEGATIVE_SECONDS",
                    "amber intervals at " + where + " carry negative seconds", where));
        }
    }

    private void validateIntergreen(Map<Id<SignalSystem>, SignalSystem> systems,
                                    Map<Id<SignalSystem>, SignalSystemGroups> groups,
                                    IntergreenTimes intergreenTimes, ValidationReport report) {
        for (SystemIntergreens system : intergreenTimes.getSystems().values()) {
            Id<SignalSystem> systemId = system.getId();
            if (systems == null || !systems.containsKey(systemId)) {
                unknownSystem(systems, systemId, "intergreenTimes", report);
                continue;
            }
            SignalSystemGroups systemGroups = groups == null ? null : groups.get(systemId);
            if (systemGroups == null) {
                continue;
            }
            for (Map.Entry<Id<SignalGroup>, Map<Id<SignalGroup>, Integer>> row
                    : system.getEndingToBeginningSec().entrySet()) {
                checkIntergreenGroup(systemGroups, row.getKey(), systemId, "ending", report);
                for (Map.Entry<Id<SignalGroup>, Integer> cell : row.getValue().entrySet()) {
                    checkIntergreenGroup(systemGroups, cell.getKey(), systemId, "beginning", report);
                    if (cell.getValue() < 0) {
                        report.addIssue(issue(ValidationSeverity.ERROR, "NEGATIVE_SECONDS",
                                "intergreen " + row.getKey() + " -> " + cell.getKey()
                                        + " in system " + systemId + " carries negative seconds",
                                row.getKey() + "->" + cell.getKey()));
                    }
                }
            }
        }
    }

    private void checkIntergreenGroup(SignalSystemGroups systemGroups, Id<SignalGroup> groupId,
                                      Id<SignalSystem> systemId, String side, ValidationReport report) {
        if (!systemGroups.getGroups().containsKey(groupId)) {
            report.addIssue(issue(ValidationSeverity.ERROR, "DANGLING_INTERGREEN_GROUP_REF",
                    side + "SignalGroup " + groupId + " in intergreen system " + systemId
                            + " which has no such group",
                    groupId.toString()));
        }
    }

    private void unknownSystem(Map<Id<SignalSystem>, SignalSystem> systems, Id<SignalSystem> systemId,
                               String family, ValidationReport report) {
        if (systems == null) {
            // The systems family was not supplied at all: cross-file system resolution
            // is skipped entirely (no warning, no error). Supplying an EMPTY map means
            // the file was supplied with no systems, which is a different case handled below.
            return;
        }
        ValidationSeverity severity = systems.isEmpty()
                ? ValidationSeverity.WARNING : ValidationSeverity.ERROR;
        report.addIssue(issue(severity, "UNKNOWN_SYSTEM",
                family + " references system " + systemId + " which is not in the systems family",
                systemId.toString()));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static ValidationIssue issue(ValidationSeverity severity, String code,
                                         String message, String objectId) {
        return new ValidationIssue(severity, DOMAIN, code, message, objectId, null);
    }
}

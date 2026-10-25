package edu.roadsafety.service;

import edu.roadsafety.enums.EvidenceStatus;
import edu.roadsafety.enums.IncidentType;
import edu.roadsafety.enums.ObservationType;
import edu.roadsafety.enums.SafetyState;
import edu.roadsafety.model.Incident;
import edu.roadsafety.model.IncidentHistory;
import edu.roadsafety.model.Observation;
import edu.roadsafety.model.RoadSection;
import edu.roadsafety.model.RoadsideUnit;
import edu.roadsafety.model.ThresholdConfig;
import edu.roadsafety.model.Warning;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RoadSafetySystem {
    private final ThresholdConfig thresholds;
    private final EvidenceCorroborationService evidenceService;
    private final IncidentClassifier classificationService = new IncidentClassificationService();
    private final SafetyStateMachine stateMachine = new SafetyStateMachine();
    private final RoleAssignmentService roleAssignmentService = new RoleAssignmentService();
    private final WarningService warningService = new WarningService();
    private final List<RoadSection> sections = new ArrayList<>();
    private final Map<String, RoadsideUnit> units = new LinkedHashMap<>();
    private final List<Observation> observations = new ArrayList<>();
    private final Map<String, Incident> incidents = new LinkedHashMap<>();
    private final Map<String, Incident> activeIncidentsBySection = new LinkedHashMap<>();
    private final List<IncidentHistory> history = new ArrayList<>();
    private final Map<String, edu.roadsafety.enums.OperationalRole> previousRoles = new LinkedHashMap<>();
    private long incidentSequence;

    public RoadSafetySystem() {
        this(ThresholdConfig.courseDefaults());
    }

    public RoadSafetySystem(ThresholdConfig thresholds) {
        this.thresholds = thresholds;
        evidenceService = new EvidenceCorroborationService(thresholds);
        for (int index = 1; index <= 5; index++) {
            RoadSection section = new RoadSection("S" + index, index - 1);
            sections.add(section);
            units.put("RU-" + index, new RoadsideUnit("RU-" + index, section.getSectionId(),
                    section.getRole()));
            if (index > 1) {
                section.connectTo("S" + (index - 1));
                sections.get(index - 2).connectTo(section.getSectionId());
            }
        }
    }

    public ProcessingResult processObservation(Observation observation) {
        return processObservation(observation, observation.timestamp());
    }

    public ProcessingResult processObservation(Observation observation, Instant evaluationTime) {
        long eventStart = System.nanoTime();
        if (!units.containsKey(observation.roadsideUnitId())
                || !observation.roadSectionId().equals(units.get(observation.roadsideUnitId()).roadSectionId())) {
            throw new IllegalArgumentException("Observation unit does not belong to the specified section");
        }
        observations.add(observation);
        addHistory(observation.timestamp(), observation.roadSectionId(), "OBSERVATION",
                observation.observationType() + " (" + observation.value() + ")", null, null);

        long evidenceStart = System.nanoTime();
        List<RoadSection> sectionsToEvaluate = sectionsToEvaluate(observation.roadSectionId());
        Map<String, edu.roadsafety.model.EvidenceResult> results = new LinkedHashMap<>();
        for (RoadSection section : sectionsToEvaluate) {
            var result = evidenceService.evaluate(section.getSectionId(), observations, observation,
                    evaluationTime, Set.copyOf(section.getConnectedSectionIds()));
            section.setLatestEvidence(result);
            results.put(section.getSectionId(), result);
            if (result.status() == EvidenceStatus.CONTRADICTORY) {
                addHistory(evaluationTime, section.getSectionId(), "EVIDENCE_CONTRADICTION",
                        result.explanation(), null, null);
            }
        }
        long evidenceNanos = System.nanoTime() - evidenceStart;

        long stateStart = System.nanoTime();
        for (RoadSection section : sectionsToEvaluate) {
            updateSectionState(section, results.get(section.getSectionId()), evaluationTime);
        }
        long stateNanos = System.nanoTime() - stateStart;

        long rolesStart = System.nanoTime();
        roleAssignmentService.assign(sections, activeIncidentsBySection);
        for (RoadSection section : sections) {
            var previousRole = previousRoles.put(section.getSectionId(), section.getRole());
            if (previousRole != null && previousRole != section.getRole()) {
                addHistory(evaluationTime, section.getSectionId(), "ROLE_CHANGE",
                        previousRole + " -> " + section.getRole(), null, null);
            }
            String unitId = "RU-" + (section.getPosition() + 1);
            units.put(unitId, new RoadsideUnit(unitId, section.getSectionId(), section.getRole()));
        }
        long rolesNanos = System.nanoTime() - rolesStart;

        long warningStart = System.nanoTime();
        for (RoadSection section : sections) {
            Warning nextWarning = warningService.create(section, evaluationTime);
            Warning previousWarning = section.getWarning();
            if (!sameWarning(previousWarning, nextWarning)) {
                section.setWarning(nextWarning);
                if (nextWarning != null) {
                    addHistory(evaluationTime, section.getSectionId(), "WARNING",
                            nextWarning.level() + ": " + nextWarning.heading(), null, null);
                } else if (previousWarning != null) {
                    addHistory(evaluationTime, section.getSectionId(), "WARNING_CLEARED",
                            "No active warning", null, null);
                }
            }
        }
        long warningNanos = System.nanoTime() - warningStart;
        long eventNanos = System.nanoTime() - eventStart;
        return new ProcessingResult(results.get(observation.roadSectionId()), eventNanos, evidenceNanos,
                stateNanos, rolesNanos, warningNanos);
    }

    private List<RoadSection> sectionsToEvaluate(String observedSectionId) {
        RoadSection observedSection = section(observedSectionId);
        List<RoadSection> selected = new ArrayList<>();
        selected.add(observedSection);
        for (RoadSection candidate : sections) {
            if (candidate != observedSection
                    && observedSection.getConnectedSectionIds().contains(candidate.getSectionId())
                    && (candidate.getSafetyState() == SafetyState.ABNORMAL
                    || candidate.getSafetyState() == SafetyState.INCIDENT_CONFIRMATION
                    || activeIncidentsBySection.containsKey(candidate.getSectionId()))) {
                selected.add(candidate);
            }
        }
        return selected;
    }

    private void updateSectionState(RoadSection section, edu.roadsafety.model.EvidenceResult result, Instant now) {
        SafetyState state = section.getSafetyState();
        if (result.status() == EvidenceStatus.STALE || result.status() == EvidenceStatus.DEGRADED) {
            if (state == SafetyState.NORMAL || state == SafetyState.ABNORMAL) {
                transition(section, SafetyState.OBSERVATION_DEFICIENCY, now, result.explanation());
            } else if (state == SafetyState.INCIDENT_CONFIRMATION) {
                addHistory(now, section.getSectionId(), "OBSERVATION_DEFICIENCY",
                        result.explanation(), null, null);
            }
            return;
        }

        Incident activeIncident = activeIncidentsBySection.get(section.getSectionId());
        if (activeIncident != null) {
            List<Observation> sectionObservations = observationsFor(section.getSectionId());
            if (section.getSafetyState() == SafetyState.RECOVERY
                    && hasRoadClearAfterRecovery(sectionObservations, activeIncident, now)) {
                transition(section, SafetyState.NORMAL, now, "Road-clear evidence followed recovery monitoring");
                activeIncident.close();
                activeIncidentsBySection.remove(section.getSectionId());
                addHistory(now, section.getSectionId(), "INCIDENT_CLOSED",
                        "Incident closed after evidence-supported recovery", null, null);
                return;
            }
            boolean obstructionPresent = sectionObservations.stream()
                    .anyMatch(item -> item.observationType() == ObservationType.OBSTACLE
                            && !item.timestamp().isBefore(activeIncident.getDetectedAt())
                            && item.quality() >= 0.6);
            if (obstructionPresent && (section.getSafetyState() == SafetyState.CONFIRMED_INCIDENT
                    || section.getSafetyState() == SafetyState.RECOVERY)) {
                transition(section, SafetyState.CONTINUING_HAZARD, now,
                        "Physical obstruction remains after incident confirmation");
            }
            if (section.getSafetyState() != SafetyState.RECOVERY
                    && hasRepeatedNormalPassage(sectionObservations, activeIncident, now)
                    && !hasObstructionAfterLatestClear(sectionObservations, now)) {
                transition(section, SafetyState.RECOVERY, now,
                        "Repeated normal-passage evidence begins recovery monitoring");
                if (hasRoadClearAfterRecovery(sectionObservations, activeIncident, now)) {
                    transition(section, SafetyState.NORMAL, now,
                            "Road-clear evidence confirms the completed recovery");
                    activeIncident.close();
                    activeIncidentsBySection.remove(section.getSectionId());
                    addHistory(now, section.getSectionId(), "INCIDENT_CLOSED",
                            "Incident closed after evidence-supported recovery", null, null);
                }
            }
            for (Observation item : sectionObservations) {
                if (!activeIncident.getEvidence().contains(item)) {
                    activeIncident.addEvidence(item);
                }
            }
            return;
        }

        if (isFalsePositiveResolution(section.getSectionId(), now)
                && (state == SafetyState.ABNORMAL || state == SafetyState.INCIDENT_CONFIRMATION)) {
            transition(section, SafetyState.NORMAL, now,
                    "Subsequent normal passage contradicts the unconfirmed abnormal event");
            addHistory(now, section.getSectionId(), "FALSE_POSITIVE",
                    "Abnormal evidence was not corroborated by subsequent passage", null, null);
            return;
        }

        switch (result.status()) {
            case CONTRADICTORY -> moveToConfirmation(section, now, result.explanation());
            case CONFIRMED -> confirmIncident(section, result.score(), now);
            case INCIDENT_CONFIRMATION -> {
                if (state == SafetyState.NORMAL || state == SafetyState.OBSERVATION_DEFICIENCY) {
                    transition(section, SafetyState.ABNORMAL, now, result.explanation());
                }
                transition(section, SafetyState.INCIDENT_CONFIRMATION, now, result.explanation());
            }
            case ABNORMAL -> {
                if (state == SafetyState.NORMAL || state == SafetyState.OBSERVATION_DEFICIENCY) {
                    transition(section, SafetyState.ABNORMAL, now, result.explanation());
                }
            }
            case INSUFFICIENT -> {
                if ((state == SafetyState.OBSERVATION_DEFICIENCY || state == SafetyState.ABNORMAL)
                        && hasRecentNormalEvidence(section.getSectionId(), now)) {
                    transition(section, SafetyState.NORMAL, now, "Recent observations support normal operation");
                }
            }
            case STALE, DEGRADED -> throw new IllegalStateException("Stale or degraded evidence was handled before classification");
        }
    }

    private boolean isFalsePositiveResolution(String sectionId, Instant now) {
        Observation latestNormal = observations.stream()
                .filter(observation -> observation.roadSectionId().equals(sectionId)
                        && observation.timestamp().equals(now)
                        && (observation.observationType() == ObservationType.NORMAL_PASSAGE
                        || observation.observationType() == ObservationType.NORMAL_TRAFFIC)
                        && observation.quality() >= 0.5)
                .max(java.util.Comparator.comparing(Observation::timestamp))
                .orElse(null);
        if (latestNormal == null) {
            return false;
        }
        return observations.stream().anyMatch(observation -> observation.roadSectionId().equals(sectionId)
                && observation.timestamp().isBefore(latestNormal.timestamp())
                && (observation.observationType() == ObservationType.ABNORMAL_STOP
                || observation.observationType() == ObservationType.IMPACT
                || observation.observationType() == ObservationType.LOSS_OF_CONTROL)
                && (latestNormal.vehicleId().isBlank() || observation.vehicleId().isBlank()
                || latestNormal.vehicleId().equals(observation.vehicleId())));
    }

    private void moveToConfirmation(RoadSection section, Instant now, String explanation) {
        SafetyState state = section.getSafetyState();
        if (state == SafetyState.NORMAL || state == SafetyState.OBSERVATION_DEFICIENCY) {
            transition(section, SafetyState.ABNORMAL, now, explanation);
            state = section.getSafetyState();
        }
        if (state == SafetyState.ABNORMAL) {
            transition(section, SafetyState.INCIDENT_CONFIRMATION, now, explanation);
        }
    }

    private void confirmIncident(RoadSection section, double score, Instant now) {
        SafetyState state = section.getSafetyState();
        if (state == SafetyState.NORMAL || state == SafetyState.OBSERVATION_DEFICIENCY) {
            transition(section, SafetyState.ABNORMAL, now, "Corroborated abnormal evidence detected");
            state = section.getSafetyState();
        }
        if (state == SafetyState.ABNORMAL) {
            transition(section, SafetyState.INCIDENT_CONFIRMATION, now, "Evidence reached incident confirmation");
            state = section.getSafetyState();
        }
        if (state == SafetyState.INCIDENT_CONFIRMATION) {
            transition(section, SafetyState.CONFIRMED_INCIDENT, now,
                    "Corroborated evidence score " + String.format("%.1f", score));
            List<Observation> sectionEvidence = observationsFor(section.getSectionId());
            Incident incident = new Incident("INC-" + (++incidentSequence), section.getSectionId(),
                    classificationService.classify(sectionEvidence),
                    sectionEvidence.stream().map(Observation::timestamp).min(Instant::compareTo).orElse(now),
                    sectionEvidence.isEmpty() ? "FORWARD" : sectionEvidence.get(sectionEvidence.size() - 1).direction());
            sectionEvidence.forEach(incident::addEvidence);
            incident.confirm(now);
            incidents.put(incident.getIncidentId(), incident);
            activeIncidentsBySection.put(section.getSectionId(), incident);
            addHistory(now, section.getSectionId(), "INCIDENT_CONFIRMED",
                    incident.getIncidentId() + " - " + incident.getIncidentType(), null, null);
            boolean obstructionPresent = sectionEvidence.stream()
                    .anyMatch(item -> item.observationType() == ObservationType.OBSTACLE
                            && !item.timestamp().isBefore(incident.getDetectedAt())
                            && item.quality() >= 0.6);
            if (obstructionPresent) {
                transition(section, SafetyState.CONTINUING_HAZARD, now,
                        "Obstacle evidence remains after incident confirmation");
            }
        }
    }

    private boolean hasRepeatedNormalPassage(List<Observation> sectionObservations, Incident incident, Instant now) {
        var start = now.minus(thresholds.evidenceWindow());
        List<Observation> recoveryObservations = sectionObservations.stream()
                .filter(observation -> !observation.timestamp().isBefore(start)
                        && (incident.getConfirmedAt() == null
                        || !observation.timestamp().isBefore(incident.getConfirmedAt()))
                        && observation.quality() >= 0.7)
                .toList();
        long normalPassages = recoveryObservations.stream()
                .filter(observation -> observation.observationType() == ObservationType.NORMAL_PASSAGE
                        || observation.observationType() == ObservationType.NORMAL_TRAFFIC)
                .count();
        return normalPassages >= 2;
    }

    private boolean hasRoadClearAfterRecovery(List<Observation> sectionObservations, Incident incident, Instant now) {
        Instant recoveryStarted = history.stream()
                .filter(entry -> entry.roadSectionId().equals(incident.getRoadSectionId())
                        && entry.stateAfter() == SafetyState.RECOVERY)
                .map(IncidentHistory::timestamp)
                .max(Instant::compareTo)
                .orElse(incident.getConfirmedAt());
        return sectionObservations.stream().anyMatch(observation ->
                (observation.observationType() == ObservationType.ROAD_CLEAR
                        || observation.observationType() == ObservationType.NO_OBSTRUCTION)
                        && !observation.timestamp().isBefore(recoveryStarted)
                        && !observation.timestamp().isAfter(now)
                        && observation.quality() >= 0.7);
    }

    private boolean hasObstructionAfterLatestClear(List<Observation> sectionObservations, Instant now) {
        Instant latestClear = sectionObservations.stream()
                .filter(observation -> observation.observationType() == ObservationType.ROAD_CLEAR
                        || observation.observationType() == ObservationType.NO_OBSTRUCTION)
                .map(Observation::timestamp)
                .max(Instant::compareTo)
                .orElse(Instant.MIN);
        return sectionObservations.stream().anyMatch(observation ->
                observation.observationType() == ObservationType.OBSTACLE
                        && observation.timestamp().isAfter(latestClear)
                        && !observation.timestamp().isAfter(now)
                        && observation.quality() >= 0.6);
    }

    private boolean hasRecentNormalEvidence(String sectionId, Instant now) {
        var start = now.minus(thresholds.evidenceWindow());
        return observations.stream().anyMatch(observation -> observation.roadSectionId().equals(sectionId)
                && !observation.timestamp().isBefore(start)
                && (observation.observationType() == ObservationType.NORMAL_TRAFFIC
                || observation.observationType() == ObservationType.NORMAL_PASSAGE));
    }

    private List<Observation> observationsFor(String sectionId) {
        return observations.stream()
                .filter(observation -> observation.roadSectionId().equals(sectionId))
                .toList();
    }

    private void transition(RoadSection section, SafetyState next, Instant timestamp, String detail) {
        SafetyState current = section.getSafetyState();
        if (current == next) {
            return;
        }
        if (!stateMachine.canTransition(current, next)) {
            throw new IllegalStateException("Invalid safety-state transition: " + current + " -> " + next);
        }
        section.setSafetyState(next);
        addHistory(timestamp, section.getSectionId(), "STATE_TRANSITION", detail, current, next);
    }

    private void addHistory(Instant timestamp, String sectionId, String category, String detail,
                            SafetyState before, SafetyState after) {
        history.add(new IncidentHistory(timestamp, sectionId, category, detail, before, after));
    }

    private boolean sameWarning(Warning first, Warning second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.role() == second.role() && first.level() == second.level()
                && first.heading().equals(second.heading()) && first.message().equals(second.message());
    }

    private RoadSection section(String sectionId) {
        return sections.stream()
                .filter(item -> item.getSectionId().equals(sectionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown road section: " + sectionId));
    }

    public void reset() {
        RoadSafetySystem fresh = new RoadSafetySystem(thresholds);
        sections.clear();
        sections.addAll(fresh.sections);
        units.clear();
        units.putAll(fresh.units);
        observations.clear();
        incidents.clear();
        activeIncidentsBySection.clear();
        history.clear();
        previousRoles.clear();
        incidentSequence = 0;
    }

    public List<RoadSection> getSections() { return List.copyOf(sections); }
    public List<RoadsideUnit> getRoadsideUnits() { return List.copyOf(units.values()); }
    public List<Observation> getObservations() { return List.copyOf(observations); }
    public List<Incident> getIncidents() { return List.copyOf(incidents.values()); }
    public List<Incident> getActiveIncidents() { return List.copyOf(activeIncidentsBySection.values()); }
    public List<IncidentHistory> getHistory() { return List.copyOf(history); }

    public edu.roadsafety.model.SectionHistoryProfile getSectionHistoryProfile(String sectionId) {
        List<Observation> sectionObservations = observationsFor(sectionId);
        List<IncidentHistory> sectionHistory = history.stream()
                .filter(entry -> entry.roadSectionId().equals(sectionId))
                .toList();
        int abnormalStops = (int) sectionObservations.stream()
                .filter(observation -> observation.observationType() == ObservationType.ABNORMAL_STOP)
                .count();
        int confirmedIncidents = (int) sectionHistory.stream()
                .filter(entry -> entry.category().equals("INCIDENT_CONFIRMED"))
                .count();
        int continuingHazards = (int) sectionHistory.stream()
                .filter(entry -> entry.stateAfter() == SafetyState.CONTINUING_HAZARD)
                .count();
        int recoveries = (int) sectionHistory.stream()
                .filter(entry -> entry.stateAfter() == SafetyState.RECOVERY)
                .count();
        int falsePositives = (int) sectionHistory.stream()
                .filter(entry -> entry.category().equals("FALSE_POSITIVE"))
                .count();
        int contradictory = (int) sectionHistory.stream()
                .filter(entry -> entry.category().equals("EVIDENCE_CONTRADICTION"))
                .count();
        return new edu.roadsafety.model.SectionHistoryProfile(sectionId, sectionObservations.size(), abnormalStops,
                confirmedIncidents, continuingHazards, recoveries, falsePositives, contradictory,
                section(sectionId).getMonitoringAttentionLevel());
    }
}

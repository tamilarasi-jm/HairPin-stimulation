package edu.roadsafety.simulation;

import edu.roadsafety.enums.ObservationType;
import edu.roadsafety.enums.SafetyState;
import edu.roadsafety.model.Incident;
import edu.roadsafety.model.IncidentHistory;
import edu.roadsafety.model.Observation;
import edu.roadsafety.model.SectionHistoryProfile;
import edu.roadsafety.service.RoadSafetySystem;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class HistoricalEvolutionService {
    private final List<Observation> observations = new ArrayList<>();
    private final List<IncidentHistory> history = new ArrayList<>();
    private final Map<String, Set<String>> verifiedIncidentScenariosBySection = new HashMap<>();

    public synchronized void recordScenario(Scenario scenario, RoadSafetySystem system) {
        observations.addAll(system.getObservations());
        history.addAll(system.getHistory());
        for (Incident incident : system.getIncidents()) {
            Set<String> signalTypes = new HashSet<>();
            Set<String> groups = new HashSet<>();
            for (Observation observation : incident.getEvidence()) {
                if (observation.observationType() == ObservationType.IMPACT
                        || observation.observationType() == ObservationType.LOSS_OF_CONTROL) {
                    signalTypes.add(observation.observationType().name());
                }
                if (!observation.evidenceGroup().isBlank()) {
                    groups.add(observation.evidenceGroup());
                }
            }
            if (!signalTypes.isEmpty() && groups.size() >= 2) {
                String scenarioId = incident.getEvidence().stream()
                        .map(Observation::scenarioId)
                        .findFirst()
                        .orElse(scenario.type().name());
                verifiedIncidentScenariosBySection
                        .computeIfAbsent(incident.getRoadSectionId(), ignored -> new HashSet<>())
                        .add(scenarioId);
            }
        }
    }

    public synchronized SectionHistoryProfile getProfile(String sectionId) {
        List<Observation> sectionObservations = observations.stream()
                .filter(observation -> observation.roadSectionId().equals(sectionId))
                .toList();
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
        int contradictions = (int) sectionHistory.stream()
                .filter(entry -> entry.category().equals("EVIDENCE_CONTRADICTION"))
                .count();
        int attention = verifiedIncidentScenariosBySection.getOrDefault(sectionId, Set.of()).size() >= 2 ? 1 : 0;
        return new SectionHistoryProfile(sectionId, sectionObservations.size(), abnormalStops,
                confirmedIncidents, continuingHazards, recoveries, falsePositives, contradictions, attention);
    }

    public synchronized List<Observation> getObservations(String sectionId) {
        return observations.stream()
                .filter(observation -> observation.roadSectionId().equals(sectionId))
                .toList();
    }

    public synchronized List<IncidentHistory> getHistory(String sectionId) {
        return history.stream()
                .filter(entry -> entry.roadSectionId().equals(sectionId))
                .toList();
    }

    public synchronized List<Observation> getAllObservations() {
        return List.copyOf(observations);
    }
}

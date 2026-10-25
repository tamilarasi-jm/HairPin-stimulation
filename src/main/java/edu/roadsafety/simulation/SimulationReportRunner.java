package edu.roadsafety.simulation;

import edu.roadsafety.enums.ObservationType;
import edu.roadsafety.enums.SafetyState;
import edu.roadsafety.model.Incident;
import edu.roadsafety.model.Observation;
import edu.roadsafety.model.RoadSection;
import edu.roadsafety.service.RoadSafetySystem;

import java.io.IOException;
import java.io.PrintStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class SimulationReportRunner {
    private final ScenarioRunner scenarioRunner = new ScenarioRunner();
    private final HistoricalEvolutionService historyService = new HistoricalEvolutionService();

    public void runAll(PrintStream output) throws IOException {
        for (ScenarioType type : ScenarioType.values()) {
            RoadSafetySystem system = new RoadSafetySystem();
            Scenario scenario = scenarioRunner.getScenario(type);
            scenarioRunner.run(type, system, (event, result) -> { });
            historyService.recordScenario(scenario, system);
            printReport(output, scenario, system);
        }
        output.println("Cumulative synthetic observations recorded: " + historyService.getAllObservations().size());
    }

    private void printReport(PrintStream output, Scenario scenario, RoadSafetySystem system) {
        List<Incident> incidents = system.getIncidents();
        Set<String> affectedSections = incidents.stream()
                .map(Incident::getRoadSectionId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (affectedSections.isEmpty()) {
            system.getSections().stream()
                    .filter(section -> section.getSafetyState() != SafetyState.NORMAL)
                    .map(RoadSection::getSectionId)
                    .forEach(affectedSections::add);
        }
        String finalState = affectedSections.isEmpty() ? "NORMAL" : affectedSections.stream()
                .map(sectionId -> sectionId + "=" + findSection(system, sectionId).getSafetyState())
                .collect(Collectors.joining(", "));
        String incidentTypes = incidents.isEmpty() ? "none" : incidents.stream()
                .map(incident -> incident.getIncidentType() + " at " + incident.getRoadSectionId())
                .collect(Collectors.joining(", "));
        List<Observation> evidence = incidents.stream()
                .flatMap(incident -> incident.getEvidence().stream())
                .toList();
        if (evidence.isEmpty()) {
            evidence = system.getObservations();
        }
        String evidenceTypes = evidence.stream()
                .map(Observation::observationType)
                .distinct()
                .map(ObservationType::name)
                .collect(Collectors.joining(" + "));
        String roles = system.getRoadsideUnits().stream()
                .map(unit -> unit.unitId() + ": " + unit.role())
                .collect(Collectors.joining(", "));
        boolean recoveryAchieved = system.getHistory().stream()
                .anyMatch(entry -> entry.stateAfter() == SafetyState.RECOVERY);
        output.println("Scenario: " + scenario.type().getDatasetName());
        output.println("Final state: " + finalState);
        output.println("Incident detected: " + incidentTypes);
        output.println("Evidence used: " + (evidenceTypes.isBlank() ? "none" : evidenceTypes));
        output.println("Affected section: " + (affectedSections.isEmpty() ? "none" : String.join(", ", affectedSections)));
        output.println("RU roles: " + roles);
        output.println("Recovery achieved: " + (recoveryAchieved ? "yes" : "no"));
        output.println("Number of observations processed: " + system.getObservations().size());
        output.println();
    }

    private RoadSection findSection(RoadSafetySystem system, String sectionId) {
        return system.getSections().stream()
                .filter(section -> section.getSectionId().equals(sectionId))
                .findFirst()
                .orElseThrow();
    }
}

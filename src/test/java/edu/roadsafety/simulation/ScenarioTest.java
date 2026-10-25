package edu.roadsafety.simulation;

import edu.roadsafety.enums.OperationalRole;
import edu.roadsafety.enums.ObservationType;
import edu.roadsafety.enums.SafetyState;
import edu.roadsafety.model.Observation;
import edu.roadsafety.model.RoadSection;
import edu.roadsafety.service.RoadSafetySystem;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioTest {
    private final ScenarioRunner runner = new ScenarioRunner();

    @Test
    void loaderParsesDatasetAndPreservesEvaluationOnlyTruthLabel() throws IOException {
        List<Observation> observations = new SyntheticObservationLoader().loadAll();
        assertEquals(490, observations.size());
        Observation first = observations.get(0);
        assertEquals("OBS-0001", first.observationId());
        assertEquals("V0101", first.vehicleId());
        assertEquals("NORM-01-E1", first.evidenceGroup());
        assertEquals("False", first.groundTruthEvent());
        assertEquals(0.8, first.quality());
    }

    @Test
    void groundTruthLabelDoesNotChangeSimulationDecisions() throws IOException {
        List<Observation> loaded = new SyntheticObservationLoader().loadScenario(ScenarioType.NORMAL_TRAFFIC);
        RoadSafetySystem originalSystem = new RoadSafetySystem();
        RoadSafetySystem relabeledSystem = new RoadSafetySystem();
        for (Observation observation : loaded) {
            originalSystem.processObservation(observation);
            relabeledSystem.processObservation(new Observation(observation.observationId(), observation.scenarioId(),
                    observation.scenarioType(), observation.timestamp(), observation.roadsideUnitId(),
                    observation.roadSectionId(), observation.vehicleId(), observation.observationType(),
                    observation.direction(), observation.speedKmh(), observation.stoppingDurationSeconds(),
                    observation.impactDetected(), observation.obstacleDetected(), observation.weatherCondition(),
                    observation.roadCondition(), observation.quality(), "True", observation.evidenceGroup()));
        }
        assertEquals(originalSystem.getSections().stream().map(RoadSection::getSafetyState).toList(),
                relabeledSystem.getSections().stream().map(RoadSection::getSafetyState).toList());
    }

    @Test
    void normalTrafficLeavesSectionsNormal() throws IOException {
        RoadSafetySystem system = run(ScenarioType.NORMAL_TRAFFIC);
        assertTrue(system.getSections().stream().allMatch(section -> section.getSafetyState() == SafetyState.NORMAL));
    }

    @Test
    void abnormalStopDoesNotConfirmIncident() throws IOException {
        RoadSafetySystem system = run(ScenarioType.ABNORMAL_STOP);
        assertTrue(sectionState(system, "S3") == SafetyState.ABNORMAL
                || sectionState(system, "S3") == SafetyState.INCIDENT_CONFIRMATION);
        assertTrue(system.getActiveIncidents().isEmpty());
    }

    @Test
    void corroboratedCollisionConfirmsAndDifferentiatesRoles() throws IOException {
        RoadSafetySystem system = run(ScenarioType.CONFIRMED_COLLISION);
        assertEquals(SafetyState.CONTINUING_HAZARD, sectionState(system, "S3"));
        assertTrue(system.getHistory().stream().anyMatch(entry ->
                entry.stateAfter() == SafetyState.CONFIRMED_INCIDENT));
        assertEquals(OperationalRole.EARLY_APPROACH_WARNING, section(system, "S1").getRole());
        assertEquals(OperationalRole.IMMEDIATE_HAZARD_WARNING, section(system, "S2").getRole());
        assertEquals(OperationalRole.INCIDENT_AWARENESS, section(system, "S3").getRole());
        assertEquals(OperationalRole.CONTINUING_HAZARD_MONITORING, section(system, "S4").getRole());
        assertEquals(1, system.getActiveIncidents().size());
    }

    @Test
    void isolatedWeakObservationIsNotConfirmedAndLaterNormalEvidenceResolvesIt() throws IOException {
        RoadSafetySystem system = run(ScenarioType.FALSE_POSITIVE);
        assertTrue(system.getActiveIncidents().isEmpty());
        assertEquals(SafetyState.NORMAL, sectionState(system, "S3"));
        assertTrue(system.getHistory().stream().anyMatch(entry -> entry.category().equals("FALSE_POSITIVE")));
    }

    @Test
    void contradictoryEvidenceRemainsUnderConfirmation() throws IOException {
        RoadSafetySystem system = run(ScenarioType.CONTRADICTORY_DATA);
        assertEquals(SafetyState.INCIDENT_CONFIRMATION, sectionState(system, "S3"));
        assertTrue(system.getActiveIncidents().isEmpty());
    }

    @Test
    void singleVehicleIncidentCanBeConfirmed() throws IOException {
        RoadSafetySystem system = run(ScenarioType.SINGLE_VEHICLE_INCIDENT);
        assertEquals(SafetyState.CONFIRMED_INCIDENT, sectionState(system, "S3"));
    }

    @Test
    void obstructionKeepsIncidentAsContinuingHazard() throws IOException {
        RoadSafetySystem system = run(ScenarioType.CONTINUING_HAZARD);
        assertEquals(SafetyState.CONTINUING_HAZARD, sectionState(system, "S3"));
    }

    @Test
    void recoveryNeedsPositiveEvidenceAndClosesIncident() throws IOException {
        RoadSafetySystem system = run(ScenarioType.RECOVERY);
        assertEquals(SafetyState.NORMAL, sectionState(system, "S3"));
        assertTrue(system.getActiveIncidents().isEmpty());
        assertTrue(system.getHistory().stream().anyMatch(entry ->
                entry.stateAfter() == SafetyState.RECOVERY));
    }

    @Test
    void incidentsAtDifferentSectionsAreTrackedIndependently() {
        RoadSafetySystem system = new RoadSafetySystem();
        Instant start = Instant.parse("2026-10-24T12:30:00Z");
        for (String section : List.of("S2", "S4")) {
            int unit = Integer.parseInt(section.substring(1));
            String vehicle = "TEST-" + section;
            ObservationType[] types = {
                    ObservationType.VEHICLE_DETECTED,
                    ObservationType.SPEED_CHANGE,
                    ObservationType.ABNORMAL_STOP,
                    ObservationType.IMPACT
            };
            for (int index = 0; index < types.length; index++) {
                String group = index < 2 ? "G1-" + section : "G2-" + section;
                Instant timestamp = start.plusMillis((unit * 10L + index) * 250L);
                system.processObservation(new Observation("TEST-" + section + "-" + index,
                        "TEST-SCENARIO-" + section, "TEST", timestamp, "RU-" + unit, section, vehicle,
                        types[index], "UPHILL", 0, 0, types[index] == ObservationType.IMPACT,
                        false, "CLEAR", "DRY", 0.95, "evaluation-only", group));
            }
        }
        assertEquals(SafetyState.CONFIRMED_INCIDENT, sectionState(system, "S2"));
        assertEquals(SafetyState.CONFIRMED_INCIDENT, sectionState(system, "S4"));
        assertEquals(2, system.getActiveIncidents().size());
    }

    @Test
    void staleEvidenceCreatesObservationDeficiency() throws IOException {
        RoadSafetySystem system = run(ScenarioType.STALE_EVIDENCE);
        assertEquals(SafetyState.OBSERVATION_DEFICIENCY, sectionState(system, "S3"));
        assertTrue(system.getActiveIncidents().isEmpty());
    }

    @Test
    void reverseDirectionChangesWhichConnectedUnitIsUpstream() {
        RoadSafetySystem system = new RoadSafetySystem();
        Instant start = Instant.parse("2026-10-24T12:30:00Z");
        ObservationType[] types = {
                ObservationType.VEHICLE_DETECTED,
                ObservationType.SPEED_CHANGE,
                ObservationType.ABNORMAL_STOP,
                ObservationType.IMPACT
        };
        for (int index = 0; index < types.length; index++) {
            Instant timestamp = start.plusMillis(index * 250L);
            String group = index < 2 ? "REVERSE-E1" : "REVERSE-E2";
            system.processObservation(new Observation("REVERSE-" + index, "REVERSE-TEST", "TEST",
                    timestamp, "RU-3", "S3", "VEHICLE-1", types[index], "REVERSE", 30, 0,
                    types[index] == ObservationType.IMPACT, false, "CLEAR", "DRY", 0.95,
                    "evaluation-only", group));
        }
        assertEquals(SafetyState.CONFIRMED_INCIDENT, sectionState(system, "S3"));
        assertEquals(OperationalRole.IMMEDIATE_HAZARD_WARNING, section(system, "S4").getRole());
        assertEquals(OperationalRole.EARLY_APPROACH_WARNING, section(system, "S5").getRole());
        assertEquals(OperationalRole.CONTINUING_HAZARD_MONITORING, section(system, "S2").getRole());
    }

    @Test
    void replayEngineProcessesRowsSequentiallyAndCanPauseAndResume() throws Exception {
        List<Observation> observations = new SyntheticObservationLoader()
                .loadScenario(ScenarioType.NORMAL_TRAFFIC);
        List<String> processedIds = new ArrayList<>();
        CountDownLatch complete = new CountDownLatch(1);
        try (ObservationReplayEngine engine = new ObservationReplayEngine(Duration.ofMillis(20))) {
            engine.start(observations, observation -> processedIds.add(observation.observationId()),
                    complete::countDown, failure -> complete.countDown());
            engine.pause();
            assertTrue(engine.isPaused());
            engine.resume();
            assertTrue(complete.await(3, TimeUnit.SECONDS));
        }
        assertEquals(observations.stream().map(Observation::observationId).toList(), processedIds);
    }

    @Test
    void historicalProfilesAreQueryableAndPriorityRequiresRepeatedVerifiedIncidents() throws IOException {
        HistoricalEvolutionService history = new HistoricalEvolutionService();
        for (ScenarioType type : List.of(ScenarioType.CONFIRMED_COLLISION,
                ScenarioType.SINGLE_VEHICLE_INCIDENT)) {
            RoadSafetySystem system = run(type);
            history.recordScenario(runner.getScenario(type), system);
        }
        var profile = history.getProfile("S3");
        assertTrue(profile.totalObservations() > 0);
        assertTrue(profile.confirmedIncidents() >= 2);
        assertEquals(1, profile.monitoringAttentionLevel());
        assertFalse(history.getObservations("S3").isEmpty());
    }

    private RoadSafetySystem run(ScenarioType type) throws IOException {
        RoadSafetySystem system = new RoadSafetySystem();
        runner.run(type, system, (event, result) -> { });
        return system;
    }

    private SafetyState sectionState(RoadSafetySystem system, String sectionId) {
        return section(system, sectionId).getSafetyState();
    }

    private edu.roadsafety.model.RoadSection section(RoadSafetySystem system, String sectionId) {
        return system.getSections().stream()
                .filter(section -> section.getSectionId().equals(sectionId))
                .findFirst()
                .orElseThrow();
    }
}

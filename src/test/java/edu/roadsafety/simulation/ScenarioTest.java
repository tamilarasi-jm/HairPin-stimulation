package edu.roadsafety.simulation;

import edu.roadsafety.enums.OperationalRole;
import edu.roadsafety.enums.EvidenceStatus;
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
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void courseEvidenceScoreIsNormalizedAndCorroboratesActualObservations() {
        RoadSafetySystem isolatedSystem = new RoadSafetySystem();
        var isolated = isolatedSystem.processObservation(observation("STOP", ObservationType.ABNORMAL_STOP,
                Instant.parse("2026-10-24T12:00:00Z"), "E1"));
        assertEquals(40.5, isolated.evidence().score());
        assertEquals(EvidenceStatus.ABNORMAL, isolated.evidence().status());
        assertEquals(SafetyState.ABNORMAL, sectionState(isolatedSystem, "S3"));
        assertTrue(isolatedSystem.getActiveIncidents().isEmpty());

        RoadSafetySystem corroboratingSystem = new RoadSafetySystem();
        ObservationType[] types = {
                ObservationType.VEHICLE_DETECTED,
                ObservationType.SPEED_CHANGE,
                ObservationType.ABNORMAL_STOP,
                ObservationType.IMPACT,
                ObservationType.OBSTACLE
        };
        double previousScore = 0;
        double finalScore = 0;
        for (int index = 0; index < types.length; index++) {
            String group = index < 2 ? "E1" : "E2";
            var result = corroboratingSystem.processObservation(observation("CORROBORATION-" + index,
                    types[index], Instant.parse("2026-10-24T12:01:00Z").plusSeconds(index), group));
            finalScore = result.evidence().score();
            assertTrue(finalScore > previousScore);
            assertTrue(finalScore <= 100);
            previousScore = finalScore;
        }
        assertEquals(100, finalScore);
        assertEquals(SafetyState.CONTINUING_HAZARD, sectionState(corroboratingSystem, "S3"));
        assertEquals(1, corroboratingSystem.getActiveIncidents().size());
    }

    @Test
    void matchingNormalObservationReducesCourseEvidenceScore() {
        RoadSafetySystem system = new RoadSafetySystem();
        Instant start = Instant.parse("2026-10-24T12:02:00Z");
        var abnormal = system.processObservation(observation("STOP-CONFLICT", ObservationType.ABNORMAL_STOP,
                start, "E1"));
        var normal = system.processObservation(observation("NORMAL-CONFLICT", ObservationType.NORMAL_PASSAGE,
                start.plusSeconds(1), "E1"));
        assertTrue(normal.evidence().score() < abnormal.evidence().score());
        assertEquals(EvidenceStatus.CONTRADICTORY, normal.evidence().status());
        assertTrue(normal.evidence().score() >= 0);
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
    void requestedScenariosExposeProgressAndExpectedFinalStatus() throws IOException {
        for (ScenarioType type : List.of(ScenarioType.NORMAL_TRAFFIC, ScenarioType.ABNORMAL_STOP,
                ScenarioType.CONFIRMED_COLLISION, ScenarioType.FALSE_POSITIVE,
                ScenarioType.CONTRADICTORY_DATA, ScenarioType.CONTINUING_HAZARD, ScenarioType.RECOVERY)) {
            RoadSafetySystem system = new RoadSafetySystem();
            List<Double> s3Scores = new ArrayList<>();
            Map<ObservationType, Double> collisionSignalScores = new LinkedHashMap<>();
            runner.run(type, system, (event, result) -> {
                if (event.observation().roadSectionId().equals("S3")) {
                    s3Scores.add(result.evidence().score());
                    if (type == ScenarioType.CONFIRMED_COLLISION
                            && EnumSet.of(ObservationType.VEHICLE_DETECTED, ObservationType.SPEED_CHANGE,
                            ObservationType.ABNORMAL_STOP, ObservationType.IMPACT, ObservationType.OBSTACLE)
                            .contains(event.observation().observationType())) {
                        collisionSignalScores.putIfAbsent(event.observation().observationType(),
                                result.evidence().score());
                    }
                }
            });
            RoadSection s3 = section(system, "S3");
            double finalScore = s3.getLatestEvidence() == null ? 0 : s3.getLatestEvidence().score();
            EvidenceStatus evidenceStatus = s3.getLatestEvidence() == null
                    ? EvidenceStatus.INSUFFICIENT : s3.getLatestEvidence().status();
            boolean activeIncident = system.getActiveIncidents().stream()
                    .anyMatch(incident -> incident.getRoadSectionId().equals("S3"));
            boolean recoveryAchieved = system.getHistory().stream()
                    .anyMatch(entry -> entry.roadSectionId().equals("S3")
                            && entry.stateAfter() == SafetyState.RECOVERY);
            long contradictions = system.getHistory().stream()
                    .filter(entry -> entry.roadSectionId().equals("S3")
                            && entry.category().equals("EVIDENCE_CONTRADICTION"))
                    .count();

            System.out.printf("SCENARIO %s | state=%s | S3 score=%.1f | active incident=%s"
                            + " | evidence=%s | contradictions=%d | affected section=%s"
                            + " | roles=%s | recovery=%s",
                    type.getDatasetName(), s3.getSafetyState(), finalScore, activeIncident, evidenceStatus,
                    contradictions,
                    s3.getSafetyState() == SafetyState.NORMAL && type == ScenarioType.NORMAL_TRAFFIC
                            ? "none" : "S3",
                    system.getRoadsideUnits().stream().map(unit -> unit.unitId() + "=" + unit.role()).toList(),
                    recoveryAchieved);
            if (type == ScenarioType.CONFIRMED_COLLISION) {
                System.out.print(" | replay signal scores=" + collisionSignalScores);
            }
            System.out.println();

            switch (type) {
                case NORMAL_TRAFFIC -> {
                    assertEquals(SafetyState.NORMAL, s3.getSafetyState());
                    assertTrue(system.getActiveIncidents().isEmpty());
                }
                case ABNORMAL_STOP -> {
                    assertTrue(EnumSet.of(SafetyState.ABNORMAL, SafetyState.INCIDENT_CONFIRMATION)
                            .contains(s3.getSafetyState()));
                    assertTrue(system.getActiveIncidents().isEmpty());
                }
                case CONFIRMED_COLLISION -> {
                    assertTrue(s3Scores.size() > 3);
                    List<ObservationType> progression = List.of(ObservationType.VEHICLE_DETECTED,
                            ObservationType.SPEED_CHANGE, ObservationType.ABNORMAL_STOP,
                            ObservationType.IMPACT, ObservationType.OBSTACLE);
                    double priorSignalScore = -1;
                    for (ObservationType signal : progression) {
                        double signalScore = collisionSignalScores.get(signal);
                        assertTrue(signalScore > priorSignalScore,
                                signal + " should increase the score during collision replay");
                        priorSignalScore = signalScore;
                    }
                    assertEquals(SafetyState.CONTINUING_HAZARD, s3.getSafetyState());
                    assertEquals(OperationalRole.EARLY_APPROACH_WARNING, section(system, "S1").getRole());
                    assertEquals(OperationalRole.IMMEDIATE_HAZARD_WARNING, section(system, "S2").getRole());
                    assertEquals(OperationalRole.INCIDENT_AWARENESS, s3.getRole());
                    assertEquals(OperationalRole.CONTINUING_HAZARD_MONITORING, section(system, "S4").getRole());
                    assertEquals(OperationalRole.NORMAL_MONITORING, section(system, "S5").getRole());
                    assertTrue(activeIncident);
                }
                case FALSE_POSITIVE -> {
                    assertTrue(s3Scores.stream().anyMatch(score -> score > 0),
                            "false-positive evidence should rise temporarily");
                    assertEquals(SafetyState.NORMAL, s3.getSafetyState());
                    assertFalse(activeIncident);
                }
                case CONTRADICTORY_DATA -> {
                    assertTrue(s3Scores.stream().anyMatch(score -> score > 0));
                    assertEquals(SafetyState.INCIDENT_CONFIRMATION, s3.getSafetyState());
                    assertFalse(activeIncident);
                    assertTrue(s3.getLatestEvidence().status() == EvidenceStatus.CONTRADICTORY
                                    || system.getHistory().stream().anyMatch(entry ->
                                    entry.category().equals("EVIDENCE_CONTRADICTION")
                                            && entry.roadSectionId().equals("S3")),
                            "contradiction must remain visible in evidence or history");
                }
                case CONTINUING_HAZARD -> {
                    assertEquals(SafetyState.CONTINUING_HAZARD, s3.getSafetyState());
                    assertTrue(activeIncident);
                }
                case RECOVERY -> {
                    assertTrue(recoveryAchieved);
                    assertEquals(SafetyState.NORMAL, s3.getSafetyState());
                    assertFalse(activeIncident);
                    assertEquals(ObservationType.ROAD_CLEAR, system.getObservations().stream()
                            .filter(observation -> observation.roadSectionId().equals("S3"))
                            .reduce((first, second) -> second)
                            .orElseThrow().observationType());
                    assertNull(s3.getWarning());
                }
                default -> throw new IllegalStateException("Unexpected validated scenario: " + type);
            }
        }
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

    private Observation observation(String id, ObservationType type, Instant timestamp, String evidenceGroup) {
        return new Observation(id, "COURSE-SCORE-TEST", "TEST", timestamp, "RU-3", "S3", "TEST-VEHICLE",
                type, "UPHILL", 35, type == ObservationType.ABNORMAL_STOP ? 15 : 0,
                type == ObservationType.IMPACT, type == ObservationType.OBSTACLE,
                "CLEAR", "DRY", 0.9, "evaluation-only", evidenceGroup);
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

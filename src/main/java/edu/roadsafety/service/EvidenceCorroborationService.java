package edu.roadsafety.service;

import edu.roadsafety.enums.EvidenceStatus;
import edu.roadsafety.enums.ObservationType;
import edu.roadsafety.model.EvidenceResult;
import edu.roadsafety.model.Observation;
import edu.roadsafety.model.ThresholdConfig;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EvidenceCorroborationService {
    private static final Set<ObservationType> SUPPORTING_TYPES = EnumSet.of(
            ObservationType.VEHICLE_DETECTED,
            ObservationType.SPEED_CHANGE,
            ObservationType.ABNORMAL_STOP,
            ObservationType.IMPACT,
            ObservationType.OBSTACLE,
            ObservationType.LOSS_OF_CONTROL);
    private static final Set<ObservationType> CONTRADICTORY_TYPES = EnumSet.of(
            ObservationType.NORMAL_PASSAGE,
            ObservationType.NORMAL_TRAFFIC);

    private final ThresholdConfig thresholds;
    private final EvidenceScoringStrategy scoringStrategy;

    public EvidenceCorroborationService(ThresholdConfig thresholds) {
        this(thresholds, new CourseEvidenceScoringStrategy());
    }

    public EvidenceCorroborationService(ThresholdConfig thresholds, EvidenceScoringStrategy scoringStrategy) {
        this.thresholds = thresholds;
        this.scoringStrategy = scoringStrategy;
    }

    public EvidenceResult evaluate(String sectionId, List<Observation> observations, Observation trigger,
                                   Instant evaluationTime, Set<String> connectedSectionIds) {
        if (trigger.observationType() == ObservationType.OBSERVATION_GAP
                || trigger.quality() < CourseEvidenceScoringConfig.MINIMUM_TRIGGER_QUALITY) {
            return new EvidenceResult(0, EvidenceStatus.DEGRADED, List.of(), List.of(),
                    "The latest simulated observation is missing or below the configured quality floor");
        }

        List<Observation> sectionObservations = observations.stream()
                .filter(observation -> observation.roadSectionId().equals(sectionId))
                .toList();
        Instant latestForSection = sectionObservations.stream()
                .map(Observation::timestamp)
                .max(Instant::compareTo)
                .orElse(null);

        if (latestForSection == null || latestForSection.plus(thresholds.staleAfter()).isBefore(evaluationTime)) {
            return new EvidenceResult(0, EvidenceStatus.STALE, List.of(), List.of(),
                    "No sufficiently recent observation is available for " + sectionId);
        }

        Instant windowStart = evaluationTime.minus(thresholds.evidenceWindow());
        List<Observation> relevant = observations.stream()
                .filter(observation -> !observation.timestamp().isBefore(windowStart)
                        && !observation.timestamp().isAfter(evaluationTime)
                        && sameVehicleAndDirection(trigger, observation)
                        && (observation.roadSectionId().equals(sectionId)
                        || connectedSectionIds.contains(observation.roadSectionId())))
                .toList();

        Map<String, Observation> strongestByTypeAndGroup = new LinkedHashMap<>();
        List<Observation> contradictoryObservations = relevant.stream()
                .filter(observation -> CONTRADICTORY_TYPES.contains(observation.observationType())
                        && observation.quality() >= CourseEvidenceScoringConfig.MINIMUM_CONTRADICTION_QUALITY)
                .toList();
        Set<ObservationType> distinctSupport = EnumSet.noneOf(ObservationType.class);
        Set<String> evidenceGroups = new java.util.HashSet<>();

        for (Observation observation : relevant) {
            if (SUPPORTING_TYPES.contains(observation.observationType())
                    && observation.quality() >= CourseEvidenceScoringConfig.MINIMUM_SUPPORTING_QUALITY) {
                String evidenceGroup = observation.evidenceGroup().isBlank()
                        ? observation.observationId() : observation.evidenceGroup();
                String key = observation.observationType() + ":" + evidenceGroup;
                strongestByTypeAndGroup.merge(key, observation,
                        (first, second) -> first.quality() >= second.quality() ? first : second);
                distinctSupport.add(observation.observationType());
                if (!observation.evidenceGroup().isBlank()) {
                    evidenceGroups.add(observation.evidenceGroup());
                }
            }
        }

        double score = strongestByTypeAndGroup.values().stream()
                .mapToDouble(observation -> scoringStrategy.weightFor(observation.observationType())
                        * observation.quality())
                .sum();
        List<String> supporting = strongestByTypeAndGroup.values().stream()
                .sorted(Comparator.comparing(Observation::timestamp))
                .map(observation -> observation.observationType() + " at " + observation.roadSectionId()
                        + (observation.vehicleId().isBlank() ? "" : " (" + observation.vehicleId() + ")"))
                .toList();
        boolean abnormalSignalPresent = distinctSupport.stream()
                .anyMatch(type -> type != ObservationType.VEHICLE_DETECTED);
        List<String> contradictions = abnormalSignalPresent
                ? contradictoryObservations.stream()
                .map(observation -> observation.observationType() + " at " + observation.roadSectionId())
                .toList()
                : List.of();

        if (!contradictions.isEmpty() && !supporting.isEmpty()) {
            score = CourseEvidenceScoringConfig.clampScore(score - contradictoryObservations.stream()
                    .mapToDouble(observation -> CourseEvidenceScoringConfig.CONTRADICTION_PENALTY
                            * observation.quality()).sum());
            return new EvidenceResult(score, EvidenceStatus.CONTRADICTORY, supporting, contradictions,
                    "Recent normal-passage evidence conflicts with abnormal observations for the same vehicle and direction");
        }
        score = CourseEvidenceScoringConfig.clampScore(score);
        if (supporting.isEmpty() || score < thresholds.abnormalScore()) {
            return new EvidenceResult(score, EvidenceStatus.INSUFFICIENT, supporting, contradictions,
                    "Available observations do not reach the abnormal-evidence threshold");
        }
        boolean criticalEvidence = distinctSupport.contains(ObservationType.IMPACT)
                || distinctSupport.contains(ObservationType.LOSS_OF_CONTROL);
        if (score >= thresholds.confirmedScore() && distinctSupport.size() >= 3
                && evidenceGroups.size() >= 2 && criticalEvidence) {
            return new EvidenceResult(score, EvidenceStatus.CONFIRMED, supporting, contradictions,
                    "Distinct, confidence-weighted observations across evidence groups corroborate a hazard");
        }
        if (score >= thresholds.confirmationScore() || criticalEvidence) {
            return new EvidenceResult(score, EvidenceStatus.INCIDENT_CONFIRMATION, supporting, contradictions,
                    "Evidence warrants incident confirmation but is not yet sufficient to confirm");
        }
        return new EvidenceResult(score, EvidenceStatus.ABNORMAL, supporting, contradictions,
                "Abnormal evidence is present without sufficient corroboration");
    }

    private boolean sameVehicleAndDirection(Observation trigger, Observation candidate) {
        boolean sameVehicle = trigger.vehicleId().isBlank() || candidate.vehicleId().isBlank()
                || trigger.vehicleId().equals(candidate.vehicleId());
        boolean sameDirection = trigger.direction().equalsIgnoreCase(candidate.direction());
        return sameVehicle && sameDirection;
    }
}

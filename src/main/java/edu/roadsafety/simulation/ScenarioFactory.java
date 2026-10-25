package edu.roadsafety.simulation;

import edu.roadsafety.model.Observation;

import java.io.IOException;
import java.util.List;

public final class ScenarioFactory {
    private final SyntheticObservationLoader loader;

    public ScenarioFactory() {
        this(new SyntheticObservationLoader());
    }

    public ScenarioFactory(SyntheticObservationLoader loader) {
        this.loader = loader;
    }

    public Scenario create(ScenarioType type) throws IOException {
        List<Observation> observations = loader.loadScenario(type);
        String description = description(type);
        if (type == ScenarioType.RECOVERY) {
            List<Observation> precedingHazard = loader.loadScenario(ScenarioType.CONTINUING_HAZARD);
            java.util.ArrayList<Observation> combined = new java.util.ArrayList<>(precedingHazard);
            combined.addAll(observations);
            observations = combined.stream()
                    .sorted(java.util.Comparator.comparing(Observation::timestamp)
                            .thenComparing(Observation::observationId))
                    .toList();
            description += " A prior continuing-hazard observation group is replayed first to establish the active context.";
        }
        List<ScenarioEvent> events = observations.stream()
                .map(observation -> new ScenarioEvent(observation, observation.timestamp()))
                .toList();
        return new Scenario(type, description, events);
    }

    private String description(ScenarioType type) {
        return switch (type) {
            case NORMAL_TRAFFIC -> "Synthetic vehicle detections and normal passages.";
            case ABNORMAL_STOP -> "Repeated abnormal stopping and speed-change observations.";
            case CONFIRMED_COLLISION -> "Corroborated vehicle, braking, stop, impact, and obstruction evidence.";
            case FALSE_POSITIVE -> "Abnormal observations followed by normal passage evidence.";
            case CONTRADICTORY_DATA -> "Impact and obstruction indications conflict with normal passage evidence.";
            case SINGLE_VEHICLE_INCIDENT -> "Corroborated loss-of-control evidence for one simulated vehicle.";
            case CONTINUING_HAZARD -> "Incident and repeated road-obstruction observations.";
            case RECOVERY -> "Post-hazard normal passage and road-clearance observations.";
            case MULTIPLE_INCIDENTS -> "Multiple event groups are processed independently by section.";
            case STALE_EVIDENCE -> "Observation gaps and degraded confidence indicate evidence deficiency.";
        };
    }
}

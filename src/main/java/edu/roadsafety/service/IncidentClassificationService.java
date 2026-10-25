package edu.roadsafety.service;

import edu.roadsafety.enums.IncidentType;
import edu.roadsafety.enums.ObservationType;
import edu.roadsafety.model.Observation;

import java.util.List;

public final class IncidentClassificationService implements IncidentClassifier {
    @Override
    public IncidentType classify(List<Observation> evidence) {
        if (evidence.stream().anyMatch(observation -> observation.observationType() == ObservationType.IMPACT)) {
            return IncidentType.COLLISION;
        }
        if (evidence.stream().anyMatch(observation -> observation.observationType() == ObservationType.LOSS_OF_CONTROL)) {
            return IncidentType.LOSS_OF_CONTROL;
        }
        if (evidence.stream().anyMatch(observation -> observation.observationType() == ObservationType.ABNORMAL_STOP)) {
            return IncidentType.ABNORMAL_STOP;
        }
        if (evidence.stream().anyMatch(observation -> observation.observationType() == ObservationType.OBSTACLE)) {
            return IncidentType.ROAD_OBSTRUCTION;
        }
        if (evidence.stream().anyMatch(observation -> observation.observationType() == ObservationType.SPEED_CHANGE)) {
            return IncidentType.LOSS_OF_CONTROL;
        }
        return IncidentType.UNKNOWN;
    }
}

package edu.roadsafety.service;

import edu.roadsafety.enums.ObservationType;

public final class CourseEvidenceScoringStrategy implements EvidenceScoringStrategy {
    @Override
    public double weightFor(ObservationType observationType) {
        return switch (observationType) {
            case VEHICLE_DETECTED -> 0.45;
            case SPEED_CHANGE -> 1.2;
            case ABNORMAL_STOP -> 2.0;
            case IMPACT -> 2.8;
            case OBSTACLE -> 2.0;
            case LOSS_OF_CONTROL -> 2.5;
            default -> 0;
        };
    }
}

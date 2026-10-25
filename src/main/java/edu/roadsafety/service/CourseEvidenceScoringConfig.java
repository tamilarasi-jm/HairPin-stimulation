package edu.roadsafety.service;

import edu.roadsafety.enums.ObservationType;

/**
 * Course-project-only score weights and quality floors; these parameters are not patent-defined.
 */
public final class CourseEvidenceScoringConfig {
    public static final double MINIMUM_SCORE = 0;
    public static final double MAXIMUM_SCORE = 100;
    public static final double CONTRADICTION_PENALTY = 20;
    public static final double MINIMUM_TRIGGER_QUALITY = 0.5;
    public static final double MINIMUM_SUPPORTING_QUALITY = 0.4;
    public static final double MINIMUM_CONTRADICTION_QUALITY = 0.5;

    private CourseEvidenceScoringConfig() {
    }

    public static double weightFor(ObservationType observationType) {
        return switch (observationType) {
            case VEHICLE_DETECTED -> 8;
            case SPEED_CHANGE -> 12;
            case ABNORMAL_STOP -> 45;
            case IMPACT -> 30;
            case OBSTACLE -> 25;
            case LOSS_OF_CONTROL -> 25;
            default -> 0;
        };
    }

    public static double clampScore(double score) {
        return Math.max(MINIMUM_SCORE, Math.min(MAXIMUM_SCORE, score));
    }
}

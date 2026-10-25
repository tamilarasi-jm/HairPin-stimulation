package edu.roadsafety.service;

import edu.roadsafety.enums.ObservationType;

public final class CourseEvidenceScoringStrategy implements EvidenceScoringStrategy {
    @Override
    public double weightFor(ObservationType observationType) {
        return CourseEvidenceScoringConfig.weightFor(observationType);
    }
}

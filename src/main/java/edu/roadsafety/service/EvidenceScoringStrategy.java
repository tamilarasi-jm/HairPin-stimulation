package edu.roadsafety.service;

import edu.roadsafety.enums.ObservationType;

public interface EvidenceScoringStrategy {
    double weightFor(ObservationType observationType);
}

package edu.roadsafety.service;

import edu.roadsafety.model.EvidenceResult;

public record ProcessingResult(
        EvidenceResult evidence,
        long eventProcessingNanos,
        long evidenceProcessingNanos,
        long stateTransitionNanos,
        long roleAssignmentNanos,
        long warningActivationNanos) {
}

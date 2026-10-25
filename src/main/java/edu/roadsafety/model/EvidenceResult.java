package edu.roadsafety.model;

import edu.roadsafety.enums.EvidenceStatus;

import java.util.List;

public record EvidenceResult(
        double score,
        EvidenceStatus status,
        List<String> supportingObservations,
        List<String> contradictions,
        String explanation) {

    public EvidenceResult {
        supportingObservations = List.copyOf(supportingObservations);
        contradictions = List.copyOf(contradictions);
    }
}

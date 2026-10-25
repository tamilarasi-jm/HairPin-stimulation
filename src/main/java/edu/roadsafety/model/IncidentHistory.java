package edu.roadsafety.model;

import edu.roadsafety.enums.SafetyState;

import java.time.Instant;

public record IncidentHistory(
        Instant timestamp,
        String roadSectionId,
        String category,
        String detail,
        SafetyState stateBefore,
        SafetyState stateAfter) {
}

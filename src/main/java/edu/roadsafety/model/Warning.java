package edu.roadsafety.model;

import edu.roadsafety.enums.OperationalRole;
import edu.roadsafety.enums.WarningLevel;

import java.time.Instant;

public record Warning(
        String roadSectionId,
        OperationalRole role,
        WarningLevel level,
        String heading,
        String message,
        Instant activatedAt) {
}

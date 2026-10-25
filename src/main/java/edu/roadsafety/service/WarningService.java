package edu.roadsafety.service;

import edu.roadsafety.enums.OperationalRole;
import edu.roadsafety.enums.WarningLevel;
import edu.roadsafety.model.RoadSection;
import edu.roadsafety.model.Warning;

import java.time.Instant;

public final class WarningService {
    public Warning create(RoadSection section, Instant timestamp) {
        OperationalRole role = section.getRole();
        if (role == OperationalRole.NORMAL_MONITORING) {
            return null;
        }
        return switch (role) {
            case IMMEDIATE_HAZARD_WARNING -> new Warning(section.getSectionId(), role, WarningLevel.HIGH,
                    "IMMEDIATE HAZARD WARNING", "REDUCE SPEED - INCIDENT AHEAD", timestamp);
            case EARLY_APPROACH_WARNING -> new Warning(section.getSectionId(), role, WarningLevel.MEDIUM,
                    "EARLY APPROACH WARNING", "CAUTION - HAZARD AHEAD", timestamp);
            case INCIDENT_AWARENESS -> new Warning(section.getSectionId(), role, WarningLevel.MEDIUM,
                    "INCIDENT AWARENESS", "INCIDENT REPORTED IN THIS SECTION", timestamp);
            case CONTINUING_HAZARD_MONITORING -> new Warning(section.getSectionId(), role, WarningLevel.MEDIUM,
                    "CONTINUING HAZARD MONITORING", "MONITOR FOR SECONDARY HAZARDS", timestamp);
            case RECOVERY_MONITORING -> new Warning(section.getSectionId(), role, WarningLevel.LOW,
                    "RECOVERY MONITORING", "VERIFY ROAD CLEARANCE WITH NEW OBSERVATIONS", timestamp);
            case NORMAL_MONITORING -> null;
        };
    }
}

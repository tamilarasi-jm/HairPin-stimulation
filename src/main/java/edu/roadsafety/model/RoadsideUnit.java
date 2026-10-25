package edu.roadsafety.model;

import edu.roadsafety.enums.OperationalRole;

public record RoadsideUnit(String unitId, String roadSectionId, OperationalRole role) {
    public RoadsideUnit {
        if (unitId == null || unitId.isBlank() || roadSectionId == null || roadSectionId.isBlank() || role == null) {
            throw new IllegalArgumentException("Unit id, section id, and role are required");
        }
    }
}

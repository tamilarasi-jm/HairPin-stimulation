package edu.roadsafety.service;

import edu.roadsafety.enums.OperationalRole;
import edu.roadsafety.enums.SafetyState;
import edu.roadsafety.model.Incident;
import edu.roadsafety.model.RoadSection;

import java.util.List;
import java.util.Map;

public final class RoleAssignmentService {
    public void assign(List<RoadSection> sections, Map<String, Incident> activeIncidents) {
        sections.forEach(section -> section.setRole(OperationalRole.NORMAL_MONITORING));
        for (Incident incident : activeIncidents.values()) {
            if (!incident.isActive()) {
                continue;
            }
            int incidentPosition = sections.stream()
                    .filter(section -> section.getSectionId().equals(incident.getRoadSectionId()))
                    .mapToInt(RoadSection::getPosition)
                    .findFirst()
                    .orElse(-1);
            if (incidentPosition < 0) {
                continue;
            }
            int direction = "REVERSE".equalsIgnoreCase(incident.getDirection()) ? -1 : 1;
            for (RoadSection section : sections) {
                int distance = (incidentPosition - section.getPosition()) * direction;
                if (incident.getSeverity() < 2 && distance > 0) {
                    continue;
                }
                if (distance == 0) {
                    setIfHigherPriority(section, OperationalRole.INCIDENT_AWARENESS);
                } else if (distance == 1) {
                    setIfHigherPriority(section, OperationalRole.IMMEDIATE_HAZARD_WARNING);
                } else if (distance == 2) {
                    setIfHigherPriority(section, OperationalRole.EARLY_APPROACH_WARNING);
                } else if (distance == -1) {
                    setIfHigherPriority(section, OperationalRole.CONTINUING_HAZARD_MONITORING);
                }
            }
        }
        sections.stream()
                .filter(section -> section.getSafetyState() == SafetyState.RECOVERY)
                .forEach(section -> section.setRole(OperationalRole.RECOVERY_MONITORING));
    }

    private void setIfHigherPriority(RoadSection section, OperationalRole role) {
        if (priority(role) > priority(section.getRole())) {
            section.setRole(role);
        }
    }

    private int priority(OperationalRole role) {
        return switch (role) {
            case NORMAL_MONITORING -> 0;
            case CONTINUING_HAZARD_MONITORING -> 1;
            case EARLY_APPROACH_WARNING -> 2;
            case INCIDENT_AWARENESS -> 3;
            case IMMEDIATE_HAZARD_WARNING -> 4;
            case RECOVERY_MONITORING -> 5;
        };
    }
}

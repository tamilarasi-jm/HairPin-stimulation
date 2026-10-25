package edu.roadsafety.model;

import edu.roadsafety.enums.IncidentType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class Incident {
    private final String incidentId;
    private final String roadSectionId;
    private final IncidentType incidentType;
    private final Instant detectedAt;
    private final String direction;
    private Instant confirmedAt;
    private boolean active = true;
    private final List<Observation> evidence = new ArrayList<>();

    public Incident(String incidentId, String roadSectionId, IncidentType incidentType, Instant detectedAt,
                    String direction) {
        this.incidentId = incidentId;
        this.roadSectionId = roadSectionId;
        this.incidentType = incidentType;
        this.detectedAt = detectedAt;
        this.direction = direction == null || direction.isBlank() ? "FORWARD" : direction;
    }

    public String getIncidentId() { return incidentId; }
    public String getRoadSectionId() { return roadSectionId; }
    public IncidentType getIncidentType() { return incidentType; }
    public Instant getDetectedAt() { return detectedAt; }
    public String getDirection() { return direction; }
    public int getSeverity() {
        return switch (incidentType) {
            case COLLISION, ROAD_OBSTRUCTION, RUN_OFF_ROAD -> 3;
            case LOSS_OF_CONTROL, UNKNOWN -> 2;
            case ABNORMAL_STOP -> 1;
            case NONE -> 0;
        };
    }
    public Instant getConfirmedAt() { return confirmedAt; }
    public boolean isActive() { return active; }
    public List<Observation> getEvidence() { return List.copyOf(evidence); }
    public void addEvidence(Observation observation) { evidence.add(observation); }
    public void confirm(Instant timestamp) { confirmedAt = timestamp; }
    public void close() { active = false; }
}

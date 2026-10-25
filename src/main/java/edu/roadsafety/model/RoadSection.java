package edu.roadsafety.model;

import edu.roadsafety.enums.SafetyState;
import edu.roadsafety.enums.OperationalRole;

import java.util.ArrayList;
import java.util.List;

public final class RoadSection {
    private final String sectionId;
    private final int position;
    private final List<String> connectedSectionIds = new ArrayList<>();
    private SafetyState safetyState = SafetyState.NORMAL;
    private OperationalRole role = OperationalRole.NORMAL_MONITORING;
    private int monitoringAttentionLevel;
    private Warning warning;
    private EvidenceResult latestEvidence;

    public RoadSection(String sectionId, int position) {
        if (sectionId == null || sectionId.isBlank() || position < 0) {
            throw new IllegalArgumentException("Section id and non-negative position are required");
        }
        this.sectionId = sectionId;
        this.position = position;
    }

    public String getSectionId() { return sectionId; }
    public int getPosition() { return position; }
    public List<String> getConnectedSectionIds() { return List.copyOf(connectedSectionIds); }
    public SafetyState getSafetyState() { return safetyState; }
    public OperationalRole getRole() { return role; }
    public int getMonitoringAttentionLevel() { return monitoringAttentionLevel; }
    public Warning getWarning() { return warning; }
    public EvidenceResult getLatestEvidence() { return latestEvidence; }

    public void connectTo(String sectionId) {
        if (!connectedSectionIds.contains(sectionId)) {
            connectedSectionIds.add(sectionId);
        }
    }

    public void setSafetyState(SafetyState safetyState) { this.safetyState = safetyState; }
    public void setRole(OperationalRole role) { this.role = role; }
    public void setMonitoringAttentionLevel(int monitoringAttentionLevel) {
        if (monitoringAttentionLevel < 0 || monitoringAttentionLevel > 1) {
            throw new IllegalArgumentException("monitoringAttentionLevel must be 0 or 1");
        }
        this.monitoringAttentionLevel = monitoringAttentionLevel;
    }
    public void setWarning(Warning warning) { this.warning = warning; }
    public void setLatestEvidence(EvidenceResult latestEvidence) { this.latestEvidence = latestEvidence; }
}

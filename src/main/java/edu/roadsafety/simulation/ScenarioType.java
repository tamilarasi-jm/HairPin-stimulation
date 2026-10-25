package edu.roadsafety.simulation;

public enum ScenarioType {
    NORMAL_TRAFFIC("NORMAL_TRAFFIC", "Normal traffic"),
    ABNORMAL_STOP("ABNORMAL_STOP", "Abnormal stop"),
    CONFIRMED_COLLISION("CORROBORATED_COLLISION", "Corroborated collision"),
    FALSE_POSITIVE("FALSE_POSITIVE", "False positive"),
    CONTRADICTORY_DATA("CONTRADICTORY_DATA", "Contradictory data"),
    SINGLE_VEHICLE_INCIDENT("SINGLE_VEHICLE_INCIDENT", "Single-vehicle incident"),
    CONTINUING_HAZARD("CONTINUING_HAZARD", "Continuing hazard"),
    RECOVERY("EVIDENCE_BASED_RECOVERY", "Evidence-based recovery"),
    MULTIPLE_INCIDENTS("MULTIPLE_INCIDENTS", "Multiple incidents"),
    STALE_EVIDENCE("STALE_MISSING_EVIDENCE", "Stale / missing evidence");

    private final String datasetName;
    private final String label;

    ScenarioType(String datasetName, String label) {
        this.datasetName = datasetName;
        this.label = label;
    }

    public String getDatasetName() {
        return datasetName;
    }

    public String getLabel() {
        return label;
    }
}

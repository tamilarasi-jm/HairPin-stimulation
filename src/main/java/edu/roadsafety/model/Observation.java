package edu.roadsafety.model;

import edu.roadsafety.enums.ObservationType;

import java.time.Instant;
import java.util.Objects;

public record Observation(
        String observationId,
        String scenarioId,
        String scenarioType,
        Instant timestamp,
        String roadsideUnitId,
        String roadSectionId,
        String vehicleId,
        ObservationType observationType,
        String direction,
        double speedKmh,
        double stoppingDurationSeconds,
        boolean impactDetected,
        boolean obstacleDetected,
        String weatherCondition,
        String roadCondition,
        double quality,
        String groundTruthEvent,
        String evidenceGroup,
        String rawValue) {

    public Observation {
        observationId = defaultText(observationId, "observation");
        scenarioId = defaultText(scenarioId, "SIM");
        scenarioType = defaultText(scenarioType, "CUSTOM");
        Objects.requireNonNull(timestamp, "timestamp");
        roadsideUnitId = requireText(roadsideUnitId, "roadsideUnitId");
        roadSectionId = requireText(roadSectionId, "roadSectionId");
        vehicleId = defaultText(vehicleId, "");
        Objects.requireNonNull(observationType, "observationType");
        direction = defaultText(direction, "UNKNOWN");
        weatherCondition = defaultText(weatherCondition, "UNKNOWN");
        roadCondition = defaultText(roadCondition, "UNKNOWN");
        groundTruthEvent = defaultText(groundTruthEvent, "");
        evidenceGroup = defaultText(evidenceGroup, "");
        rawValue = defaultText(rawValue, "");
        if (quality < 0 || quality > 1 || Double.isNaN(quality)) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
        if (Double.isInfinite(speedKmh) || Double.isInfinite(stoppingDurationSeconds)
                || speedKmh < 0 || stoppingDurationSeconds < 0) {
            throw new IllegalArgumentException("speed and stopping duration must be non-negative");
        }
    }

    public Observation(Instant timestamp, String roadsideUnitId, String roadSectionId,
                       ObservationType observationType, String value, String direction, double quality) {
        this("SIM-" + timestamp.toEpochMilli(), "SIM", "CUSTOM", timestamp, roadsideUnitId, roadSectionId,
                "", observationType, direction, 0, 0,
                observationType == ObservationType.IMPACT, observationType == ObservationType.OBSTACLE,
                "UNKNOWN", "UNKNOWN", quality, "", "SIM", value);
    }

    public Observation(String observationId, String scenarioId, String scenarioType, Instant timestamp,
                       String roadsideUnitId, String roadSectionId, String vehicleId,
                       ObservationType observationType, String direction, double speedKmh,
                       double stoppingDurationSeconds, boolean impactDetected, boolean obstacleDetected,
                       String weatherCondition, String roadCondition, double quality,
                       String groundTruthEvent, String evidenceGroup) {
        this(observationId, scenarioId, scenarioType, timestamp, roadsideUnitId, roadSectionId, vehicleId,
                observationType, direction, speedKmh, stoppingDurationSeconds, impactDetected, obstacleDetected,
                weatherCondition, roadCondition, quality, groundTruthEvent, evidenceGroup, "");
    }

    public String value() {
        if (!rawValue.isBlank()) {
            return rawValue;
        }
        return switch (observationType) {
            case SPEED_CHANGE -> "speed=" + speedKmh + " km/h";
            case ABNORMAL_STOP -> "stopped " + stoppingDurationSeconds + " sec";
            case IMPACT -> impactDetected ? "impact indication" : "impact event";
            case OBSTACLE -> obstacleDetected ? "obstacle detected" : "obstacle event";
            case VEHICLE_DETECTED -> vehicleId.isBlank() ? "vehicle detected" : "vehicle " + vehicleId;
            case WEATHER_CHANGE -> weatherCondition;
            case OBSERVATION_GAP -> "observation gap";
            case LOSS_OF_CONTROL -> "loss-of-control indication";
            case ROAD_CLEAR -> "road-clear observation";
            case NO_OBSTRUCTION -> "no obstruction observed";
            case NORMAL_TRAFFIC -> "normal traffic";
            case NORMAL_PASSAGE -> "normal passage";
        };
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}

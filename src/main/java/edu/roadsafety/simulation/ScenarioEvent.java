package edu.roadsafety.simulation;

import edu.roadsafety.model.Observation;

import java.time.Instant;

public record ScenarioEvent(Observation observation, Instant evaluationTime) {
}

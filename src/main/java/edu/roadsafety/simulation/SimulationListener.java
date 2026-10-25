package edu.roadsafety.simulation;

import edu.roadsafety.model.Observation;
import edu.roadsafety.service.ProcessingResult;

public interface SimulationListener {
    default void onScenarioStarted(Scenario scenario) {
    }

    default void onObservationProcessed(Observation observation, ProcessingResult result) {
    }

    default void onScenarioCompleted(Scenario scenario) {
    }

    default void onSimulationReset() {
    }

    default void onSimulationFailure(Throwable failure) {
    }
}

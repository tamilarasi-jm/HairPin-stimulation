package edu.roadsafety.simulation;

import edu.roadsafety.service.ProcessingResult;
import edu.roadsafety.service.RoadSafetySystem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public final class ScenarioRunner {
    private final ScenarioFactory scenarioFactory = new ScenarioFactory();

    public List<ProcessingResult> run(ScenarioType type, RoadSafetySystem system,
                                      BiConsumer<ScenarioEvent, ProcessingResult> afterEvent) throws IOException {
        system.reset();
        Scenario scenario = scenarioFactory.create(type);
        List<ProcessingResult> results = new ArrayList<>();
        for (ScenarioEvent event : scenario.events()) {
            ProcessingResult result = system.processObservation(event.observation(), event.evaluationTime());
            results.add(result);
            afterEvent.accept(event, result);
        }
        return List.copyOf(results);
    }

    public Scenario getScenario(ScenarioType type) throws IOException {
        return scenarioFactory.create(type);
    }

    public ScenarioFactory getScenarioFactory() {
        return scenarioFactory;
    }
}

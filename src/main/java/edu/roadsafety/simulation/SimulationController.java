package edu.roadsafety.simulation;

import edu.roadsafety.model.Observation;
import edu.roadsafety.service.ProcessingResult;
import edu.roadsafety.service.RoadSafetySystem;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class SimulationController implements AutoCloseable {
    private final RoadSafetySystem system;
    private final ScenarioFactory scenarioFactory;
    private final ObservationReplayEngine replayEngine;
    private final HistoricalEvolutionService historyService;
    private final List<SimulationListener> listeners = new CopyOnWriteArrayList<>();
    private Scenario currentScenario;

    public SimulationController(RoadSafetySystem system) {
        this(system, new ScenarioFactory(), new HistoricalEvolutionService(), Duration.ofMillis(550));
    }

    public SimulationController(RoadSafetySystem system, ScenarioFactory scenarioFactory,
                                HistoricalEvolutionService historyService, Duration visualDelay) {
        this.system = system;
        this.scenarioFactory = scenarioFactory;
        this.historyService = historyService;
        replayEngine = new ObservationReplayEngine(visualDelay);
    }

    public void addListener(SimulationListener listener) {
        listeners.add(listener);
    }

    public void removeListener(SimulationListener listener) {
        listeners.remove(listener);
    }

    public void start(ScenarioType type) throws IOException {
        Scenario scenario = scenarioFactory.create(type);
        replayEngine.reset();
        system.reset();
        currentScenario = scenario;
        applyHistoricalPriorities();
        listeners.forEach(listener -> listener.onScenarioStarted(scenario));
        replayEngine.start(scenario.events().stream().map(ScenarioEvent::observation).toList(),
                this::processObservation,
                () -> completeScenario(scenario),
                failure -> listeners.forEach(listener -> listener.onSimulationFailure(failure)));
    }

    public void pause() {
        replayEngine.pause();
    }

    public void resume() {
        replayEngine.resume();
    }

    public boolean isPaused() {
        return replayEngine.isPaused();
    }

    public void reset() {
        replayEngine.reset();
        system.reset();
        currentScenario = null;
        listeners.forEach(SimulationListener::onSimulationReset);
    }

    public RoadSafetySystem getSystem() {
        return system;
    }

    public HistoricalEvolutionService getHistoryService() {
        return historyService;
    }

    private void processObservation(Observation observation) {
        ProcessingResult result = system.processObservation(observation);
        listeners.forEach(listener -> listener.onObservationProcessed(observation, result));
    }

    private void completeScenario(Scenario scenario) {
        historyService.recordScenario(scenario, system);
        applyHistoricalPriorities();
        listeners.forEach(listener -> listener.onScenarioCompleted(scenario));
    }

    private void applyHistoricalPriorities() {
        for (var section : system.getSections()) {
            section.setMonitoringAttentionLevel(historyService.getProfile(section.getSectionId())
                    .monitoringAttentionLevel());
        }
    }

    @Override
    public void close() {
        replayEngine.close();
    }
}

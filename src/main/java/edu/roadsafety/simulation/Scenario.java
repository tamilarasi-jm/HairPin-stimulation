package edu.roadsafety.simulation;

import java.util.List;

public record Scenario(ScenarioType type, String description, List<ScenarioEvent> events) {
    public Scenario {
        events = List.copyOf(events);
    }
}

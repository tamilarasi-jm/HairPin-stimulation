package edu.roadsafety.service;

import edu.roadsafety.enums.SafetyState;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class SafetyStateMachine {
    private final Map<SafetyState, Set<SafetyState>> allowedTransitions = new EnumMap<>(SafetyState.class);

    public SafetyStateMachine() {
        allowedTransitions.put(SafetyState.NORMAL, EnumSet.of(
                SafetyState.ABNORMAL, SafetyState.INCIDENT_CONFIRMATION, SafetyState.OBSERVATION_DEFICIENCY));
        allowedTransitions.put(SafetyState.OBSERVATION_DEFICIENCY, EnumSet.of(
                SafetyState.NORMAL, SafetyState.ABNORMAL, SafetyState.INCIDENT_CONFIRMATION));
        allowedTransitions.put(SafetyState.ABNORMAL, EnumSet.of(
                SafetyState.NORMAL, SafetyState.INCIDENT_CONFIRMATION, SafetyState.OBSERVATION_DEFICIENCY));
        allowedTransitions.put(SafetyState.INCIDENT_CONFIRMATION, EnumSet.of(
                SafetyState.NORMAL, SafetyState.ABNORMAL, SafetyState.CONFIRMED_INCIDENT,
                SafetyState.OBSERVATION_DEFICIENCY));
        allowedTransitions.put(SafetyState.CONFIRMED_INCIDENT, EnumSet.of(
                SafetyState.CONTINUING_HAZARD, SafetyState.RECOVERY, SafetyState.OBSERVATION_DEFICIENCY));
        allowedTransitions.put(SafetyState.CONTINUING_HAZARD, EnumSet.of(
                SafetyState.RECOVERY, SafetyState.OBSERVATION_DEFICIENCY));
        allowedTransitions.put(SafetyState.RECOVERY, EnumSet.of(
                SafetyState.NORMAL, SafetyState.CONTINUING_HAZARD, SafetyState.OBSERVATION_DEFICIENCY));
    }

    public boolean canTransition(SafetyState from, SafetyState to) {
        return allowedTransitions.getOrDefault(from, Set.of()).contains(to);
    }
}

package edu.roadsafety.model;

public record SectionHistoryProfile(
        String roadSectionId,
        int totalObservations,
        int abnormalStops,
        int confirmedIncidents,
        int continuingHazards,
        int recoveries,
        int falsePositives,
        int contradictoryObservations,
        int monitoringAttentionLevel) {
}

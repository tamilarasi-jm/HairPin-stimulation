package edu.roadsafety.service;

import edu.roadsafety.enums.IncidentType;
import edu.roadsafety.model.Observation;

import java.util.List;

public interface IncidentClassifier {
    IncidentType classify(List<Observation> evidence);
}

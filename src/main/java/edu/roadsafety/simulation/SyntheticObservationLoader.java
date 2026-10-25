package edu.roadsafety.simulation;

import edu.roadsafety.enums.ObservationType;
import edu.roadsafety.model.Observation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SyntheticObservationLoader {
    public static final String DATASET_NAME = "synthetic_roadside_observations.csv";
    private static final String RESOURCE_NAME = "/edu/roadsafety/data/" + DATASET_NAME;
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final Path sourcePath;

    public SyntheticObservationLoader() {
        this(null);
    }

    public SyntheticObservationLoader(Path sourcePath) {
        this.sourcePath = sourcePath;
    }

    public List<Observation> loadAll() throws IOException {
        try (InputStream input = openDataset();
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            return parse(reader);
        }
    }

    public List<Observation> loadScenario(ScenarioType scenarioType) throws IOException {
        Objects.requireNonNull(scenarioType, "scenarioType");
        List<Observation> matching = loadAll().stream()
                .filter(observation -> observation.scenarioType().equalsIgnoreCase(scenarioType.getDatasetName()))
                .toList();
        if (matching.isEmpty()) {
            throw new IOException("No observations found for dataset scenario " + scenarioType.getDatasetName());
        }

        String selectedScenarioId = matching.stream()
                .map(Observation::scenarioId)
                .min(String::compareTo)
                .orElseThrow();
        return matching.stream()
                .filter(observation -> observation.scenarioId().equals(selectedScenarioId))
                .sorted(Comparator.comparing(Observation::timestamp).thenComparing(Observation::observationId))
                .toList();
    }

    private InputStream openDataset() throws IOException {
        if (sourcePath != null) {
            return Files.newInputStream(sourcePath);
        }
        InputStream resource = SyntheticObservationLoader.class.getResourceAsStream(RESOURCE_NAME);
        if (resource != null) {
            return resource;
        }
        for (Path candidate : List.of(
                Path.of("data", DATASET_NAME),
                Path.of("src", "main", "java", "edu", "roadsafety", "data", DATASET_NAME))) {
            if (Files.isRegularFile(candidate)) {
                return Files.newInputStream(candidate);
            }
        }
        throw new IOException("Synthetic dataset not found. Expected classpath resource " + RESOURCE_NAME
                + " or file data/" + DATASET_NAME);
    }

    private List<Observation> parse(BufferedReader reader) throws IOException {
        String headerLine = reader.readLine();
        if (headerLine == null) {
            throw new IOException("Synthetic observation CSV is empty");
        }
        List<String> header = parseCsvLine(headerLine);
        if (!header.isEmpty()) {
            header.set(0, header.get(0).replace("\uFEFF", ""));
        }
        Map<String, Integer> indexes = new HashMap<>();
        for (int index = 0; index < header.size(); index++) {
            indexes.put(header.get(index).trim(), index);
        }
        for (String required : List.of("observation_id", "scenario_id", "scenario_type", "timestamp",
                "road_section", "roadside_unit", "vehicle_id", "direction", "observation_type",
                "speed_kmh", "stopping_duration_sec", "impact_detected", "obstacle_detected",
                "weather_condition", "road_condition", "confidence", "ground_truth_event", "evidence_group")) {
            if (!indexes.containsKey(required)) {
                throw new IOException("Synthetic observation CSV is missing required column: " + required);
            }
        }

        List<Observation> observations = new ArrayList<>();
        String line;
        int lineNumber = 1;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (line.isBlank()) {
                continue;
            }
            try {
                List<String> row = parseCsvLine(line);
                observations.add(toObservation(row, indexes));
            } catch (RuntimeException exception) {
                throw new IOException("Invalid synthetic observation at CSV line " + lineNumber + ": "
                        + exception.getMessage(), exception);
            }
        }
        if (observations.isEmpty()) {
            throw new IOException("Synthetic observation CSV contains no data rows");
        }
        return List.copyOf(observations);
    }

    private Observation toObservation(List<String> row, Map<String, Integer> indexes) {
        return new Observation(
                field(row, indexes, "observation_id"),
                field(row, indexes, "scenario_id"),
                field(row, indexes, "scenario_type"),
                LocalDateTime.parse(field(row, indexes, "timestamp"), TIMESTAMP_FORMAT).toInstant(ZoneOffset.UTC),
                field(row, indexes, "roadside_unit"),
                field(row, indexes, "road_section"),
                field(row, indexes, "vehicle_id"),
                ObservationType.valueOf(field(row, indexes, "observation_type").toUpperCase()),
                field(row, indexes, "direction"),
                parseDouble(field(row, indexes, "speed_kmh")),
                parseDouble(field(row, indexes, "stopping_duration_sec")),
                parseBoolean(field(row, indexes, "impact_detected")),
                parseBoolean(field(row, indexes, "obstacle_detected")),
                field(row, indexes, "weather_condition"),
                field(row, indexes, "road_condition"),
                parseDouble(field(row, indexes, "confidence")),
                field(row, indexes, "ground_truth_event"),
                field(row, indexes, "evidence_group"));
    }

    private String field(List<String> row, Map<String, Integer> indexes, String name) {
        int index = indexes.get(name);
        if (index >= row.size()) {
            throw new IllegalArgumentException("missing value for column " + name);
        }
        return row.get(index).trim();
    }

    private double parseDouble(String value) {
        return value.isBlank() ? 0 : Double.parseDouble(value);
    }

    private boolean parseBoolean(String value) {
        return switch (value.toLowerCase()) {
            case "1", "true", "yes" -> true;
            case "0", "false", "no", "" -> false;
            default -> throw new IllegalArgumentException("invalid boolean value: " + value);
        };
    }

    private List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    field.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(character);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("unterminated quoted CSV field");
        }
        fields.add(field.toString());
        return fields;
    }
}

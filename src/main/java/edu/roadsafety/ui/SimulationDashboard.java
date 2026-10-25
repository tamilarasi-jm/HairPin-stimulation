package edu.roadsafety.ui;

import edu.roadsafety.enums.SafetyState;
import edu.roadsafety.model.Incident;
import edu.roadsafety.model.EvidenceResult;
import edu.roadsafety.model.Observation;
import edu.roadsafety.model.RoadSection;
import edu.roadsafety.model.Warning;
import edu.roadsafety.service.ProcessingResult;
import edu.roadsafety.service.RoadSafetySystem;
import edu.roadsafety.simulation.Scenario;
import edu.roadsafety.simulation.SimulationController;
import edu.roadsafety.simulation.SimulationListener;
import edu.roadsafety.simulation.ScenarioType;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class SimulationDashboard extends JFrame {
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);
    private final SimulationController controller;
    private final RoadSafetySystem system;
    private final JPanel roadPanel = new JPanel(new GridBagLayout());
    private final Map<String, SectionCard> sectionCards = new LinkedHashMap<>();
    private final Map<String, Observation> latestObservations = new LinkedHashMap<>();
    private final JTextArea eventLog = new JTextArea();
    private final JLabel scenarioStatus = new JLabel("Select a scenario, then press Start.");
    private final JLabel timestampStatus = new JLabel("SIMULATION TIME: —");
    private final JLabel currentSimulationState = new JLabel("CURRENT SIMULATION STATE: NO ACTIVE INCIDENT");
    private final JLabel incidentStatus = new JLabel("Active incidents: none");
    private final JLabel latestEvent = new JLabel("LATEST PROCESSED OBSERVATION: Waiting for replay.");
    private final JLabel metrics = new JLabel("Replay delay is for visualization only; timings are simulated software measurements.");
    private final JButton pauseButton = new JButton("Pause");
    private ScenarioType selectedScenario = ScenarioType.NORMAL_TRAFFIC;
    private int displayedHistoryCount;

    public SimulationDashboard(RoadSafetySystem system) {
        super("Mountain Road Safety Simulation");
        this.system = system;
        controller = new SimulationController(system);
        controller.addListener(new DashboardListener());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1240, 800));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(12, 12));
        getRootPane().setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        add(buildHeader(), BorderLayout.NORTH);
        add(buildMainContent(), BorderLayout.CENTER);
        add(buildScenarioControls(), BorderLayout.SOUTH);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                controller.close();
            }
        });
        refreshRoad();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(8, 8));
        JLabel title = new JLabel("Mountain Road Safety Monitoring and Adaptive Warning Simulation");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        header.add(title, BorderLayout.NORTH);
        JLabel disclaimer = new JLabel("COURSE-PROJECT SIMULATION — synthetic dataset; no physical sensors, vehicles, or roadside hardware");
        disclaimer.setForeground(new Color(120, 55, 20));
        header.add(disclaimer, BorderLayout.CENTER);
        JPanel status = new JPanel(new GridLayout(0, 1, 2, 2));
        status.add(scenarioStatus);
        currentSimulationState.setFont(currentSimulationState.getFont().deriveFont(Font.BOLD, 13f));
        status.add(currentSimulationState);
        status.add(timestampStatus);
        status.add(incidentStatus);
        header.add(status, BorderLayout.SOUTH);
        return header;
    }

    private JPanel buildMainContent() {
        JPanel center = new JPanel(new BorderLayout(12, 12));
        JPanel routeContainer = new JPanel(new BorderLayout(0, 8));
        routeContainer.setBorder(BorderFactory.createTitledBorder(
                "Conceptual connected sections and simulated roadside units"));
        roadPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        buildRoadTopology();
        routeContainer.add(roadPanel, BorderLayout.CENTER);
        routeContainer.add(buildLegend(), BorderLayout.SOUTH);
        center.add(routeContainer, BorderLayout.NORTH);

        JPanel details = new JPanel(new BorderLayout(8, 8));
        details.setBorder(BorderFactory.createTitledBorder("Live synthetic event stream and history"));
        eventLog.setEditable(false);
        eventLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        latestEvent.setFont(latestEvent.getFont().deriveFont(Font.BOLD, 13f));
        latestEvent.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
        details.add(latestEvent, BorderLayout.NORTH);
        details.add(new JScrollPane(eventLog), BorderLayout.CENTER);
        metrics.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        details.add(metrics, BorderLayout.SOUTH);
        center.add(details, BorderLayout.CENTER);
        return center;
    }

    private JPanel buildScenarioControls() {
        JPanel controls = new JPanel(new BorderLayout(8, 8));
        controls.setBorder(BorderFactory.createTitledBorder("Dataset scenario selection and replay"));
        JPanel buttons = new JPanel(new GridLayout(2, 5, 6, 6));
        for (ScenarioType type : ScenarioType.values()) {
            JButton button = new JButton(type.getLabel());
            button.addActionListener(event -> selectScenario(type));
            buttons.add(button);
        }
        JPanel replayControls = new JPanel(new GridLayout(1, 3, 8, 0));
        JButton startButton = new JButton("Start");
        startButton.addActionListener(event -> startReplay());
        pauseButton.addActionListener(event -> togglePause());
        JButton resetButton = new JButton("Reset");
        resetButton.addActionListener(event -> controller.reset());
        replayControls.add(startButton);
        replayControls.add(pauseButton);
        replayControls.add(resetButton);
        controls.add(buttons, BorderLayout.CENTER);
        controls.add(replayControls, BorderLayout.SOUTH);
        return controls;
    }

    private void selectScenario(ScenarioType type) {
        selectedScenario = type;
        scenarioStatus.setText("Selected dataset scenario: " + type.getDatasetName() + " — press Start to replay.");
    }

    private void startReplay() {
        try {
            controller.start(selectedScenario);
        } catch (java.io.IOException exception) {
            eventLog.append("Dataset load failed: " + exception.getMessage() + "\n");
            scenarioStatus.setText("Unable to load selected scenario.");
        }
    }

    private void togglePause() {
        if (controller.isPaused()) {
            controller.resume();
            pauseButton.setText("Pause");
            scenarioStatus.setText("Replay resumed: " + selectedScenario.getDatasetName());
        } else {
            controller.pause();
            pauseButton.setText("Resume");
            scenarioStatus.setText("Replay paused: " + selectedScenario.getDatasetName());
        }
    }

    private void appendEvent(Observation observation, ProcessingResult result) {
        latestObservations.put(observation.roadSectionId(), observation);
        latestEvent.setText("LATEST PROCESSED OBSERVATION: " + TIME_FORMAT.format(observation.timestamp())
                + "  |  " + observation.observationType() + "  |  "
                + observation.roadSectionId() + " / " + observation.roadsideUnitId());
        latestEvent.setToolTipText(observation.value());
        eventLog.append("SIM " + TIME_FORMAT.format(observation.timestamp()) + "  "
                + observation.observationType() + " at " + observation.roadSectionId()
                + " (" + observation.roadsideUnitId() + ")"
                + (observation.vehicleId().isBlank() ? "" : " vehicle=" + observation.vehicleId())
                + " direction=" + observation.direction()
                + " confidence=" + String.format("%.2f", observation.quality()) + "\n");
        eventLog.append("    speed=" + observation.speedKmh() + " km/h, stop="
                + observation.stoppingDurationSeconds() + " sec, weather=" + observation.weatherCondition()
                + ", road=" + observation.roadCondition() + ", group=" + observation.evidenceGroup() + "\n");
        EvidenceResult evidence = result.evidence();
        eventLog.append("    Evidence: " + evidence.status() + ", score "
                + String.format("%.2f", evidence.score()) + " — " + evidence.explanation() + "\n");
        if (!evidence.supportingObservations().isEmpty()) {
            eventLog.append("    Supporting: " + String.join(" + ", evidence.supportingObservations()) + "\n");
        }
        if (!evidence.contradictions().isEmpty()) {
            eventLog.append("    Contradictory: " + String.join(" + ", evidence.contradictions()) + "\n");
        }
        var history = system.getHistory();
        for (int index = displayedHistoryCount; index < history.size(); index++) {
            var item = history.get(index);
            if (item.category().equals("STATE_TRANSITION") || item.category().equals("INCIDENT_CONFIRMED")
                    || item.category().equals("FALSE_POSITIVE")
                    || item.category().equals("EVIDENCE_CONTRADICTION")) {
                eventLog.append("    " + item.category() + ": " + item.roadSectionId()
                        + " — " + item.detail() + "\n");
            }
        }
        displayedHistoryCount = history.size();
        timestampStatus.setText("SIMULATION TIME: " + TIME_FORMAT.format(observation.timestamp())
                + " (synthetic replay)");
        updateIncidentStatus();
        metrics.setText(String.format("Last processing: event %,d ns | evidence %,d ns | state %,d ns | roles %,d ns | warning %,d ns"
                        + " — local simulated measurements, not physical response guarantees",
                result.eventProcessingNanos(), result.evidenceProcessingNanos(), result.stateTransitionNanos(),
                result.roleAssignmentNanos(), result.warningActivationNanos()));
        eventLog.setCaretPosition(eventLog.getDocument().getLength());
    }

    private void updateIncidentStatus() {
        List<Incident> activeIncidents = system.getActiveIncidents();
        if (activeIncidents.isEmpty()) {
            incidentStatus.setText("Active incidents: none");
        } else {
            incidentStatus.setText("Active incidents: " + activeIncidents.stream()
                    .map(incident -> incident.getIncidentType() + " at " + incident.getRoadSectionId())
                    .collect(Collectors.joining("; ")));
        }
        currentSimulationState.setText(globalSimulationState(activeIncidents));
    }

    private String globalSimulationState(List<Incident> activeIncidents) {
        for (SafetyState state : List.of(SafetyState.CONTINUING_HAZARD, SafetyState.RECOVERY,
                SafetyState.CONFIRMED_INCIDENT)) {
            for (Incident incident : activeIncidents) {
                RoadSection section = system.getSections().stream()
                        .filter(candidate -> candidate.getSectionId().equals(incident.getRoadSectionId()))
                        .findFirst().orElseThrow();
                if (section.getSafetyState() == state) {
                    String description = switch (state) {
                        case CONTINUING_HAZARD -> "CONTINUING HAZARD";
                        case RECOVERY -> "RECOVERY IN PROGRESS";
                        case CONFIRMED_INCIDENT -> "ACTIVE INCIDENT";
                        default -> throw new IllegalStateException("Unexpected global simulation state: " + state);
                    };
                    return "CURRENT SIMULATION STATE: " + description + " — " + section.getSectionId();
                }
            }
        }
        if (!activeIncidents.isEmpty()) {
            return "CURRENT SIMULATION STATE: ACTIVE INCIDENT — " + activeIncidents.get(0).getRoadSectionId();
        }
        return "CURRENT SIMULATION STATE: NO ACTIVE INCIDENT";
    }

    private void refreshRoad() {
        for (RoadSection section : system.getSections()) {
            SectionCard card = sectionCards.get(section.getSectionId());
            if (card != null) {
                Observation latest = latestObservations.get(section.getSectionId());
                Incident activeIncident = system.getActiveIncidents().stream()
                        .filter(incident -> incident.getRoadSectionId().equals(section.getSectionId()))
                        .findFirst().orElse(null);
                long contradictionCount = system.getHistory().stream()
                        .filter(item -> item.roadSectionId().equals(section.getSectionId())
                                && item.category().equals("EVIDENCE_CONTRADICTION"))
                        .count();
                card.update(section, latest, activeIncident, contradictionCount);
            }
        }
        roadPanel.repaint();
    }

    private void buildRoadTopology() {
        List<RoadSection> sections = system.getSections().stream()
                .sorted(java.util.Comparator.comparingInt(RoadSection::getPosition))
                .toList();
        for (int index = 0; index < sections.size(); index++) {
            RoadSection section = sections.get(index);
            SectionCard card = new SectionCard();
            sectionCards.put(section.getSectionId(), card);

            GridBagConstraints cardConstraints = new GridBagConstraints();
            cardConstraints.gridx = index * 2;
            cardConstraints.gridy = 0;
            cardConstraints.weightx = 1;
            cardConstraints.weighty = 1;
            cardConstraints.fill = GridBagConstraints.BOTH;
            cardConstraints.insets = index % 2 == 0
                    ? new Insets(2, 2, 26, 2) : new Insets(26, 2, 2, 2);
            roadPanel.add(card.panel, cardConstraints);

            if (index + 1 < sections.size()) {
                RoadSection next = sections.get(index + 1);
                boolean connected = section.getConnectedSectionIds().contains(next.getSectionId());
                JLabel arrow = new JLabel(connected ? (index % 2 == 0 ? "↘" : "↗") : "·",
                        SwingConstants.CENTER);
                arrow.setFont(arrow.getFont().deriveFont(Font.BOLD, 22f));
                arrow.setForeground(new Color(70, 80, 90));
                arrow.setToolTipText(connected ? section.getSectionId() + " connects to "
                        + next.getSectionId() : "No direct connection");
                GridBagConstraints arrowConstraints = new GridBagConstraints();
                arrowConstraints.gridx = index * 2 + 1;
                arrowConstraints.gridy = 0;
                arrowConstraints.weightx = 0.1;
                arrowConstraints.anchor = GridBagConstraints.CENTER;
                arrowConstraints.insets = new Insets(0, 0, 0, 0);
                roadPanel.add(arrow, arrowConstraints);
            }
        }
    }

    private JPanel buildLegend() {
        JPanel legend = new JPanel(new BorderLayout(4, 4));
        legend.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        legend.add(new JLabel("Five connected road sections shown for simulation purposes; colors represent simulated operational states."),
                BorderLayout.NORTH);
        JPanel items = new JPanel(new GridLayout(1, 0, 8, 0));
        items.add(legendItem("GREEN", "Normal monitoring", new Color(55, 150, 85)));
        items.add(legendItem("AMBER / ORANGE", "Warning or abnormal condition", new Color(210, 135, 25)));
        items.add(legendItem("RED", "Active incident or hazard", new Color(190, 45, 45)));
        items.add(legendItem("BLUE", "Recovery", new Color(55, 125, 190)));
        items.add(legendItem("GRAY", "Observation deficiency", new Color(125, 125, 125)));
        legend.add(items, BorderLayout.CENTER);
        return legend;
    }

    private JLabel legendItem(String colorName, String meaning, Color color) {
        JLabel item = new JLabel(colorName + " — " + meaning);
        item.setForeground(color);
        item.setFont(item.getFont().deriveFont(Font.PLAIN, 10f));
        item.setToolTipText("Color indicates a simulated operational state.");
        return item;
    }

    private Color stateColor(SafetyState state) {
        return switch (state) {
            case NORMAL -> new Color(55, 150, 85);
            case OBSERVATION_DEFICIENCY -> new Color(125, 125, 125);
            case ABNORMAL -> new Color(235, 190, 40);
            case INCIDENT_CONFIRMATION -> new Color(230, 125, 35);
            case CONFIRMED_INCIDENT -> new Color(200, 45, 45);
            case CONTINUING_HAZARD -> new Color(130, 25, 35);
            case RECOVERY -> new Color(55, 125, 190);
        };
    }

    private Color stateBackground(SafetyState state) {
        return switch (state) {
            case NORMAL -> new Color(232, 246, 236);
            case OBSERVATION_DEFICIENCY -> new Color(238, 238, 238);
            case ABNORMAL -> new Color(255, 247, 218);
            case INCIDENT_CONFIRMATION -> new Color(255, 235, 213);
            case CONFIRMED_INCIDENT -> new Color(255, 226, 226);
            case CONTINUING_HAZARD -> new Color(250, 225, 226);
            case RECOVERY -> new Color(227, 239, 250);
        };
    }

    private final class SectionCard {
        private final JPanel panel = new JPanel(new GridBagLayout());
        private final JLabel heading = createLabel(Font.BOLD, 15f);
        private final JLabel activeIncident = createLabel(Font.BOLD, 10f);
        private final JLabel role = createLabel(Font.BOLD, 10f);
        private final JLabel state = createLabel(Font.BOLD, 10f);
        private final JLabel evidenceHeading = createLabel(Font.BOLD, 10f);
        private final JProgressBar evidenceBar = new JProgressBar(0, 100);
        private final JLabel evidenceDetails = createLabel(Font.PLAIN, 9f);
        private final JLabel evidenceStatus = createLabel(Font.BOLD, 9f);
        private final JLabel latest = createLabel(Font.PLAIN, 9f);
        private final JLabel warning = createLabel(Font.PLAIN, 9f);
        private int nextRow;

        private SectionCard() {
            panel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
            panel.setPreferredSize(new Dimension(176, 238));
            activeIncident.setForeground(new Color(150, 25, 25));
            addRow(heading, 1);
            addRow(activeIncident, 1);
            addRow(role, 1);
            addRow(state, 1);
            evidenceBar.setStringPainted(true);
            evidenceBar.setString("0%");
            evidenceBar.setToolTipText("Course-Project Evidence Support Score (0-100)");
            addRow(evidenceHeading, 1);
            addRow(evidenceBar, 1);
            addRow(evidenceStatus, 1);
            addRow(evidenceDetails, 1);
            addRow(latest, 1);
            addRow(warning, 1);
        }

        private JLabel createLabel(int style, float size) {
            JLabel label = new JLabel("", SwingConstants.CENTER);
            label.setFont(label.getFont().deriveFont(style, size));
            return label;
        }

        private void addRow(java.awt.Component component, double weightY) {
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.gridx = 0;
            constraints.gridy = nextRow++;
            constraints.weightx = 1;
            constraints.weighty = weightY;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.insets = new Insets(1, 0, 1, 0);
            panel.add(component, constraints);
        }

        private void update(RoadSection section, Observation latestObservation, Incident incident,
                            long contradictionCount) {
            Color color = roleOrStateColor(section);
            panel.setBackground(roleOrStateBackground(section));
            panel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(color, incident == null ? 2 : 4),
                    BorderFactory.createEmptyBorder(6, 6, 6, 6)));
            heading.setText(section.getSectionId() + " / RU-" + (section.getPosition() + 1));
            activeIncident.setVisible(incident != null);
            activeIncident.setText(incident == null ? ""
                    : htmlField("ACTIVE INCIDENT", incident.getIncidentType().toString()));
            role.setText(roleText(section));
            state.setText(htmlField("CURRENT STATE", section.getSafetyState().toString()));
            evidenceHeading.setText("EVIDENCE SUPPORT");

            EvidenceResult result = section.getLatestEvidence();
            int score = result == null ? 0 : (int) Math.round(result.score());
            evidenceBar.setValue(score);
            evidenceBar.setString(score + "%");
            evidenceBar.setForeground(color);
            evidenceDetails.setText(result == null ? "Supporting: 0 | Contradictions: 0"
                    : String.format("SUPPORTING: %d | CONTRADICTIONS: %d",
                    result.supportingObservations().size(),
                    Math.max(result.contradictions().size(), (int) contradictionCount)));
            evidenceStatus.setText(htmlField("EVIDENCE STATUS",
                    result == null ? "INSUFFICIENT" : result.status().toString()));
            latest.setText(htmlField("LATEST OBSERVATION", latestObservation == null ? "—"
                    : latestObservation.observationType().toString()));
            latest.setToolTipText(latestObservation == null ? "No observation processed for this section."
                    : TIME_FORMAT.format(latestObservation.timestamp()) + " — " + latestObservation.value());
            Warning currentWarning = section.getWarning();
            warning.setText(htmlField("ACTIVE WARNING", currentWarning == null ? "NONE"
                    : currentWarning.level() + ": " + currentWarning.heading()));
            panel.setToolTipText(section.getConnectedSectionIds().isEmpty()
                    ? "Road endpoint" : "Connected to " + String.join(", ", section.getConnectedSectionIds()));
            panel.revalidate();
        }

        private String htmlField(String title, String value) {
            return "<html><div style='text-align:center'><b>" + title + ":</b><br>"
                    + value.replace('_', ' ') + "</div></html>";
        }

        private String roleText(RoadSection section) {
            return htmlField("OPERATIONAL ROLE", section.getRole().toString());
        }

        private Color roleOrStateColor(RoadSection section) {
            return switch (section.getRole()) {
                case EARLY_APPROACH_WARNING -> new Color(205, 155, 25);
                case IMMEDIATE_HAZARD_WARNING -> new Color(225, 115, 25);
                case INCIDENT_AWARENESS -> new Color(200, 45, 45);
                case CONTINUING_HAZARD_MONITORING -> new Color(180, 75, 35);
                case RECOVERY_MONITORING -> new Color(55, 125, 190);
                case NORMAL_MONITORING -> stateColor(section.getSafetyState());
            };
        }

        private Color roleOrStateBackground(RoadSection section) {
            return switch (section.getRole()) {
                case EARLY_APPROACH_WARNING -> new Color(255, 248, 218);
                case IMMEDIATE_HAZARD_WARNING -> new Color(255, 235, 215);
                case INCIDENT_AWARENESS -> new Color(255, 226, 226);
                case CONTINUING_HAZARD_MONITORING -> new Color(250, 234, 222);
                case RECOVERY_MONITORING -> new Color(227, 239, 250);
                case NORMAL_MONITORING -> stateBackground(section.getSafetyState());
            };
        }
    }

    private final class DashboardListener implements SimulationListener {
        @Override
        public void onScenarioStarted(Scenario scenario) {
            SwingUtilities.invokeLater(() -> {
                selectedScenario = scenario.type();
                eventLog.setText("");
                displayedHistoryCount = 0;
                latestObservations.clear();
                latestEvent.setText("LATEST PROCESSED OBSERVATION: Waiting for replay.");
                latestEvent.setToolTipText(null);
                timestampStatus.setText("SIMULATION TIME: Awaiting first observation (synthetic replay)");
                currentSimulationState.setText("CURRENT SIMULATION STATE: NO ACTIVE INCIDENT");
                scenarioStatus.setText("Replaying " + scenario.type().getDatasetName() + " ("
                        + scenario.events().size() + " observations) — simulation timestamps are UTC.");
                pauseButton.setText("Pause");
                refreshRoad();
            });
        }

        @Override
        public void onObservationProcessed(Observation observation, ProcessingResult result) {
            SwingUtilities.invokeLater(() -> {
                appendEvent(observation, result);
                refreshRoad();
            });
        }

        @Override
        public void onScenarioCompleted(Scenario scenario) {
            SwingUtilities.invokeLater(() -> {
                scenarioStatus.setText("Replay complete: " + scenario.type().getDatasetName()
                        + " — historical observations recorded.");
                for (RoadSection section : system.getSections()) {
                    var profile = controller.getHistoryService().getProfile(section.getSectionId());
                    eventLog.append("History " + profile.roadSectionId() + ": observations="
                            + profile.totalObservations() + ", abnormal stops=" + profile.abnormalStops()
                            + ", confirmed incidents=" + profile.confirmedIncidents()
                            + ", continuing hazards=" + profile.continuingHazards()
                            + ", recoveries=" + profile.recoveries()
                            + ", false positives=" + profile.falsePositives()
                            + ", contradictions=" + profile.contradictoryObservations()
                            + ", monitoring attention="
                            + (profile.monitoringAttentionLevel() == 1 ? "ELEVATED" : "STANDARD") + "\n");
                }
                updateIncidentStatus();
                refreshRoad();
            });
        }

        @Override
        public void onSimulationReset() {
            SwingUtilities.invokeLater(() -> {
                eventLog.setText("");
                displayedHistoryCount = 0;
                latestObservations.clear();
                latestEvent.setText("LATEST PROCESSED OBSERVATION: Waiting for replay.");
                latestEvent.setToolTipText(null);
                scenarioStatus.setText("Select a scenario, then press Start.");
                timestampStatus.setText("SIMULATION TIME: —");
                currentSimulationState.setText("CURRENT SIMULATION STATE: NO ACTIVE INCIDENT");
                pauseButton.setText("Pause");
                updateIncidentStatus();
                refreshRoad();
            });
        }

        @Override
        public void onSimulationFailure(Throwable failure) {
            SwingUtilities.invokeLater(() -> {
                eventLog.append("Simulation failed: " + failure.getMessage() + "\n");
                scenarioStatus.setText("Simulation failed; see event stream for details.");
            });
        }
    }

    static {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}

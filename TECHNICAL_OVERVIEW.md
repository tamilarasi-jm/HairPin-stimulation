# Technical Overview

## Objective and boundaries

This is an educational Advanced Java course-project simulation of selected software decision concepts from the supplied invention disclosure. It is not a physical roadside system, deployment prototype, real-world accident detector, or performance/safety guarantee.

The five road sections are a simulation configuration and are not a limitation stated in the invention disclosure.

The dataset is synthetic and is used only to demonstrate the software simulation.

No patent document is modified or extended. No claims are made that course-project thresholds are specified by the disclosure.

## Dataset

The bundled CSV contains 490 synthetic observations with identifiers, scenario, timestamp, section/unit, vehicle, direction, event type, speed, stopping duration, impact/obstacle flags, weather, road condition, confidence, ground-truth label, and evidence group.

`ground_truth_event` is parsed and retained only for later evaluation. The evidence service, classification service, state machine, replay controller, and report runner do not consult it.

CSV timestamps have no timezone suffix. The loader interprets them as UTC simulation time. Each scenario selection uses the lexicographically first `scenario_id` for that scenario type, then orders observations by timestamp and observation ID. `EVIDENCE_BASED_RECOVERY` replays one `CONTINUING_HAZARD` observation group first as explicit prior-event context, followed by the recovery observations. This is needed because the recovery records begin after the hazard has already arisen.

## Architecture and flow

```text
synthetic_roadside_observations.csv
  -> SyntheticObservationLoader
  -> ScenarioFactory / ScenarioRunner
  -> ObservationReplayEngine
  -> SimulationController
  -> RoadSafetySystem
       -> EvidenceCorroborationService
       -> IncidentClassificationService
       -> SafetyStateMachine
       -> RoleAssignmentService
       -> WarningService
  -> SimulationListener
  -> Swing dashboard and historical profile
```

- `Observation` retains the source fields required for processing and evaluation.
- `SyntheticObservationLoader` validates CSV headers, parses rows, and selects scenario data.
- `ObservationReplayEngine` sorts events and delivers them sequentially with a configurable visual delay. That delay is only for demonstration and is not a real-time processing claim.
- `SimulationController` is the GUI-facing controller and notifies listeners after each processed observation.
- `RoadSafetySystem` is the simulation facade for event processing, section state, incidents, roles, warnings, and history.
- Swing updates are dispatched onto the event-dispatch thread.

The current project has no database or web tier. JDBC/MySQL, Servlet/JSP/Tomcat MVC, and persistence are future phases; suggested tables are `road_sections`, `roadside_units`, `observations`, `incidents`, `incident_history`, and `safety_state_history`.

## Evidence corroboration

The evidence engine weights observation types using `CourseEvidenceScoringStrategy`, applies observation confidence, associates observations within a configurable temporal window, and matches vehicle and direction when present. Evidence-group diversity and distinct types contribute to corroboration. Matching normal-passage evidence reduces certainty; isolated impact or stop evidence is not enough to confirm an incident.

Course-project defaults: abnormal score `1.5`, confirmation score `3.5`, confirmed score `5.0`, evidence window `2 minutes`, stale age `5 minutes`, and a confidence quality floor of `0.5`. Incident confirmation requires at least three distinct signal types, two evidence groups, a critical impact/loss-of-control indication, and no matching high-confidence normal-passage contradiction. Values and rules are simulation parameters, not patent-defined values.

`OBSERVATION_GAP` or very low confidence is surfaced as degraded evidence. The system represents stale or missing evidence as observation deficiency/uncertainty, rather than silently treating it as normal.

## Safety states and incidents

The transition manager validates state changes across:

`NORMAL`, `OBSERVATION_DEFICIENCY`, `ABNORMAL`, `INCIDENT_CONFIRMATION`, `CONFIRMED_INCIDENT`, `CONTINUING_HAZARD`, and `RECOVERY`.

Transitions are evidence-led. Corroborated evidence can confirm an incident. Subsequent obstacle observations keep the hazard active. Repeated high-confidence normal passages move the section to `RECOVERY`; later road-clear/no-obstruction evidence supports return to `NORMAL`. No timer clears an incident.

The road-obstruction classifier distinguishes a continuing obstruction from an earlier collision signal. A single impact indication is not automatically classified as a confirmed collision.

## Connected-section response

The simulation keeps the configured topology `S1 → S2 → S3 → S4 → S5`, with one simulated unit per section. Role assignment uses incident location, relative section position, incident direction, and simplified connectivity:

- approach-side units may receive early or immediate warning roles
- the affected section receives incident awareness
- a neighboring downstream section can monitor continuing hazards
- unrelated sections retain normal monitoring

These are simplified course-project rules, not an asserted implementation detail of a deployed system.

## Historical evolution

`HistoricalEvolutionService` stores synthetic observations and transition history in memory and exposes queryable per-section profiles, including observation counts, abnormal stops, confirmed incidents, continuing hazards, recoveries, false positives, and contradictions.

Monitoring attention becomes elevated only after at least two distinct scenario IDs have independently confirmed incidents with a critical signal and evidence from at least two evidence groups. This is a transparent educational verification condition. History is session-memory only and does not alter evidence thresholds or claim learned/predictive behavior.

## GUI flow

The Swing dashboard provides ten scenario selectors, Start/Pause/Reset, a conceptual section map, per-section state/role/evidence/warning/attention level, simulation timestamps in UTC, active-incident status, a live event/evidence stream, and historical profile summaries. Replay delay exists only to make event progression visible.

## Design patterns and separation

- **State-style transition manager:** `SafetyStateMachine` owns legal transitions; it uses a transition table, not a separate polymorphic class per state.
- **Strategy:** `EvidenceScoringStrategy` / `CourseEvidenceScoringStrategy` make evidence weights replaceable; `IncidentClassifier` allows classification policy substitution.
- **Observer:** `SimulationListener` receives replay lifecycle and event updates; the Swing view is a listener.
- **Factory:** `ScenarioFactory` constructs scenario streams from loaded data.
- **Facade:** `RoadSafetySystem` gives the controller a clear simulation API.
- **MVC-style separation:** Swing view, `SimulationController`, and model/services are separated. This is a desktop layered design, not a Servlet/JSP MVC application.

## Scenario execution and current data limitation

The executable report runs:

`NORMAL_TRAFFIC`, `ABNORMAL_STOP`, `CORROBORATED_COLLISION`, `FALSE_POSITIVE`, `CONTRADICTORY_DATA`, `SINGLE_VEHICLE_INCIDENT`, `CONTINUING_HAZARD`, `EVIDENCE_BASED_RECOVERY`, `MULTIPLE_INCIDENTS`, and `STALE_MISSING_EVIDENCE`.

The supplied `MULTIPLE_INCIDENTS` observations place their impact/obstacle signals at S3 in the inspected samples; they do not provide distinct confirmed hazard locations in that category. The simulation core is separately tested with corroborated incidents at two different sections to verify independent tracking. The dataset itself would need additional distinct-section samples to demonstrate that exact case solely through its CSV category.

## Limitations

- All observations and road topology are synthetic.
- Scenario replay uses one representative dataset run per category (with explicit hazard context for recovery).
- Dataset timestamps are interpreted as UTC because the CSV contains no timezone.
- History is held in memory and is not persisted between application restarts.
- There is no MySQL/JDBC integration, Servlet/JSP dashboard, physical sensor, vehicle, roadside display, or deployment component in this phase.
- Visual replay delay and measured local Java processing durations do not establish physical real-time response.

# Mountain Road Safety Monitoring and Adaptive Warning Simulation

An educational Java simulation of selected software decision concepts from the supplied invention disclosure. It is not a physical roadside system, a deployment prototype, or a claim about real-world performance.

## Current implementation

The existing Phase 1 project now replays the bundled synthetic roadside-observation dataset through the Java simulation core and Swing dashboard. It includes:

- five conceptual connected sections (`S1`–`S5`) and simulated units (`RU-1`–`RU-5`)
- timestamp-ordered CSV loading and sequential replay
- confidence-weighted evidence corroboration, contradictions, and observation-gap handling
- safety-state transitions, incidents, differentiated roles, warning messages, and recovery monitoring
- in-memory event history and section-level history profiles
- Start / Pause / Reset and ten dataset-scenario selections

The dataset has 490 synthetic rows. `ground_truth_event` is retained only as an evaluation label; no simulation decision reads it. One representative `scenario_id` is replayed for each selected scenario category. The recovery replay also uses a continuing-hazard observation group as its prior-event context, because the recovery rows begin after the hazard has already arisen.

No external dataset, database, JDBC driver, Servlet, JSP, Tomcat, sensor, or hardware integration is required in Phase 1. MySQL/JDBC and the web dashboard remain future phases.

## Requirements and run

- JDK 17 or newer
- Maven 3.8 or newer
- A graphical desktop for the Swing dashboard

Run tests and package:

```powershell
mvn test
mvn package
```

Launch the visual replay:

```powershell
mvn exec:java "-Dexec.mainClass=edu.roadsafety.Main"
```

Print execution reports for all ten scenario categories:

```powershell
mvn exec:java "-Dexec.mainClass=edu.roadsafety.Main" "-Dexec.args=--report"
```

The CSV is bundled as a Maven classpath resource from `src/main/java/edu/roadsafety/data/`; it can also be loaded from the expected workspace data path. The dataset's local timestamps are interpreted as UTC simulation timestamps for consistent display and ordering.

## Simulation parameters

The dashboard displays the simulation's **Course-Project Evidence Support Score**, a visualization
parameter for evidence corroboration; it is not a formula supplied by the invention disclosure.
Scores are normalized to `0–100`. The default course-project thresholds are configured in
`ThresholdConfig`:

- `0–39`: insufficient evidence
- `40–59`: abnormal condition
- `60–79`: incident confirmation
- `80–100`: confirmed-incident threshold
- evidence association window: `2 minutes`
- stale evidence age: `5 minutes`

The evidence service uses confidence-weighted contributions for observation types, associates observations within the evidence window, and groups corroboration by observation type and evidence group. Matching normal-passage evidence applies a configurable penalty. The final score is bounded to `0–100`; confirmation additionally requires at least three distinct supporting observation types, at least two evidence groups, a critical impact/loss-of-control indication, and no matching high-confidence normal-passage contradiction. A single abnormal-stop observation contributes to the score but cannot confirm an incident. Observations below confidence `0.5` and `OBSERVATION_GAP` are treated as degraded. These formulas, ranges, and rules are course-project simulation parameters, not patent-defined thresholds.

See [TECHNICAL_OVERVIEW.md](./TECHNICAL_OVERVIEW.md) for architecture, evidence logic, state transitions, history, scenario coverage, and limitations.

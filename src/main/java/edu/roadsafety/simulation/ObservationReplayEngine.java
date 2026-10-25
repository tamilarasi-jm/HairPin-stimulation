package edu.roadsafety.simulation;

import edu.roadsafety.model.Observation;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Comparator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class ObservationReplayEngine implements AutoCloseable {
    private final Object lock = new Object();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "synthetic-observation-replay");
        thread.setDaemon(true);
        return thread;
    });
    private final long delayMillis;
    private List<Observation> observations = List.of();
    private int nextIndex;
    private boolean paused;
    private boolean running;
    private ScheduledFuture<?> scheduledTask;
    private Consumer<Observation> observationConsumer = observation -> { };
    private Runnable completionHandler = () -> { };
    private Consumer<Throwable> failureHandler = throwable -> { };

    public ObservationReplayEngine(Duration visualDelay) {
        Objects.requireNonNull(visualDelay, "visualDelay");
        if (visualDelay.isNegative()) {
            throw new IllegalArgumentException("Visual replay delay must not be negative");
        }
        delayMillis = visualDelay.toMillis();
    }

    public void start(List<Observation> orderedObservations, Consumer<Observation> onObservation,
                      Runnable onComplete, Consumer<Throwable> onFailure) {
        Objects.requireNonNull(orderedObservations, "orderedObservations");
        synchronized (lock) {
            cancelScheduledTask();
            observations = orderedObservations.stream()
                    .sorted(Comparator.comparing(Observation::timestamp).thenComparing(Observation::observationId))
                    .toList();
            observationConsumer = Objects.requireNonNull(onObservation, "onObservation");
            completionHandler = Objects.requireNonNull(onComplete, "onComplete");
            failureHandler = Objects.requireNonNull(onFailure, "onFailure");
            nextIndex = 0;
            paused = false;
            running = true;
            scheduleNext(0);
        }
    }

    public void pause() {
        synchronized (lock) {
            if (!running || paused) {
                return;
            }
            paused = true;
            cancelScheduledTask();
        }
    }

    public void resume() {
        synchronized (lock) {
            if (!running || !paused) {
                return;
            }
            paused = false;
            scheduleNext(0);
        }
    }

    public void reset() {
        synchronized (lock) {
            cancelScheduledTask();
            observations = List.of();
            nextIndex = 0;
            paused = false;
            running = false;
        }
    }

    public boolean isRunning() {
        synchronized (lock) {
            return running;
        }
    }

    public boolean isPaused() {
        synchronized (lock) {
            return paused;
        }
    }

    private void scheduleNext(long delay) {
        if (!running || paused) {
            return;
        }
        scheduledTask = executor.schedule(this::processNext, delay, TimeUnit.MILLISECONDS);
    }

    private void processNext() {
        synchronized (lock) {
            if (!running || paused) {
                return;
            }
            if (nextIndex >= observations.size()) {
                running = false;
                completionHandler.run();
                return;
            }
            Observation observation = observations.get(nextIndex++);
            try {
                observationConsumer.accept(observation);
            } catch (RuntimeException exception) {
                running = false;
                failureHandler.accept(exception);
                return;
            }
            if (nextIndex >= observations.size()) {
                running = false;
                completionHandler.run();
            } else {
                scheduleNext(delayMillis);
            }
        }
    }

    private void cancelScheduledTask() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledTask = null;
        }
    }

    @Override
    public void close() {
        reset();
        executor.shutdownNow();
    }
}

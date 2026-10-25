package edu.roadsafety.model;

import java.time.Duration;

public record ThresholdConfig(
        double abnormalScore,
        double confirmationScore,
        double confirmedScore,
        Duration evidenceWindow,
        Duration staleAfter) {

    public ThresholdConfig {
        if (abnormalScore <= 0 || confirmationScore < abnormalScore || confirmedScore < confirmationScore) {
            throw new IllegalArgumentException("Evidence thresholds must be positive and ordered");
        }
        if (evidenceWindow == null || evidenceWindow.isNegative() || evidenceWindow.isZero()
                || staleAfter == null || staleAfter.isNegative() || staleAfter.isZero()) {
            throw new IllegalArgumentException("Evidence durations must be positive");
        }
    }

    public static ThresholdConfig courseDefaults() {
        return new ThresholdConfig(40, 60, 80, Duration.ofMinutes(2), Duration.ofMinutes(5));
    }
}

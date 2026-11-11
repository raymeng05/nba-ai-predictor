package com.nba.nba_predictor.prediction;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ModelSummary(
        String modelName,
        int trainingSamples,
        int historicalRows,
        List<String> trainingSeasons,
        String validationSeason,
        int validationSamples,
        int featureCount,
        List<String> targetStats,
        Double validationR2,
        Double withinToleranceAccuracyPct,
        Map<String, Double> withinToleranceAccuracyByStat,
        Map<String, Double> accuracyTolerances,
        Double baselineWithinToleranceAccuracyPct,
        Double accuracyImprovementPctPoints,
        Map<String, Double> meanAbsoluteError,
        Instant trainedAt,
        String trainingNote) {
}

package com.nba.nba_predictor.prediction;

import java.time.Instant;
import java.util.List;

public record ModelSummary(
        String modelName,
        int trainingSamples,
        int featureCount,
        List<String> targetStats,
        Instant trainedAt,
        String trainingNote) {
}

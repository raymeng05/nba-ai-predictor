package com.nba.nba_predictor.prediction;

import java.util.List;

public record PlayerPrediction(
        String name,
        String team,
        String position,
        int currentAge,
        int predictedAge,
        double confidence,
        int trainingSamples,
        PredictionStats current,
        PredictionStats predicted,
        PredictionStats delta,
        List<String> modelInputs,
        String modelNote) {
}

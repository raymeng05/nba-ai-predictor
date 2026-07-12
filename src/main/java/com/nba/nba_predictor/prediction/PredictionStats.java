package com.nba.nba_predictor.prediction;

public record PredictionStats(
        double games,
        double minutesPlayed,
        double points,
        double totalRebounds,
        double assists,
        double steals,
        double blocks,
        double turnovers,
        double fieldGoalPct,
        double threePointerPct,
        double freeThrowPct,
        double effectiveFieldGoalPct) {
}

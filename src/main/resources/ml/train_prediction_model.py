#!/usr/bin/env python3
"""Train and evaluate the next-season NBA player projection model.

Each example is a real player-season transition: season N supplies the features
and season N+1 supplies the labels. The newest target season is kept out of
training for a chronological backtest, then the production model is refit on
every available transition.
"""

import json
import sys
from datetime import datetime, timezone

import numpy as np
import pandas as pd
from sklearn.linear_model import Ridge
from sklearn.metrics import mean_absolute_error, r2_score
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler


TARGET_STATS = [
    "games",
    "minutesPlayed",
    "points",
    "totalRebounds",
    "assists",
    "steals",
    "blocks",
    "turnovers",
    "fieldGoalPct",
    "threePointerPct",
    "freeThrowPct",
    "effectiveFieldGoalPct",
]

NUMERIC_COLUMNS = ["season", "age", *TARGET_STATS]

FEATURE_NAMES = [
    "next_age_scaled",
    "next_age_squared_scaled",
    "games_rate",
    "minutes_rate",
    "points_rate",
    "rebounds_rate",
    "assists_rate",
    "steals_rate",
    "blocks_rate",
    "turnovers_rate",
    "field_goal_pct",
    "three_pointer_pct",
    "free_throw_pct",
    "effective_field_goal_pct",
    "team_scoring_index",
]

# Regression has no classification-style accuracy. These tolerances define an
# easy-to-interpret hit rate for the chronological backtest.
ACCURACY_TOLERANCES = {
    "games": 10.0,
    "minutesPlayed": 3.0,
    "points": 2.0,
    "totalRebounds": 2.0,
    "assists": 2.0,
    "steals": 0.5,
    "blocks": 0.5,
    "turnovers": 0.75,
    "fieldGoalPct": 0.03,
    "threePointerPct": 0.03,
    "freeThrowPct": 0.03,
    "effectiveFieldGoalPct": 0.03,
}

PREDICTION_BOUNDS = {
    "games": (0.0, 82.0, 0),
    "minutesPlayed": (0.0, 42.0, 1),
    "points": (0.0, 38.0, 1),
    "totalRebounds": (0.0, 17.0, 1),
    "assists": (0.0, 14.0, 1),
    "steals": (0.0, 4.0, 1),
    "blocks": (0.0, 5.0, 1),
    "turnovers": (0.0, 6.0, 1),
    "fieldGoalPct": (0.0, 0.72, 3),
    "threePointerPct": (0.0, 0.55, 3),
    "freeThrowPct": (0.0, 0.97, 3),
    "effectiveFieldGoalPct": (0.0, 0.75, 3),
}

TEAM_ALIASES = {
    "BKN": "BRK",
    "CHA": "CHO",
    "PHX": "PHO",
}


def normalize(value):
    if value is None or pd.isna(value):
        return ""
    normalized = str(value).strip().upper()
    return TEAM_ALIASES.get(normalized, normalized)


def season_label(end_year):
    end_year = int(end_year)
    return f"{end_year - 1}-{str(end_year)[-2:]}"


def make_model():
    return Pipeline(
        steps=[
            ("scaler", StandardScaler()),
            ("ridge", Ridge(alpha=0.55)),
        ]
    )


def load_historical_data(input_path):
    df = pd.read_csv(input_path)
    required = {"playerId", "name", "team", *NUMERIC_COLUMNS}
    missing = sorted(required.difference(df.columns))
    if missing:
        raise RuntimeError(
            "Historical data is missing required columns: " + ", ".join(missing)
        )

    for column in NUMERIC_COLUMNS:
        df[column] = pd.to_numeric(df[column], errors="coerce").fillna(0.0)

    df["playerId"] = df["playerId"].astype(str)
    df["teamKey"] = df["team"].map(normalize)
    df = df[(df["games"] > 0) & (df["minutesPlayed"] > 0)].copy()
    if df.empty:
        raise RuntimeError("No usable player-season rows were provided.")

    duplicate_keys = df.duplicated(["playerId", "season"], keep=False)
    if duplicate_keys.any():
        examples = df.loc[duplicate_keys, ["playerId", "season"]].head(5)
        raise RuntimeError(
            "Historical data must contain one aggregate row per player and season. "
            f"Duplicate examples: {examples.to_dict(orient='records')}"
        )

    league_average = df.groupby("season")["points"].transform("mean").replace(0, 1)
    team_average = df.groupby(["season", "teamKey"])["points"].transform("mean")
    df["teamScoringIndex"] = (team_average / league_average).clip(0.90, 1.10)
    return df.sort_values(["season", "playerId"]).reset_index(drop=True)


def build_next_season_pairs(df, minimum_games=10, minimum_minutes=5.0):
    """Join adjacent seasons for the same player without leaking future data."""
    current_columns = [
        "playerId",
        "season",
        "age",
        "teamKey",
        "teamScoringIndex",
        *TARGET_STATS,
    ]
    current = df[current_columns].copy()
    target = df[["playerId", "season", *TARGET_STATS]].copy()
    target = target.rename(
        columns={
            "season": "targetSeason",
            **{stat: f"target_{stat}" for stat in TARGET_STATS},
        }
    )

    current["targetSeason"] = current["season"] + 1
    pairs = current.merge(target, on=["playerId", "targetSeason"], how="inner")
    pairs = pairs[
        (pairs["games"] >= minimum_games)
        & (pairs["minutesPlayed"] >= minimum_minutes)
    ].copy()
    if pairs.empty:
        raise RuntimeError("No adjacent player-season pairs were available for training.")
    return pairs.sort_values(["targetSeason", "playerId"]).reset_index(drop=True)


def build_features(rows):
    next_age = rows["age"].astype(float) + 1.0
    features = pd.DataFrame(index=rows.index)
    features["next_age_scaled"] = next_age / 40.0
    features["next_age_squared_scaled"] = (next_age * next_age) / 1600.0
    features["games_rate"] = rows["games"].astype(float) / 82.0
    features["minutes_rate"] = rows["minutesPlayed"].astype(float) / 48.0
    features["points_rate"] = rows["points"].astype(float) / 40.0
    features["rebounds_rate"] = rows["totalRebounds"].astype(float) / 18.0
    features["assists_rate"] = rows["assists"].astype(float) / 14.0
    features["steals_rate"] = rows["steals"].astype(float) / 4.0
    features["blocks_rate"] = rows["blocks"].astype(float) / 5.0
    features["turnovers_rate"] = rows["turnovers"].astype(float) / 6.0
    features["field_goal_pct"] = rows["fieldGoalPct"].astype(float)
    features["three_pointer_pct"] = rows["threePointerPct"].astype(float)
    features["free_throw_pct"] = rows["freeThrowPct"].astype(float)
    features["effective_field_goal_pct"] = rows["effectiveFieldGoalPct"].astype(float)
    features["team_scoring_index"] = rows["teamScoringIndex"].astype(float)
    return features[FEATURE_NAMES]


def target_values(rows):
    return rows[[f"target_{stat}" for stat in TARGET_STATS]].set_axis(
        TARGET_STATS, axis=1
    )


def current_values(rows):
    return rows[TARGET_STATS].copy()


def bound_predictions(predictions):
    bounded = pd.DataFrame(predictions, columns=TARGET_STATS).reset_index(drop=True)
    for stat, (minimum, maximum, decimals) in PREDICTION_BOUNDS.items():
        bounded[stat] = bounded[stat].clip(minimum, maximum).round(decimals)
    return bounded


def round_metric_map(values):
    return {stat: round(float(value), 4) for stat, value in values.items()}


def evaluate_predictions(actual, predicted, baseline):
    predicted = bound_predictions(predicted)
    baseline = bound_predictions(baseline)
    actual = actual.reset_index(drop=True)

    mae_values = mean_absolute_error(actual, predicted, multioutput="raw_values")
    baseline_mae_values = mean_absolute_error(
        actual, baseline, multioutput="raw_values"
    )
    r2_values = r2_score(actual, predicted, multioutput="raw_values")
    weighted_r2 = r2_score(actual, predicted, multioutput="variance_weighted")

    tolerance_hits = {}
    baseline_tolerance_hits = {}
    for stat in TARGET_STATS:
        tolerance = ACCURACY_TOLERANCES[stat]
        tolerance_hits[stat] = float(
            ((actual[stat] - predicted[stat]).abs() <= tolerance).mean() * 100.0
        )
        baseline_tolerance_hits[stat] = float(
            ((actual[stat] - baseline[stat]).abs() <= tolerance).mean() * 100.0
        )

    model_accuracy = float(np.mean(list(tolerance_hits.values())))
    baseline_accuracy = float(np.mean(list(baseline_tolerance_hits.values())))
    mae = dict(zip(TARGET_STATS, mae_values))
    baseline_mae = dict(zip(TARGET_STATS, baseline_mae_values))
    r2_by_stat = dict(zip(TARGET_STATS, r2_values))

    return {
        "r2": round(float(weighted_r2), 4),
        "r2ByStat": round_metric_map(r2_by_stat),
        "mae": round_metric_map(mae),
        "withinToleranceAccuracyPct": round(model_accuracy, 2),
        "withinToleranceAccuracyByStat": round_metric_map(tolerance_hits),
        "toleranceByStat": ACCURACY_TOLERANCES,
        "baselineMae": round_metric_map(baseline_mae),
        "baselineWithinToleranceAccuracyPct": round(baseline_accuracy, 2),
        "accuracyImprovementPctPoints": round(model_accuracy - baseline_accuracy, 2),
    }


def chronological_backtest(pairs):
    validation_season = int(pairs["targetSeason"].max())
    training_rows = pairs[pairs["targetSeason"] < validation_season]
    validation_rows = pairs[pairs["targetSeason"] == validation_season]
    if training_rows.empty or validation_rows.empty:
        raise RuntimeError(
            "At least three consecutive seasons are required for a chronological backtest."
        )

    model = make_model()
    model.fit(build_features(training_rows), target_values(training_rows))
    predictions = model.predict(build_features(validation_rows))
    metrics = evaluate_predictions(
        target_values(validation_rows),
        predictions,
        current_values(validation_rows),
    )
    metrics["validationSeason"] = season_label(validation_season)
    metrics["validationSamples"] = int(len(validation_rows))
    metrics["backtestTrainingSamples"] = int(len(training_rows))
    return metrics


def latest_team_indexes(df):
    latest = df[df["season"] == df["season"].max()]
    return {
        team: round(float(group["teamScoringIndex"].mean()), 6)
        for team, group in latest.groupby("teamKey")
    }


def train(input_path, output_path):
    historical = load_historical_data(input_path)
    pairs = build_next_season_pairs(historical)
    metrics = chronological_backtest(pairs)

    x = build_features(pairs)
    y = target_values(pairs)
    model = make_model()
    model.fit(x, y)

    scaler = model.named_steps["scaler"]
    ridge = model.named_steps["ridge"]
    seasons = sorted(int(season) for season in historical["season"].unique())
    artifact = {
        "modelName": "Ridge next-season projection v3",
        "trainingSamples": int(len(pairs)),
        "historicalRows": int(len(historical)),
        "trainingSeasons": [season_label(season) for season in seasons],
        "featureNames": FEATURE_NAMES,
        "targetStats": TARGET_STATS,
        "trainedAt": datetime.now(timezone.utc).isoformat(),
        "trainingNote": (
            "Trained on adjacent player-season pairs from ten historical NBA "
            "regular seasons. Accuracy is measured with a chronological holdout "
            "of the newest target season, never a random row split."
        ),
        "scalerMean": scaler.mean_.round(12).tolist(),
        "scalerScale": scaler.scale_.round(12).tolist(),
        "coefficients": ridge.coef_.round(12).tolist(),
        "intercepts": ridge.intercept_.round(12).tolist(),
        "teamScoringIndexes": latest_team_indexes(historical),
        "leagueAverage": y.mean().round(6).to_dict(),
        "metrics": metrics,
    }

    with open(output_path, "w", encoding="utf-8") as destination:
        json.dump(artifact, destination, ensure_ascii=False, indent=2, allow_nan=False)
    return artifact


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit(
            "Usage: train_prediction_model.py "
            "<historical_player_seasons.csv> <model.json>"
        )
    train(sys.argv[1], sys.argv[2])

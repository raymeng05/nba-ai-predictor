#!/usr/bin/env python3
import json
import sys
from datetime import datetime, timezone

import numpy as np
import pandas as pd
from sklearn.linear_model import Ridge
from sklearn.metrics import mean_absolute_error, r2_score
from sklearn.model_selection import train_test_split
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

NUMERIC_COLUMNS = [
    "age",
    "games",
    "gamesStarted",
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

FEATURE_NAMES = [
    "age_scaled",
    "age_squared_scaled",
    "games_rate",
    "starts_rate",
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
    "is_guard",
    "is_wing",
    "is_big",
]


def normalize(value):
    if value is None:
        return ""
    return str(value).strip().upper()


def is_guard(position):
    return int("PG" in position or "SG" in position)


def is_wing(position):
    return int("SG" in position or "SF" in position or "PF" in position)


def is_big(position):
    return int("PF" in position or "C" in position)


def build_team_indexes(df):
    league_average = float(df["points"].mean()) or 1.0
    team_averages = df.groupby("teamKey")["points"].mean().to_dict()
    return {
        team: float(np.clip(points / league_average, 0.92, 1.08))
        for team, points in team_averages.items()
    }


def build_position_averages(df):
    return {
        position: group[TARGET_STATS].mean().round(6).to_dict()
        for position, group in df.groupby("positionKey")
    }


def build_features(df, team_indexes):
    features = pd.DataFrame(index=df.index)
    age = df["age"].astype(float)
    games = df["games"].astype(float).replace(0, np.nan)

    features["age_scaled"] = age / 40.0
    features["age_squared_scaled"] = (age * age) / 1600.0
    features["games_rate"] = df["games"].astype(float) / 82.0
    features["starts_rate"] = (df["gamesStarted"].astype(float) / games).fillna(0.0)
    features["minutes_rate"] = df["minutesPlayed"].astype(float) / 48.0
    features["points_rate"] = df["points"].astype(float) / 40.0
    features["rebounds_rate"] = df["totalRebounds"].astype(float) / 18.0
    features["assists_rate"] = df["assists"].astype(float) / 14.0
    features["steals_rate"] = df["steals"].astype(float) / 4.0
    features["blocks_rate"] = df["blocks"].astype(float) / 5.0
    features["turnovers_rate"] = df["turnovers"].astype(float) / 6.0
    features["field_goal_pct"] = df["fieldGoalPct"].astype(float)
    features["three_pointer_pct"] = df["threePointerPct"].astype(float)
    features["free_throw_pct"] = df["freeThrowPct"].astype(float)
    features["effective_field_goal_pct"] = df["effectiveFieldGoalPct"].astype(float)
    features["team_scoring_index"] = df["teamKey"].map(team_indexes).fillna(1.0)
    features["is_guard"] = df["positionKey"].map(is_guard)
    features["is_wing"] = df["positionKey"].map(is_wing)
    features["is_big"] = df["positionKey"].map(is_big)
    return features[FEATURE_NAMES]


def train(input_path, output_path):
    with open(input_path, "r", encoding="utf-8") as source:
        rows = json.load(source)

    df = pd.DataFrame(rows)
    for column in NUMERIC_COLUMNS:
        df[column] = pd.to_numeric(df.get(column, 0.0), errors="coerce").fillna(0.0)

    df["teamKey"] = df.get("team", "").map(normalize)
    df["positionKey"] = df.get("position", "").map(normalize)
    training_df = df[(df["games"] > 0) & (df["minutesPlayed"] > 0)].copy()
    if training_df.empty:
        raise RuntimeError("No usable player rows were provided for model training.")

    team_indexes = build_team_indexes(training_df)
    position_averages = build_position_averages(training_df)
    league_average = training_df[TARGET_STATS].mean().round(6).to_dict()

    x = build_features(training_df, team_indexes)
    y = training_df[TARGET_STATS].astype(float)
    model = Pipeline(
        steps=[
            ("scaler", StandardScaler()),
            ("ridge", Ridge(alpha=0.55, random_state=42)),
        ]
    )
    model.fit(x, y)

    if len(training_df) >= 12:
        x_train, x_test, y_train, y_test = train_test_split(
            x, y, test_size=0.2, random_state=42
        )
        validation_model = Pipeline(
            steps=[
                ("scaler", StandardScaler()),
                ("ridge", Ridge(alpha=0.55, random_state=42)),
            ]
        )
        validation_model.fit(x_train, y_train)
        predictions = validation_model.predict(x_test)
        mae = mean_absolute_error(y_test, predictions, multioutput="raw_values")
        r2 = r2_score(y_test, predictions, multioutput="variance_weighted")
        metrics = {
            "r2": round(float(r2), 4),
            "mae": {
                target: round(float(value), 4)
                for target, value in zip(TARGET_STATS, mae)
            },
        }
    else:
        metrics = {"r2": None, "mae": {}}

    scaler = model.named_steps["scaler"]
    ridge = model.named_steps["ridge"]
    artifact = {
        "modelName": "Python sklearn Ridge roster projection v2",
        "trainingSamples": int(len(training_df)),
        "featureNames": FEATURE_NAMES,
        "targetStats": TARGET_STATS,
        "trainedAt": datetime.now(timezone.utc).isoformat(),
        "trainingNote": (
            "Trained in Python with pandas feature engineering, StandardScaler, "
            "and scikit-learn Ridge regression. Spring loads this artifact for fast API inference."
        ),
        "scalerMean": scaler.mean_.round(12).tolist(),
        "scalerScale": scaler.scale_.round(12).tolist(),
        "coefficients": ridge.coef_.round(12).tolist(),
        "intercepts": ridge.intercept_.round(12).tolist(),
        "teamScoringIndexes": team_indexes,
        "positionAverages": position_averages,
        "leagueAverage": league_average,
        "metrics": metrics,
    }

    with open(output_path, "w", encoding="utf-8") as destination:
        json.dump(artifact, destination, ensure_ascii=False, indent=2)


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("Usage: train_prediction_model.py <players.json> <model.json>")
    train(sys.argv[1], sys.argv[2])

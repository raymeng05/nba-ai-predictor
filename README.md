# NBA AI Predictor

A Spring Boot application that projects a player's next NBA season from age,
availability, production, shooting efficiency, and team scoring context.

## Historical training design

The model ships with 5,386 player-season rows covering ten regular seasons,
2015-16 through 2024-25. The source file is
`src/main/resources/ml/historical_player_seasons.csv`.

Training examples are adjacent seasons for the same player:

```text
player statistics in season N  ->  actual statistics in season N + 1
```

Rows with fewer than 10 games or 5 minutes per game in the input season are
excluded. This produces 3,540 genuine next-season transitions. Unlike a random
row split, this setup does not train the target from the same row as its input.

The application trains a standardized multi-output Ridge model in Python and
exports its scaler, coefficients, intercepts, and evaluation report to Spring.
Java performs inference directly from that artifact.

Historical data is sourced from the open-source
[`nba_data`](https://github.com/llimllib/nba_data) catalog, which publishes
regularly updated stats.nba.com data with stable player IDs and one aggregate
row per player-season.

## Accuracy and backtesting

The newest transition is reserved as a chronological backtest: models train on
targets through 2023-24 and are evaluated on 402 players' actual 2024-25
seasons. Only after reporting those metrics is the production model refit on
all available transitions.

The test currently reports:

- **66.69% within-tolerance accuracy** across all 12 outputs
- **0.3516 variance-weighted R²**
- **2.138 PPG mean absolute error**
- **3.734 minutes mean absolute error**
- **0.781 rebounds mean absolute error**
- **0.595 assists mean absolute error**

“Within-tolerance accuracy” is an explicit regression hit rate rather than a
classification score. A prediction is a hit when it is within the configured
limit—for example ±2 PPG, ±2 rebounds, ±3 minutes, ±10 games, or ±0.030 for a
shooting percentage. The artifact also reports per-stat tolerances, hit rates,
MAE, R², and a carry-forward baseline. `GET /api/prediction/model` exposes the
headline accuracy, holdout season, sample counts, per-stat hit rates and
tolerances, all MAEs, and the baseline comparison.

The backtest evaluates players who appeared in both adjacent seasons. It does
not estimate retirement or the probability that a player misses the entire
next season.

## Python setup and model tests

Install the model dependencies:

```bash
python3 -m pip install -r requirements-ml.txt
```

Re-run the full historical backtest and export an inspectable artifact:

```bash
python3 src/main/resources/ml/train_prediction_model.py \
  src/main/resources/ml/historical_player_seasons.csv \
  /tmp/nba-prediction-model.json
```

Run the model contract and leakage tests:

```bash
python3 -m unittest discover -s src/test/python -v
```

Refresh the historical CSV (internet access required):

```bash
python3 scripts/download_historical_data.py
```

## Spring Boot

Configure the PostgreSQL connection in `src/main/resources/application.properties`,
then run:

```bash
./mvnw spring-boot:run
```

The Python executable defaults to `python3`. Set `NBA_PREDICTOR_PYTHON` if the
dependencies are installed in another interpreter or virtual environment.

Useful prediction endpoints:

- `GET /api/prediction/model` — model provenance and numerical backtest results
- `GET /api/prediction?name=...` — one player's next-season forecast
- `GET /api/prediction?limit=10` — top projected scorers
- `POST /api/prediction/train` — retrain from the bundled historical seasons

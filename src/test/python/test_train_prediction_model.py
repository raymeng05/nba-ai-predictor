import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[3]
TRAINER_PATH = PROJECT_ROOT / "src/main/resources/ml/train_prediction_model.py"
HISTORICAL_DATA_PATH = (
    PROJECT_ROOT / "src/main/resources/ml/historical_player_seasons.csv"
)

spec = importlib.util.spec_from_file_location("train_prediction_model", TRAINER_PATH)
trainer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(trainer)


class TrainPredictionModelTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.historical = trainer.load_historical_data(HISTORICAL_DATA_PATH)
        cls.pairs = trainer.build_next_season_pairs(cls.historical)
        with tempfile.TemporaryDirectory() as temporary_directory:
            output = Path(temporary_directory) / "model.json"
            trainer.train(HISTORICAL_DATA_PATH, output)
            cls.artifact = json.loads(output.read_text(encoding="utf-8"))

    def test_training_pairs_are_same_player_in_adjacent_seasons(self):
        self.assertTrue(
            (self.pairs["targetSeason"] == self.pairs["season"] + 1).all()
        )
        self.assertFalse(
            self.pairs.duplicated(["playerId", "season", "targetSeason"]).any()
        )

    def test_latest_season_is_a_chronological_holdout(self):
        metrics = self.artifact["metrics"]
        self.assertEqual("2024-25", metrics["validationSeason"])
        self.assertGreaterEqual(metrics["validationSamples"], 300)
        self.assertGreater(metrics["backtestTrainingSamples"], 2_500)

    def test_model_reports_reproducible_regression_metrics(self):
        metrics = self.artifact["metrics"]
        self.assertGreater(metrics["r2"], 0.0)
        self.assertGreater(metrics["withinToleranceAccuracyPct"], 50.0)
        self.assertGreater(
            metrics["withinToleranceAccuracyPct"],
            metrics["baselineWithinToleranceAccuracyPct"],
        )
        self.assertLess(metrics["mae"]["points"], 3.0)
        self.assertEqual(set(trainer.TARGET_STATS), set(metrics["mae"]))

    def test_exported_feature_order_matches_contract(self):
        self.assertEqual(trainer.FEATURE_NAMES, self.artifact["featureNames"])
        self.assertEqual(trainer.TARGET_STATS, self.artifact["targetStats"])
        self.assertEqual(
            len(trainer.FEATURE_NAMES), len(self.artifact["scalerMean"])
        )


if __name__ == "__main__":
    unittest.main()

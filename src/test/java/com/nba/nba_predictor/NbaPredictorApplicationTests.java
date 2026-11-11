package com.nba.nba_predictor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.nba.nba_predictor.prediction.ModelSummary;
import com.nba.nba_predictor.prediction.PlayerPredictionService;

@SpringBootTest
class NbaPredictorApplicationTests {

	@Autowired
	private PlayerPredictionService predictionService;

	@Test
	void contextLoads() {
	}

	@Test
	void historicalModelTrainsAndExposesBacktestMetrics() {
		ModelSummary summary = predictionService.getModelSummary();

		assertThat(summary.trainingSeasons()).hasSize(10);
		assertThat(summary.trainingSamples()).isGreaterThan(3_000);
		assertThat(summary.validationSeason()).isEqualTo("2024-25");
		assertThat(summary.validationSamples()).isGreaterThan(300);
		assertThat(summary.withinToleranceAccuracyPct()).isGreaterThan(50.0);
		assertThat(summary.withinToleranceAccuracyByStat()).containsKey("points");
		assertThat(summary.accuracyTolerances().get("points")).isEqualTo(2.0);
		assertThat(summary.accuracyImprovementPctPoints()).isPositive();
		assertThat(summary.meanAbsoluteError().get("points")).isLessThan(3.0);
	}

}

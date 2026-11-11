package com.nba.nba_predictor.prediction;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nba.nba_predictor.player.Player;
import com.nba.nba_predictor.player.PlayerService;

@Service
public class PlayerPredictionService {

    private static final List<String> TARGET_STATS = List.of(
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
            "effectiveFieldGoalPct");

    private static final List<String> FEATURE_NAMES = List.of(
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
            "team_scoring_index");

    private final ObjectMapper objectMapper;
    private final PlayerService playerService;
    private volatile PythonPredictionModel model;

    public PlayerPredictionService(ObjectMapper objectMapper, PlayerService playerService) {
        this.objectMapper = objectMapper;
        this.playerService = playerService;
    }

    public ModelSummary trainModel() {
        this.model = trainWithPython();
        return this.model.summary();
    }

    public ModelSummary getModelSummary() {
        return getModel().summary();
    }

    public Optional<PlayerPrediction> predictPlayer(String name, String team, String position) {
        PythonPredictionModel trainedModel = getModel();
        return findPlayer(name, team, position).map(player -> predict(player, trainedModel));
    }

    public List<PlayerPrediction> predictTopPlayers(int limit) {
        PythonPredictionModel trainedModel = getModel();
        int safeLimit = (int) clamp(limit, 1, 50);

        return playerService.getPlayers()
                .stream()
                .map(player -> predict(player, trainedModel))
                .sorted(Comparator.comparingDouble((PlayerPrediction prediction) -> prediction.predicted().points()).reversed())
                .limit(safeLimit)
                .collect(Collectors.toList());
    }

    private PythonPredictionModel getModel() {
        PythonPredictionModel currentModel = this.model;
        if (currentModel == null) {
            synchronized (this) {
                currentModel = this.model;
                if (currentModel == null) {
                    currentModel = trainWithPython();
                    this.model = currentModel;
                }
            }
        }
        return currentModel;
    }

    private PythonPredictionModel trainWithPython() {
        try {
            Path workingDirectory = Files.createTempDirectory("nba-predictor-ml-");
            Path historicalCsv = copyHistoricalData(workingDirectory);
            Path modelJson = workingDirectory.resolve("prediction-model.json");
            Path trainerScript = copyTrainerScript(workingDirectory);

            Process process = new ProcessBuilder(
                    pythonCommand(),
                    trainerScript.toString(),
                    historicalCsv.toString(),
                    modelJson.toString())
                    .directory(workingDirectory.toFile())
                    .redirectErrorStream(true)
                    .start();

            String output;
            try (InputStream processOutput = process.getInputStream()) {
                output = new String(processOutput.readAllBytes());
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("Python model training failed: " + output);
            }

            PythonModelArtifact artifact = objectMapper.readValue(modelJson.toFile(), PythonModelArtifact.class);
            return PythonPredictionModel.fromArtifact(artifact);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not run Python model training.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Python model training was interrupted.", exception);
        }
    }

    private Path copyTrainerScript(Path workingDirectory) throws IOException {
        Path trainerScript = workingDirectory.resolve("train_prediction_model.py");
        ClassPathResource resource = new ClassPathResource("ml/train_prediction_model.py");
        try (InputStream input = resource.getInputStream();
             OutputStream output = Files.newOutputStream(trainerScript)) {
            input.transferTo(output);
        }
        return trainerScript;
    }

    private Path copyHistoricalData(Path workingDirectory) throws IOException {
        Path historicalCsv = workingDirectory.resolve("historical_player_seasons.csv");
        ClassPathResource resource = new ClassPathResource("ml/historical_player_seasons.csv");
        try (InputStream input = resource.getInputStream();
             OutputStream output = Files.newOutputStream(historicalCsv)) {
            input.transferTo(output);
        }
        return historicalCsv;
    }

    private String pythonCommand() {
        String configured = System.getenv("NBA_PREDICTOR_PYTHON");
        return configured == null || configured.isBlank() ? "python3" : configured;
    }

    private Optional<Player> findPlayer(String name, String team, String position) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }

        String normalizedName = normalize(name);
        List<Player> matches = playerService.getPlayersByName(name)
                .stream()
                .filter(player -> normalize(player.getName()).equals(normalizedName))
                .collect(Collectors.toList());

        if (team != null && !team.isBlank()) {
            String normalizedTeam = normalize(team);
            matches = matches.stream()
                    .filter(player -> normalize(player.getTeam()).equals(normalizedTeam))
                    .collect(Collectors.toList());
        }

        if (position != null && !position.isBlank()) {
            String normalizedPosition = normalize(position);
            matches = matches.stream()
                    .filter(player -> normalize(player.getPosition()).equals(normalizedPosition))
                    .collect(Collectors.toList());
        }

        return matches.stream().findFirst();
    }

    private PlayerPrediction predict(Player player, PythonPredictionModel trainedModel) {
        PredictionStats current = toStats(player);
        double teamIndex = trainedModel.teamScoringIndexes().getOrDefault(safeTeam(player), 1.0);
        PredictionStats modelStats = predictWithPythonModel(player, trainedModel, teamIndex);
        PredictionStats bounded = boundPrediction(modelStats, player);
        PredictionStats delta = subtract(bounded, current);

        double confidence = confidence(player, trainedModel.summary().trainingSamples(), trainedModel.validationR2());
        return new PlayerPrediction(
                player.getName(),
                player.getTeam(),
                player.getPosition(),
                player.getAge(),
                player.getAge() + 1,
                round(confidence, 2),
                trainedModel.summary().trainingSamples(),
                current,
                bounded,
                delta,
                List.of(
                        "Ten NBA regular seasons",
                        "Nine next-season transitions",
                        "Age and prior-season production",
                        "Team scoring environment",
                        "Chronological 2024-25 backtest"),
                "The Ridge model is trained on real season-to-season player outcomes. Spring loads the tested Python artifact for fast API inference.");
    }

    private PredictionStats predictWithPythonModel(Player player, PythonPredictionModel trainedModel, double teamIndex) {
        double[] features = buildFeatures(player, teamIndex);
        double[] scaledFeatures = new double[features.length];
        for (int i = 0; i < features.length; i++) {
            double scale = trainedModel.scalerScale()[i] == 0 ? 1.0 : trainedModel.scalerScale()[i];
            scaledFeatures[i] = (features[i] - trainedModel.scalerMean()[i]) / scale;
        }

        double[] predictions = new double[TARGET_STATS.size()];
        for (int targetIndex = 0; targetIndex < TARGET_STATS.size(); targetIndex++) {
            double value = trainedModel.intercepts()[targetIndex];
            double[] coefficients = trainedModel.coefficients()[targetIndex];
            for (int featureIndex = 0; featureIndex < scaledFeatures.length; featureIndex++) {
                value += coefficients[featureIndex] * scaledFeatures[featureIndex];
            }
            predictions[targetIndex] = value;
        }

        return new PredictionStats(
                predictions[0],
                predictions[1],
                predictions[2],
                predictions[3],
                predictions[4],
                predictions[5],
                predictions[6],
                predictions[7],
                predictions[8],
                predictions[9],
                predictions[10],
                predictions[11]);
    }

    private PredictionStats boundPrediction(PredictionStats stats, Player player) {
        double pctFloor = player.getMinutesPlayed() >= 12 ? 0.18 : 0.0;

        return new PredictionStats(
                round(clamp(stats.games(), 0, 82), 0),
                round(clamp(stats.minutesPlayed(), 0, 42), 1),
                round(clamp(stats.points(), 0, 38), 1),
                round(clamp(stats.totalRebounds(), 0, 17), 1),
                round(clamp(stats.assists(), 0, 14), 1),
                round(clamp(stats.steals(), 0, 4), 1),
                round(clamp(stats.blocks(), 0, 5), 1),
                round(clamp(stats.turnovers(), 0, 6), 1),
                round(clamp(stats.fieldGoalPct(), pctFloor, 0.72), 3),
                round(clamp(stats.threePointerPct(), 0, 0.55), 3),
                round(clamp(stats.freeThrowPct(), 0, 0.97), 3),
                round(clamp(stats.effectiveFieldGoalPct(), pctFloor, 0.75), 3));
    }

    private PredictionStats subtract(PredictionStats predicted, PredictionStats current) {
        return new PredictionStats(
                round(predicted.games() - current.games(), 1),
                round(predicted.minutesPlayed() - current.minutesPlayed(), 1),
                round(predicted.points() - current.points(), 1),
                round(predicted.totalRebounds() - current.totalRebounds(), 1),
                round(predicted.assists() - current.assists(), 1),
                round(predicted.steals() - current.steals(), 1),
                round(predicted.blocks() - current.blocks(), 1),
                round(predicted.turnovers() - current.turnovers(), 1),
                round(predicted.fieldGoalPct() - current.fieldGoalPct(), 3),
                round(predicted.threePointerPct() - current.threePointerPct(), 3),
                round(predicted.freeThrowPct() - current.freeThrowPct(), 3),
                round(predicted.effectiveFieldGoalPct() - current.effectiveFieldGoalPct(), 3));
    }

    private double[] buildFeatures(Player player, double teamIndex) {
        double age = player.getAge() + 1.0;

        return new double[] {
                age / 40.0,
                (age * age) / 1600.0,
                player.getGames() / 82.0,
                player.getMinutesPlayed() / 48.0,
                player.getPoints() / 40.0,
                player.getTotalRebounds() / 18.0,
                player.getAssists() / 14.0,
                player.getSteals() / 4.0,
                player.getBlocks() / 5.0,
                player.getTurnovers() / 6.0,
                player.getFieldGoalPct(),
                player.getThreePointerPct(),
                player.getFreeThrowPct(),
                player.getEffectiveFieldGoalPct(),
                teamIndex
        };
    }

    private PredictionStats toStats(Player player) {
        return new PredictionStats(
                player.getGames(),
                player.getMinutesPlayed(),
                player.getPoints(),
                player.getTotalRebounds(),
                player.getAssists(),
                player.getSteals(),
                player.getBlocks(),
                player.getTurnovers(),
                player.getFieldGoalPct(),
                player.getThreePointerPct(),
                player.getFreeThrowPct(),
                player.getEffectiveFieldGoalPct());
    }

    private double roleStability(Player player) {
        double availability = clamp(player.getGames() / 82.0, 0, 1);
        double startsRate = player.getGames() > 0 ? (double) player.getGamesStarted() / player.getGames() : 0.0;
        double minutes = clamp(player.getMinutesPlayed() / 34.0, 0, 1);
        return clamp((availability * 0.42) + (startsRate * 0.28) + (minutes * 0.30), 0, 1);
    }

    private double confidence(Player player, int trainingSamples, Double validationR2) {
        double ageReliability = player.getAge() <= 33 ? 1.0 : clamp(1.0 - ((player.getAge() - 33) * 0.08), 0.55, 1.0);
        double sampleReliability = clamp(trainingSamples / 350.0, 0.55, 1.0);
        double availability = clamp(player.getGames() / 82.0, 0, 1);
        double role = roleStability(player);
        double validationReliability = validationR2 == null ? 0.72 : clamp((validationR2 + 0.2) / 1.2, 0.45, 1.0);

        return clamp(0.30 + availability * 0.20 + role * 0.18 + ageReliability * 0.12 + sampleReliability * 0.10 + validationReliability * 0.14, 0.30, 0.94);
    }

    private String safeTeam(Player player) {
        if (player.getTeam() == null || player.getTeam().isBlank()) {
            return "UNK";
        }

        String team = player.getTeam().trim().toUpperCase(Locale.ROOT);
        return switch (team) {
            case "BKN" -> "BRK";
            case "CHA" -> "CHO";
            case "PHX" -> "PHO";
            default -> team;
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private double clamp(double value, double min, double max) {
        return Math.min(Math.max(value, min), max);
    }

    private double round(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PythonModelArtifact(
            String modelName,
            int trainingSamples,
            int historicalRows,
            List<String> trainingSeasons,
            List<String> featureNames,
            List<String> targetStats,
            Instant trainedAt,
            String trainingNote,
            double[] scalerMean,
            double[] scalerScale,
            double[][] coefficients,
            double[] intercepts,
            Map<String, Double> teamScoringIndexes,
            Metrics metrics) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Metrics(
            Double r2,
            Map<String, Double> mae,
            Double withinToleranceAccuracyPct,
            Map<String, Double> withinToleranceAccuracyByStat,
            Map<String, Double> toleranceByStat,
            Double baselineWithinToleranceAccuracyPct,
            Double accuracyImprovementPctPoints,
            String validationSeason,
            int validationSamples) {
    }

    private record PythonPredictionModel(
            double[] scalerMean,
            double[] scalerScale,
            double[][] coefficients,
            double[] intercepts,
            Map<String, Double> teamScoringIndexes,
            Double validationR2,
            ModelSummary summary) {

        static PythonPredictionModel fromArtifact(PythonModelArtifact artifact) {
            if (!FEATURE_NAMES.equals(artifact.featureNames())) {
                throw new IllegalStateException("Python model feature order does not match Java inference features.");
            }
            if (!TARGET_STATS.equals(artifact.targetStats())) {
                throw new IllegalStateException("Python model targets do not match Java prediction targets.");
            }

            Metrics metrics = artifact.metrics();
            ModelSummary summary = new ModelSummary(
                    artifact.modelName(),
                    artifact.trainingSamples(),
                    artifact.historicalRows(),
                    artifact.trainingSeasons(),
                    metrics == null ? null : metrics.validationSeason(),
                    metrics == null ? 0 : metrics.validationSamples(),
                    artifact.featureNames().size(),
                    artifact.targetStats(),
                    metrics == null ? null : metrics.r2(),
                    metrics == null ? null : metrics.withinToleranceAccuracyPct(),
                    metrics == null || metrics.withinToleranceAccuracyByStat() == null
                            ? Map.of()
                            : metrics.withinToleranceAccuracyByStat(),
                    metrics == null || metrics.toleranceByStat() == null
                            ? Map.of()
                            : metrics.toleranceByStat(),
                    metrics == null ? null : metrics.baselineWithinToleranceAccuracyPct(),
                    metrics == null ? null : metrics.accuracyImprovementPctPoints(),
                    metrics == null || metrics.mae() == null ? Map.of() : metrics.mae(),
                    artifact.trainedAt(),
                    artifact.trainingNote());

            return new PythonPredictionModel(
                    artifact.scalerMean(),
                    artifact.scalerScale(),
                    artifact.coefficients(),
                    artifact.intercepts(),
                    artifact.teamScoringIndexes() == null ? Map.of() : artifact.teamScoringIndexes(),
                    metrics == null ? null : metrics.r2(),
                    summary);
        }
    }
}

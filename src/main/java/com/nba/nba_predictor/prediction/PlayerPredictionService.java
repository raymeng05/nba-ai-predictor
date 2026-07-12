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

    private final ObjectMapper objectMapper;
    private final PlayerService playerService;
    private volatile PythonPredictionModel model;

    public PlayerPredictionService(ObjectMapper objectMapper, PlayerService playerService) {
        this.objectMapper = objectMapper;
        this.playerService = playerService;
    }

    public ModelSummary trainModel() {
        List<Player> players = playerService.getPlayers();
        this.model = trainWithPython(players);
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
                    currentModel = trainWithPython(playerService.getPlayers());
                    this.model = currentModel;
                }
            }
        }
        return currentModel;
    }

    private PythonPredictionModel trainWithPython(List<Player> players) {
        try {
            Path workingDirectory = Files.createTempDirectory("nba-predictor-ml-");
            Path playersJson = workingDirectory.resolve("players.json");
            Path modelJson = workingDirectory.resolve("prediction-model.json");
            Path trainerScript = copyTrainerScript(workingDirectory);

            objectMapper.writeValue(playersJson.toFile(), players);
            Process process = new ProcessBuilder(pythonCommand(), trainerScript.toString(), playersJson.toString(), modelJson.toString())
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
        PredictionStats positionAverage = trainedModel.positionAverages()
                .getOrDefault(safePosition(player), trainedModel.leagueAverage());
        double teamIndex = trainedModel.teamScoringIndexes().getOrDefault(safeTeam(player), 1.0);
        double ageCurve = ageCurve(player.getAge() + 1);
        double roleStability = roleStability(player);

        PredictionStats modelStats = predictWithPythonModel(player, trainedModel, teamIndex);
        PredictionStats agedStats = applyAgeAndRole(player, current, ageCurve, teamIndex, roleStability);
        PredictionStats predicted = blendStats(agedStats, modelStats, positionAverage);
        PredictionStats bounded = boundPrediction(predicted, player);
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
                        "Python pandas feature engineering",
                        "scikit-learn StandardScaler + Ridge regression",
                        "Age curve for age " + (player.getAge() + 1),
                        "Team scoring environment",
                        "Position baseline shrinkage"),
                "Training now runs in Python using pandas, numpy, and scikit-learn. Spring loads the trained artifact for fast API inference.");
    }

    private PredictionStats predictWithPythonModel(Player player, PythonPredictionModel trainedModel, double teamIndex) {
        double[] features = buildFeatures(player, true, teamIndex);
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

    private PredictionStats applyAgeAndRole(Player player, PredictionStats current, double ageCurve, double teamIndex, double roleStability) {
        double roleFactor = 0.94 + (roleStability * 0.12);
        double usageFactor = ageCurve * roleFactor * (0.97 + (teamIndex * 0.03));
        double defenseFactor = clamp(ageCurve + 0.02, 0.86, 1.08);
        double efficiencyFactor = 1.0 + ((ageCurve - 1.0) * 0.28);

        return new PredictionStats(
                current.games() * clamp(0.93 + roleStability * 0.10 + availabilityAgeAdjustment(player.getAge()), 0.72, 1.04),
                current.minutesPlayed() * clamp(0.91 + roleStability * 0.12 + availabilityAgeAdjustment(player.getAge()), 0.70, 1.05),
                current.points() * usageFactor,
                current.totalRebounds() * clamp(usageFactor + 0.01, 0.78, 1.10),
                current.assists() * clamp(usageFactor + guardCreationBonus(player), 0.78, 1.12),
                current.steals() * defenseFactor,
                current.blocks() * defenseFactor,
                current.turnovers() * clamp(usageFactor, 0.76, 1.12),
                current.fieldGoalPct() * efficiencyFactor,
                current.threePointerPct() * (1.0 + ((ageCurve - 1.0) * 0.18)),
                current.freeThrowPct() * (1.0 + ((ageCurve - 1.0) * 0.08)),
                current.effectiveFieldGoalPct() * efficiencyFactor);
    }

    private PredictionStats blendStats(PredictionStats agedStats, PredictionStats modelStats, PredictionStats positionAverage) {
        return new PredictionStats(
                blend(agedStats.games(), modelStats.games(), positionAverage.games(), 0.56, 0.30),
                blend(agedStats.minutesPlayed(), modelStats.minutesPlayed(), positionAverage.minutesPlayed(), 0.58, 0.28),
                blend(agedStats.points(), modelStats.points(), positionAverage.points(), 0.58, 0.30),
                blend(agedStats.totalRebounds(), modelStats.totalRebounds(), positionAverage.totalRebounds(), 0.58, 0.30),
                blend(agedStats.assists(), modelStats.assists(), positionAverage.assists(), 0.58, 0.30),
                blend(agedStats.steals(), modelStats.steals(), positionAverage.steals(), 0.56, 0.30),
                blend(agedStats.blocks(), modelStats.blocks(), positionAverage.blocks(), 0.56, 0.30),
                blend(agedStats.turnovers(), modelStats.turnovers(), positionAverage.turnovers(), 0.58, 0.30),
                blend(agedStats.fieldGoalPct(), modelStats.fieldGoalPct(), positionAverage.fieldGoalPct(), 0.62, 0.24),
                blend(agedStats.threePointerPct(), modelStats.threePointerPct(), positionAverage.threePointerPct(), 0.62, 0.24),
                blend(agedStats.freeThrowPct(), modelStats.freeThrowPct(), positionAverage.freeThrowPct(), 0.66, 0.20),
                blend(agedStats.effectiveFieldGoalPct(), modelStats.effectiveFieldGoalPct(), positionAverage.effectiveFieldGoalPct(), 0.62, 0.24));
    }

    private double blend(double aged, double model, double baseline, double agedWeight, double modelWeight) {
        double baselineWeight = 1.0 - agedWeight - modelWeight;
        return (aged * agedWeight) + (model * modelWeight) + (baseline * baselineWeight);
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

    private double[] buildFeatures(Player player, boolean nextSeason, double teamIndex) {
        double age = player.getAge() + (nextSeason ? 1.0 : 0.0);
        double startsRate = player.getGames() > 0 ? (double) player.getGamesStarted() / player.getGames() : 0.0;
        String position = safePosition(player);

        return new double[] {
                age / 40.0,
                (age * age) / 1600.0,
                player.getGames() / 82.0,
                startsRate,
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
                teamIndex,
                isGuard(position) ? 1.0 : 0.0,
                isWing(position) ? 1.0 : 0.0,
                isBig(position) ? 1.0 : 0.0
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

    private double ageCurve(int nextAge) {
        if (nextAge <= 22) {
            return 1.06;
        }
        if (nextAge <= 25) {
            return 1.035;
        }
        if (nextAge <= 29) {
            return 1.01;
        }
        if (nextAge <= 32) {
            return 0.985;
        }
        if (nextAge <= 35) {
            return 0.94;
        }
        return 0.90;
    }

    private double availabilityAgeAdjustment(int currentAge) {
        if (currentAge <= 25) {
            return 0.015;
        }
        if (currentAge >= 34) {
            return -0.06;
        }
        if (currentAge >= 31) {
            return -0.03;
        }
        return 0.0;
    }

    private double guardCreationBonus(Player player) {
        return isGuard(safePosition(player)) ? 0.015 : -0.005;
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

    private boolean isGuard(String position) {
        return position.contains("PG") || position.contains("SG");
    }

    private boolean isWing(String position) {
        return position.contains("SG") || position.contains("SF") || position.contains("PF");
    }

    private boolean isBig(String position) {
        return position.contains("PF") || position.contains("C");
    }

    private String safePosition(Player player) {
        return player.getPosition() == null || player.getPosition().isBlank()
                ? "UNK"
                : player.getPosition().trim().toUpperCase(Locale.ROOT);
    }

    private String safeTeam(Player player) {
        return player.getTeam() == null || player.getTeam().isBlank()
                ? "UNK"
                : player.getTeam().trim().toUpperCase(Locale.ROOT);
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
            List<String> featureNames,
            List<String> targetStats,
            Instant trainedAt,
            String trainingNote,
            double[] scalerMean,
            double[] scalerScale,
            double[][] coefficients,
            double[] intercepts,
            Map<String, Double> teamScoringIndexes,
            Map<String, Map<String, Double>> positionAverages,
            Map<String, Double> leagueAverage,
            Metrics metrics) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Metrics(Double r2, Map<String, Double> mae) {
    }

    private record PythonPredictionModel(
            double[] scalerMean,
            double[] scalerScale,
            double[][] coefficients,
            double[] intercepts,
            Map<String, Double> teamScoringIndexes,
            Map<String, PredictionStats> positionAverages,
            PredictionStats leagueAverage,
            Double validationR2,
            ModelSummary summary) {

        static PythonPredictionModel fromArtifact(PythonModelArtifact artifact) {
            ModelSummary summary = new ModelSummary(
                    artifact.modelName(),
                    artifact.trainingSamples(),
                    artifact.featureNames().size(),
                    artifact.targetStats(),
                    artifact.trainedAt(),
                    artifact.trainingNote());

            Map<String, PredictionStats> positionAverages = artifact.positionAverages()
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> statsFromMap(entry.getValue())));

            return new PythonPredictionModel(
                    artifact.scalerMean(),
                    artifact.scalerScale(),
                    artifact.coefficients(),
                    artifact.intercepts(),
                    artifact.teamScoringIndexes(),
                    positionAverages,
                    statsFromMap(artifact.leagueAverage()),
                    artifact.metrics() == null ? null : artifact.metrics().r2(),
                    summary);
        }

        private static PredictionStats statsFromMap(Map<String, Double> values) {
            return new PredictionStats(
                    values.getOrDefault("games", 0.0),
                    values.getOrDefault("minutesPlayed", 0.0),
                    values.getOrDefault("points", 0.0),
                    values.getOrDefault("totalRebounds", 0.0),
                    values.getOrDefault("assists", 0.0),
                    values.getOrDefault("steals", 0.0),
                    values.getOrDefault("blocks", 0.0),
                    values.getOrDefault("turnovers", 0.0),
                    values.getOrDefault("fieldGoalPct", 0.0),
                    values.getOrDefault("threePointerPct", 0.0),
                    values.getOrDefault("freeThrowPct", 0.0),
                    values.getOrDefault("effectiveFieldGoalPct", 0.0));
        }
    }
}

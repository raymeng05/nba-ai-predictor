package com.nba.nba_predictor.prediction;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "api/prediction")
public class PredictionController {

    private final PlayerPredictionService predictionService;

    public PredictionController(PlayerPredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @GetMapping(params = "name")
    public ResponseEntity<PlayerPrediction> predictPlayer(
            @RequestParam String name,
            @RequestParam(required = false) String team,
            @RequestParam(required = false) String pos) {
        return predictionService.predictPlayer(name, team, pos)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<PlayerPrediction> topPredictions(@RequestParam(defaultValue = "10") int limit) {
        return predictionService.predictTopPlayers(limit);
    }

    @GetMapping("/model")
    public ModelSummary modelSummary() {
        return predictionService.getModelSummary();
    }

    @PostMapping("/train")
    public ModelSummary trainModel() {
        return predictionService.trainModel();
    }
}

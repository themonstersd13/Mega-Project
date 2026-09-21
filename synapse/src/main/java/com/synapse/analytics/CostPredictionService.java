package com.synapse.analytics;

import com.synapse.api.response.CostPredictionResponse;
import com.synapse.core.repository.ExecutionLogRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CostPredictionService {
    private final ExecutionLogRepository executionLogRepository;
    private final SyntheticGoalGenerator syntheticGoalGenerator;
    private final HistoricalAverageCostModel historicalAverageCostModel;
    private final LinearRegressionCostModel linearRegressionCostModel;
    private final TreeBasedCostModel treeBasedCostModel;

    private volatile CostPredictionResponse lastPrediction = null;

    @PostConstruct
    public void initialize() {
        refreshModels();
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void refreshModels() {
        List<CostFeature> trainingData = loadOrGenerateTrainingData();
        historicalAverageCostModel.train(trainingData);
        linearRegressionCostModel.train(trainingData);
        treeBasedCostModel.train(trainingData);
    }

    public CostPredictionResponse predict(CostFeature feature) {
        List<CostFeature> trainingData = loadOrGenerateTrainingData();
        historicalAverageCostModel.train(trainingData);
        linearRegressionCostModel.train(trainingData);
        treeBasedCostModel.train(trainingData);

        CostPredictionModel chosenModel = chooseBestModel(trainingData);
        double prediction = chosenModel.predict(feature);

        CostPredictionModel.ModelMetrics metrics = chosenModel.evaluate(trainingData);
        CostPredictionResponse response = CostPredictionResponse.builder()
                .modelName(chosenModel.getName())
                .predictedCostUsd(Math.max(0.0, prediction))
                .mae(metrics.mae())
                .rmse(metrics.rmse())
                .rSquared(metrics.rSquared())
                .trainingRows(trainingData.size())
                .fallbackUsed(trainingData.size() < 25)
                .build();

        lastPrediction = response;
        return response;
    }

    public List<CostFeature> loadOrGenerateTrainingData() {
        List<CostFeature> dataset = new ArrayList<>();
        executionLogRepository.findAll().forEach(log -> dataset.add(CostFeature.fromExecutionLog(log)));
        if (dataset.size() < 200) {
            dataset.addAll(syntheticGoalGenerator.generateWorkload(220 - dataset.size(), 13L + dataset.size()));
        }
        return dataset;
    }

    public CostPredictionResponse getLastPrediction() {
        return lastPrediction;
    }

    private CostPredictionModel chooseBestModel(List<CostFeature> trainingData) {
        List<CostPredictionModel> models = List.of(
                historicalAverageCostModel,
                linearRegressionCostModel,
                treeBasedCostModel
        );
        return models.stream()
                .min(Comparator.comparingDouble(model -> model.evaluate(trainingData).rmse()))
                .orElse(historicalAverageCostModel);
    }
}

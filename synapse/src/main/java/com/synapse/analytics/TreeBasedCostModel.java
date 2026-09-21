package com.synapse.analytics;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TreeBasedCostModel implements CostPredictionModel {
    private double highPriorityAverage = 0.0;
    private double lowPriorityAverage = 0.0;
    private double highTokenAverage = 0.0;
    private double opuAverage = 0.0;
    private double defaultAverage = 0.0;

    @Override
    public String getName() {
        return "tree_regression";
    }

    @Override
    public void train(List<CostFeature> data) {
        if (data == null || data.isEmpty()) {
            defaultAverage = 0.0;
            highPriorityAverage = 0.0;
            lowPriorityAverage = 0.0;
            highTokenAverage = 0.0;
            opuAverage = 0.0;
            return;
        }

        defaultAverage = data.stream().mapToDouble(CostFeature::getTargetCost).average().orElse(0.0);
        highPriorityAverage = data.stream()
                .filter(feature -> feature.getPriority() >= 6)
                .mapToDouble(CostFeature::getTargetCost)
                .average()
                .orElse(defaultAverage);
        lowPriorityAverage = data.stream()
                .filter(feature -> feature.getPriority() < 6)
                .mapToDouble(CostFeature::getTargetCost)
                .average()
                .orElse(defaultAverage);
        highTokenAverage = data.stream()
                .filter(feature -> feature.getTokensUsed() > 5000)
                .mapToDouble(CostFeature::getTargetCost)
                .average()
                .orElse(defaultAverage);
        opuAverage = data.stream()
                .filter(feature -> "OPUS".equalsIgnoreCase(feature.getModelTier()))
                .mapToDouble(CostFeature::getTargetCost)
                .average()
                .orElse(defaultAverage);
    }

    @Override
    public double predict(CostFeature feature) {
        double result = defaultAverage;
        if (feature.getPriority() >= 6) {
            result = Math.max(result, highPriorityAverage);
        } else {
            result = Math.min(result, lowPriorityAverage == 0.0 ? result : lowPriorityAverage);
        }
        if (feature.getTokensUsed() > 5000) {
            result = Math.max(result, highTokenAverage);
        }
        if ("OPUS".equalsIgnoreCase(feature.getModelTier())) {
            result = Math.max(result, opuAverage);
        }
        if (feature.getRetryCount() > 1) {
            result *= 1.12;
        }
        return Math.max(0.0, result);
    }

    @Override
    public ModelMetrics evaluate(List<CostFeature> data) {
        if (data == null || data.isEmpty()) {
            return new ModelMetrics(0.0, 0.0, 1.0);
        }
        double mae = 0.0;
        double mse = 0.0;
        double meanActual = data.stream().mapToDouble(CostFeature::getTargetCost).average().orElse(0.0);
        double ssTotal = 0.0;
        for (CostFeature feature : data) {
            double prediction = predict(feature);
            double error = prediction - feature.getTargetCost();
            mae += Math.abs(error);
            mse += error * error;
            ssTotal += Math.pow(feature.getTargetCost() - meanActual, 2);
        }
        mae /= data.size();
        mse /= data.size();
        double rSquared = ssTotal == 0.0 ? 1.0 : 1.0 - ((mse * data.size()) / ssTotal);
        return new ModelMetrics(mae, Math.sqrt(mse), Math.max(0.0, rSquared));
    }
}

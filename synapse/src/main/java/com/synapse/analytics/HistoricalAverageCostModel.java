package com.synapse.analytics;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HistoricalAverageCostModel implements CostPredictionModel {
    private double averageCost = 0.0;

    @Override
    public String getName() {
        return "historical_average";
    }

    @Override
    public void train(List<CostFeature> data) {
        if (data == null || data.isEmpty()) {
            averageCost = 0.0;
            return;
        }
        averageCost = data.stream()
                .mapToDouble(CostFeature::getTargetCost)
                .average()
                .orElse(0.0);
    }

    @Override
    public double predict(CostFeature feature) {
        return Math.max(0.0, averageCost);
    }

    @Override
    public ModelMetrics evaluate(List<CostFeature> data) {
        if (data == null || data.isEmpty()) {
            return new ModelMetrics(0.0, 0.0, 1.0);
        }
        double meanActual = data.stream().mapToDouble(CostFeature::getTargetCost).average().orElse(0.0);
        double mae = 0.0;
        double mse = 0.0;
        for (CostFeature feature : data) {
            double prediction = predict(feature);
            double error = prediction - feature.getTargetCost();
            mae += Math.abs(error);
            mse += error * error;
        }
        mae /= data.size();
        mse /= data.size();
        double ssTotal = data.stream()
                .mapToDouble(feature -> Math.pow(feature.getTargetCost() - meanActual, 2))
                .sum();
        double rSquared = ssTotal == 0.0 ? 1.0 : 1.0 - (mse * data.size()) / ssTotal;
        return new ModelMetrics(mae, Math.sqrt(mse), Math.max(0.0, rSquared));
    }
}

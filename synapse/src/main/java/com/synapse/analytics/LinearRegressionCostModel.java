package com.synapse.analytics;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LinearRegressionCostModel implements CostPredictionModel {
    private double bias = 0.0;
    private double[] weights = new double[11];

    @Override
    public String getName() {
        return "linear_regression";
    }

    @Override
    public void train(List<CostFeature> data) {
        if (data == null || data.isEmpty()) {
            bias = 0.0;
            weights = new double[11];
            return;
        }

        weights = new double[11];
        bias = data.stream().mapToDouble(CostFeature::getTargetCost).average().orElse(0.0);
        double learningRate = 0.05;

        for (int iteration = 0; iteration < 2000; iteration++) {
            double gradBias = 0.0;
            double[] gradWeights = new double[11];
            for (CostFeature feature : data) {
                double prediction = predictInternal(feature, weights, bias);
                double error = prediction - feature.getTargetCost();
                double[] vector = feature.toVector();
                gradBias += 2.0 * error / data.size();
                for (int i = 0; i < vector.length; i++) {
                    gradWeights[i] += 2.0 * error * vector[i] / data.size();
                }
            }

            bias -= learningRate * gradBias;
            for (int i = 0; i < weights.length; i++) {
                weights[i] -= learningRate * gradWeights[i];
            }
        }
    }

    @Override
    public double predict(CostFeature feature) {
        return Math.max(0.0, predictInternal(feature, weights, bias));
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

    private double predictInternal(CostFeature feature, double[] weights, double biasValue) {
        double[] vector = feature.toVector();
        double result = biasValue;
        for (int i = 0; i < vector.length; i++) {
            result += weights[i] * vector[i];
        }
        return result;
    }
}

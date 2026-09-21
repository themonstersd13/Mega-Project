package com.synapse.analytics;

import java.util.List;

public interface CostPredictionModel {
    String getName();
    void train(List<CostFeature> data);
    double predict(CostFeature feature);
    ModelMetrics evaluate(List<CostFeature> data);

    record ModelMetrics(double mae, double rmse, double rSquared) {
    }
}

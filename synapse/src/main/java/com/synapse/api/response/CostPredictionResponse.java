package com.synapse.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostPredictionResponse {
    private String modelName;
    private double predictedCostUsd;
    private double mae;
    private double rmse;
    private double rSquared;
    private int trainingRows;
    private boolean fallbackUsed;
}

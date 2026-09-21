package com.synapse.api.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostPredictionRequest {
    private String modelTier = "SONNET";
    private Integer priority = 5;
    private Integer requiredSkillTagCount = 2;
    private Integer descriptionLength = 300;
    private Integer confidence = 75;
    private Integer retryCount = 0;
    private Integer deadlineProximityMinutes = 240;
    private Boolean humanApprovalRequired = false;
    private Boolean wasEscalated = false;
    private Long tokensUsed = 4000L;
    private Long actualLatencyMs = 18000L;
}

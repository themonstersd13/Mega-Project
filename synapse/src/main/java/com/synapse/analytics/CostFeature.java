package com.synapse.analytics;

import com.synapse.core.entity.ExecutionLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostFeature {
    private String modelTier = "SONNET";
    private int priority = 5;
    private int requiredSkillTagCount = 2;
    private int descriptionLength = 300;
    private int confidence = 75;
    private int retryCount = 0;
    private int deadlineProximityMinutes = 240;
    private boolean humanApprovalRequired = false;
    private boolean wasEscalated = false;
    private long tokensUsed = 4000L;
    private long actualLatencyMs = 18000L;
    private double targetCost = 0.0;

    public static CostFeature fromExecutionLog(ExecutionLog log) {
        if (log == null) {
            return new CostFeature();
        }
        return CostFeature.builder()
                .modelTier(log.getModelTier() == null ? "SONNET" : log.getModelTier())
                .priority(log.getPriority() == null ? 5 : log.getPriority())
                .requiredSkillTagCount(log.getRequiredSkillTagCount() == null ? 2 : log.getRequiredSkillTagCount())
                .descriptionLength(log.getDescriptionLength() == null ? 250 : log.getDescriptionLength())
                .confidence(log.getConfidence() == null ? 75 : log.getConfidence())
                .retryCount(log.getRetryCount() == null ? 0 : log.getRetryCount())
                .deadlineProximityMinutes(log.getDeadlineProximityMinutes() == null ? 240 : log.getDeadlineProximityMinutes())
                .humanApprovalRequired(Boolean.TRUE.equals(log.getHumanApprovalRequired()))
                .wasEscalated(Boolean.TRUE.equals(log.getWasEscalated()))
                .tokensUsed(log.getActualTokensUsed() == null ? 4000L : log.getActualTokensUsed())
                .actualLatencyMs(log.getActualLatencyMs() == null ? 18000L : log.getActualLatencyMs())
                .targetCost(log.getActualCostUsd() == null ? 0.0 : log.getActualCostUsd().doubleValue())
                .build();
    }

    public double[] toVector() {
        double tierValue = switch (modelTier == null ? "SONNET" : modelTier.toUpperCase()) {
            case "HAIKU" -> 0.0;
            case "SONNET" -> 0.5;
            case "OPUS" -> 1.0;
            default -> 0.5;
        };

        return new double[] {
                tierValue,
                priority / 10.0,
                requiredSkillTagCount / 10.0,
                descriptionLength / 1000.0,
                confidence / 100.0,
                retryCount / 10.0,
                deadlineProximityMinutes / 1440.0,
                humanApprovalRequired ? 1.0 : 0.0,
                wasEscalated ? 1.0 : 0.0,
                tokensUsed / 10000.0,
                actualLatencyMs / 60000.0
        };
    }
}

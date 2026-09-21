package com.synapse.analytics;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
public class SyntheticGoalGenerator {
    private static final String[] MODEL_TIERS = {"HAIKU", "SONNET", "OPUS"};

    public List<CostFeature> generateWorkload(int sampleCount) {
        return generateWorkload(sampleCount, 42L);
    }

    public List<CostFeature> generateWorkload(int sampleCount, long seed) {
        List<CostFeature> results = new ArrayList<>();
        Random random = new Random(seed);

        for (int i = 0; i < sampleCount; i++) {
            String modelTier = MODEL_TIERS[random.nextInt(MODEL_TIERS.length)];
            int priority = 1 + random.nextInt(10);
            int requiredSkillTagCount = 1 + random.nextInt(6);
            int descriptionLength = 120 + random.nextInt(900);
            int confidence = 45 + random.nextInt(55);
            int retryCount = random.nextInt(4);
            int deadlineProximityMinutes = random.nextInt(7 * 24 * 60);
            boolean humanApprovalRequired = random.nextDouble() < 0.22;
            boolean wasEscalated = random.nextDouble() < 0.12;

            long tokensUsed = estimateTokens(modelTier, descriptionLength, requiredSkillTagCount, priority);
            long actualLatencyMs = estimateLatency(modelTier, priority, confidence, retryCount);
            double targetCost = estimateTargetCost(modelTier, tokensUsed, priority, confidence, retryCount, humanApprovalRequired, wasEscalated, actualLatencyMs);

            results.add(CostFeature.builder()
                    .modelTier(modelTier)
                    .priority(priority)
                    .requiredSkillTagCount(requiredSkillTagCount)
                    .descriptionLength(descriptionLength)
                    .confidence(confidence)
                    .retryCount(retryCount)
                    .deadlineProximityMinutes(deadlineProximityMinutes)
                    .humanApprovalRequired(humanApprovalRequired)
                    .wasEscalated(wasEscalated)
                    .tokensUsed(tokensUsed)
                    .actualLatencyMs(actualLatencyMs)
                    .targetCost(targetCost)
                    .build());
        }
        return results;
    }

    private long estimateTokens(String modelTier, int descriptionLength, int requiredSkillTagCount, int priority) {
        double base = 1200.0 + descriptionLength * 3.5 + requiredSkillTagCount * 300 + priority * 180;
        double tierMultiplier = switch (modelTier) {
            case "HAIKU" -> 0.9;
            case "SONNET" -> 1.15;
            case "OPUS" -> 1.45;
            default -> 1.0;
        };
        return Math.max(1200L, Math.round(base * tierMultiplier));
    }

    private long estimateLatency(String modelTier, int priority, int confidence, int retryCount) {
        double multiplier = switch (modelTier) {
            case "HAIKU" -> 1.0;
            case "SONNET" -> 1.45;
            case "OPUS" -> 1.9;
            default -> 1.2;
        };
        double delay = 9000 + priority * 700 + (100 - confidence) * 110 + retryCount * 1800;
        return Math.round(delay * multiplier);
    }

    private double estimateTargetCost(String modelTier, long tokensUsed, int priority, int confidence, int retryCount,
                                     boolean humanApprovalRequired, boolean wasEscalated, long actualLatencyMs) {
        double tierMultiplier = switch (modelTier) {
            case "HAIKU" -> 0.45;
            case "SONNET" -> 0.8;
            case "OPUS" -> 1.3;
            default -> 0.7;
        };
        double complexity = tokensUsed * 0.00022 * tierMultiplier;
        double priorityPenalty = priority * 0.0025;
        double confidencePenalty = Math.max(0, 100 - confidence) * 0.0002;
        double retryPenalty = retryCount * 0.015;
        double approvalPenalty = humanApprovalRequired ? 0.03 : 0.0;
        double escalationPenalty = wasEscalated ? 0.06 : 0.0;
        double latencyPenalty = actualLatencyMs / 100000.0 * 0.02;
        return Math.max(0.004, complexity + priorityPenalty + confidencePenalty + retryPenalty + approvalPenalty + escalationPenalty + latencyPenalty);
    }
}

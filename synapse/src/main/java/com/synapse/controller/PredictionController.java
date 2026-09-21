package com.synapse.controller;

import com.synapse.analytics.CostFeature;
import com.synapse.analytics.CostPredictionService;
import com.synapse.api.request.CostPredictionRequest;
import com.synapse.api.response.CostPredictionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/predict")
@RequiredArgsConstructor
public class PredictionController {
    private final CostPredictionService costPredictionService;

    @PostMapping("/cost")
    public ResponseEntity<CostPredictionResponse> predictCost(@RequestBody CostPredictionRequest request) {
        CostFeature feature = CostFeature.builder()
                .modelTier(request.getModelTier() == null ? "SONNET" : request.getModelTier())
                .priority(request.getPriority() == null ? 5 : request.getPriority())
                .requiredSkillTagCount(request.getRequiredSkillTagCount() == null ? 2 : request.getRequiredSkillTagCount())
                .descriptionLength(request.getDescriptionLength() == null ? 300 : request.getDescriptionLength())
                .confidence(request.getConfidence() == null ? 75 : request.getConfidence())
                .retryCount(request.getRetryCount() == null ? 0 : request.getRetryCount())
                .deadlineProximityMinutes(request.getDeadlineProximityMinutes() == null ? 240 : request.getDeadlineProximityMinutes())
                .humanApprovalRequired(Boolean.TRUE.equals(request.getHumanApprovalRequired()))
                .wasEscalated(Boolean.TRUE.equals(request.getWasEscalated()))
                .tokensUsed(request.getTokensUsed() == null ? 4000L : request.getTokensUsed())
                .actualLatencyMs(request.getActualLatencyMs() == null ? 18000L : request.getActualLatencyMs())
                .build();

        CostPredictionResponse response = costPredictionService.predict(feature);
        return ResponseEntity.ok(response);
    }
}

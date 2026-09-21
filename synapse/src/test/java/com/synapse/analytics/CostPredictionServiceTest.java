package com.synapse.analytics;

import com.synapse.api.response.CostPredictionResponse;
import com.synapse.core.repository.ExecutionLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CostPredictionServiceTest {

    @Mock
    private ExecutionLogRepository executionLogRepository;

    @Test
    void syntheticGeneratorProducesRequestedSamples() {
        SyntheticGoalGenerator generator = new SyntheticGoalGenerator();
        List<CostFeature> workload = generator.generateWorkload(250, 7L);

        assertEquals(250, workload.size());
        assertTrue(workload.stream().allMatch(sample -> sample.getTargetCost() > 0.0));
    }

    @Test
    void predictionServiceReturnsPositivePrediction() {
        when(executionLogRepository.findAll()).thenReturn(List.of());

        CostPredictionService service = new CostPredictionService(
                executionLogRepository,
                new SyntheticGoalGenerator(),
                new HistoricalAverageCostModel(),
                new LinearRegressionCostModel(),
                new TreeBasedCostModel()
        );

        CostPredictionResponse response = service.predict(CostFeature.builder()
                .modelTier("SONNET")
                .priority(6)
                .requiredSkillTagCount(3)
                .descriptionLength(450)
                .confidence(72)
                .retryCount(1)
                .deadlineProximityMinutes(180)
                .humanApprovalRequired(false)
                .wasEscalated(false)
                .tokensUsed(6000L)
                .actualLatencyMs(22000L)
                .build());

        assertNotNull(response);
        assertTrue(response.getPredictedCostUsd() > 0.0);
        assertTrue(response.getTrainingRows() >= 220);
    }
}

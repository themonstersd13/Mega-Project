package com.synapse.llm.provider;

import com.synapse.llm.dto.LlmResponse;

import java.math.BigDecimal;

public class MockLlmProvider implements LlmProvider {
    @Override
    public LlmResponse execute(String prompt) {
        return LlmResponse.builder()
                .result("Mock result for: " + prompt)
                .inputTokens(100L)
                .outputTokens(150L)
                .costUsd(BigDecimal.valueOf(0.0008))
                .build();
    }
}

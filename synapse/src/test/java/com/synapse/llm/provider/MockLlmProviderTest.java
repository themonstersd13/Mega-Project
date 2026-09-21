package com.synapse.llm.provider;

import com.synapse.llm.dto.LlmResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MockLlmProviderTest {

    @Test
    void testMockLlmProviderResponse() {
        MockLlmProvider provider = new MockLlmProvider();
        LlmResponse response = provider.execute("Test prompt");

        assertNotNull(response);
        assertNotNull(response.getResult());
        assertEquals(100L, response.getInputTokens());
        assertEquals(150L, response.getOutputTokens());
        assertEquals(BigDecimal.valueOf(0.0008), response.getCostUsd());
    }

    @Test
    void testMockLlmProviderVaryingPrompts() {
        MockLlmProvider provider = new MockLlmProvider();
        
        LlmResponse response1 = provider.execute("Prompt 1");
        LlmResponse response2 = provider.execute("Prompt 2");

        assertEquals(response1.getInputTokens(), response2.getInputTokens());
        assertEquals(response1.getOutputTokens(), response2.getOutputTokens());
        assertEquals(response1.getCostUsd(), response2.getCostUsd());
    }
}

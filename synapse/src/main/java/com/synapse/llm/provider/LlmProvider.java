package com.synapse.llm.provider;

import com.synapse.llm.dto.LlmResponse;

public interface LlmProvider {
    LlmResponse execute(String prompt);
}

package com.synapse.llm.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.llm.dto.LlmResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;

@Component
public class AnthropicProvider implements LlmProvider {

    @Value("${llm.anthropic.api-key:}")
    private String apiKey;

    @Value("${llm.anthropic.model:claude-3-5-haiku-20241022}")
    private String model;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public AnthropicProvider(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.baseUrl("https://api.anthropic.com").build();
        this.objectMapper = objectMapper;
    }

    @Override
    public LlmResponse execute(String prompt) {
        try {
            if (apiKey == null || apiKey.isEmpty()) {
                return createMockResponse(prompt);
            }

            String requestBody = buildRequestBody(prompt);

            String response = webClient
                    .post()
                    .uri("/v1/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("content-type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseResponse(response);
        } catch (Exception e) {
            throw new RuntimeException("LLM execution failed", e);
        }
    }

    private String buildRequestBody(String prompt) throws Exception {
        return objectMapper.writeValueAsString(
                objectMapper.createObjectNode()
                        .put("model", model)
                        .put("max_tokens", 1024)
                        .putArray("messages")
                        .addObject()
                        .put("role", "user")
                        .put("content", prompt)
        );
    }

    private LlmResponse parseResponse(String responseJson) throws Exception {
        JsonNode root = objectMapper.readTree(responseJson);
        
        String result = root.path("content")
                .get(0)
                .path("text")
                .asText();
        
        Long inputTokens = root.path("usage").path("input_tokens").asLong(0L);
        Long outputTokens = root.path("usage").path("output_tokens").asLong(0L);
        
        // Pricing: Input: $0.80 per MTok, Output: $4.00 per MTok
        BigDecimal inputCost = BigDecimal.valueOf(inputTokens * 0.80 / 1_000_000.0);
        BigDecimal outputCost = BigDecimal.valueOf(outputTokens * 4.00 / 1_000_000.0);
        BigDecimal totalCost = inputCost.add(outputCost);

        LlmResponse response = new LlmResponse();
        response.setResult(result);
        response.setInputTokens(inputTokens);
        response.setOutputTokens(outputTokens);
        response.setCostUsd(totalCost);
        return response;
    }

    private LlmResponse createMockResponse(String prompt) {
        LlmResponse response = new LlmResponse();
        response.setResult("Task executed successfully: " + prompt);
        response.setInputTokens(50L);
        response.setOutputTokens(100L);
        response.setCostUsd(BigDecimal.valueOf(0.0004));
        return response;
    }
}

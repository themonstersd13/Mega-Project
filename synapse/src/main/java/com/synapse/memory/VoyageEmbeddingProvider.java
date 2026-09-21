package com.synapse.memory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class VoyageEmbeddingProvider implements EmbeddingProvider {
    private final WebClient webClient;

    @Value("${voyage.api-key:demo-key}")
    private String apiKey;

    public VoyageEmbeddingProvider(WebClient.Builder builder) {
        this.webClient = builder
                .baseUrl("https://api.voyageai.com")
                .build();
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[1024];
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("input", List.of(text));
            payload.put("model", "voyage-3");

            Map<String, Object> response = webClient.post()
                    .uri("/v1/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || response.get("data") == null) {
                return new float[1024];
            }

            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
            if (data == null || data.isEmpty()) {
                return new float[1024];
            }

            List<Object> embedding = (List<Object>) data.get(0).get("embedding");
            float[] vector = new float[Math.max(1024, embedding.size())];
            for (int i = 0; i < embedding.size(); i++) {
                Object value = embedding.get(i);
                vector[i] = value instanceof Number ? ((Number) value).floatValue() : 0f;
            }
            return vector;
        } catch (Exception ex) {
            return new float[1024];
        }
    }
}

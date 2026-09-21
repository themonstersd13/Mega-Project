package com.synapse.governance;

import com.synapse.core.entity.DeadLetterEntry;
import com.synapse.core.repository.DeadLetterEntryRepository;
import com.synapse.llm.dto.LlmResponse;
import com.synapse.llm.provider.LlmProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Component
@Primary
@RequiredArgsConstructor
public class ResilientLlmGateway implements LlmProvider {
    @Qualifier("anthropicProvider")
    private final LlmProvider delegate;
    private final DeadLetterEntryRepository deadLetterEntryRepository;

    @Override
    public LlmResponse execute(String prompt) {
        RuntimeException failure = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return delegate.execute(prompt);
            } catch (RuntimeException ex) {
                failure = ex;
                try {
                    Thread.sleep((long) Math.pow(2, attempt) * 100L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        DeadLetterEntry entry = DeadLetterEntry.builder()
                .id(UUID.randomUUID())
                .originalPrompt(prompt)
                .failureReason(failure == null ? "Unknown failure" : failure.getMessage())
                .failedAt(Instant.now())
                .resolved(false)
                .build();
        deadLetterEntryRepository.save(entry);

        return LlmResponse.builder()
                .result("LLM execution failed and was dead-lettered")
                .inputTokens(0L)
                .outputTokens(0L)
                .costUsd(BigDecimal.ZERO)
                .build();
    }
}

package com.synapse.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionLogResponse {
    private UUID id;
    private UUID taskId;
    private Long actualTokensUsed;
    private BigDecimal actualCostUsd;
    private Long actualLatencyMs;
    private Boolean succeeded;
    private Instant createdAt;
}

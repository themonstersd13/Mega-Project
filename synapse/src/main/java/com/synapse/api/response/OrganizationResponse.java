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
public class OrganizationResponse {
    private UUID id;
    private String name;
    private String description;
    private BigDecimal totalBudgetUsd;
    private BigDecimal spentBudgetUsd;
    private Instant createdAt;
    private Instant updatedAt;
}

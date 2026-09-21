package com.synapse.api.response;

import com.synapse.core.model.TaskItemStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskItemResponse {
    private UUID id;
    private UUID goalId;
    private UUID organizationId;
    private UUID parentTaskId;
    private String description;
    private TaskItemStatus status;
    private UUID assignedAgentId;
    private String assignedAgentName;
    private Integer priority;
    private Integer confidence;
    private List<String> requiredSkillTags;
    private BigDecimal estimatedCostUsd;
    private BigDecimal actualCostUsd;
    private Long actualLatencyMs;
    private Instant createdAt;
    private Instant updatedAt;
}

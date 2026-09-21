package com.synapse.api.response;

import com.synapse.core.model.GoalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoalResponse {
    private UUID id;
    private String description;
    private GoalStatus status;
    private List<TaskItemResponse> tasks;
    private Instant createdAt;
    private Instant updatedAt;
}

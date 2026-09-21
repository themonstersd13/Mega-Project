package com.synapse.api.request;

import com.synapse.core.model.TaskItemStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskUpdateRequest {
    private TaskItemStatus status;
    private UUID assignedAgentId;
}

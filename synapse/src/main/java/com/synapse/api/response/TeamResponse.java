package com.synapse.api.response;

import com.synapse.core.model.TeamTopology;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamResponse {
    private UUID id;
    private String name;
    private TeamTopology topology;
    private UUID leadAgentId;
    private Instant createdAt;
    private Instant updatedAt;
}

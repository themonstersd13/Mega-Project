package com.synapse.api.request;

import com.synapse.core.model.TeamTopology;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTeamRequest {
    private String name;
    private UUID leadAgentId;
    private TeamTopology teamTopology;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getLeadAgentId() {
        return leadAgentId;
    }

    public void setLeadAgentId(UUID leadAgentId) {
        this.leadAgentId = leadAgentId;
    }

    public TeamTopology getTeamTopology() {
        return teamTopology;
    }

    public void setTeamTopology(TeamTopology teamTopology) {
        this.teamTopology = teamTopology;
    }
}

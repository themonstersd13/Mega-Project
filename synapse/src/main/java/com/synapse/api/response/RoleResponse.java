package com.synapse.api.response;

import com.synapse.core.model.ModelTier;
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
public class RoleResponse {
    private UUID id;
    private String name;
    private String systemPrompt;
    private ModelTier modelTier;
    private Long tokenBudget;
    private List<String> responsibilities;
    private Instant createdAt;
    private Instant updatedAt;
}

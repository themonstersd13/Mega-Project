package com.synapse.api.request;

import com.synapse.core.model.ModelTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRoleRequest {
    private String name;
    private List<String> responsibilities;
    private String systemPrompt;
    private ModelTier modelTier;
    private Long tokenBudget;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getResponsibilities() {
        return responsibilities;
    }

    public void setResponsibilities(List<String> responsibilities) {
        this.responsibilities = responsibilities;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public ModelTier getModelTier() {
        return modelTier;
    }

    public void setModelTier(ModelTier modelTier) {
        this.modelTier = modelTier;
    }

    public Long getTokenBudget() {
        return tokenBudget;
    }

    public void setTokenBudget(Long tokenBudget) {
        this.tokenBudget = tokenBudget;
    }
}

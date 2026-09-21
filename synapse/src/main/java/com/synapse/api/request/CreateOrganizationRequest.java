package com.synapse.api.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrganizationRequest {
    private String name;
    private String description;
    private BigDecimal totalBudgetUsd;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getTotalBudgetUsd() {
        return totalBudgetUsd;
    }

    public void setTotalBudgetUsd(BigDecimal totalBudgetUsd) {
        this.totalBudgetUsd = totalBudgetUsd;
    }
}

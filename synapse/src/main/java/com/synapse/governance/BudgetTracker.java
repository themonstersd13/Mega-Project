package com.synapse.governance;

import com.synapse.core.entity.Organization;
import com.synapse.core.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class BudgetTracker {
    private final OrganizationRepository organizationRepository;

    public BudgetStatus checkBudget(Organization organization, BigDecimal estimatedCost) {
        if (organization == null) {
            return BudgetStatus.OK;
        }
        BigDecimal totalBudget = organization.getTotalBudgetUsd() == null ? BigDecimal.ZERO : organization.getTotalBudgetUsd();
        BigDecimal spent = organization.getSpentBudgetUsd() == null ? BigDecimal.ZERO : organization.getSpentBudgetUsd();
        BigDecimal projected = spent.add(estimatedCost == null ? BigDecimal.ZERO : estimatedCost);

        if (projected.compareTo(totalBudget) > 0) {
            return BudgetStatus.EXCEEDED;
        }
        if (projected.compareTo(totalBudget.multiply(new BigDecimal("0.8"))) >= 0) {
            return BudgetStatus.AT_THRESHOLD;
        }
        return BudgetStatus.OK;
    }

    @Transactional
    public Organization applySpend(Organization organization, BigDecimal amount) {
        if (organization == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return organization;
        }
        organization.setSpentBudgetUsd((organization.getSpentBudgetUsd() == null ? BigDecimal.ZERO : organization.getSpentBudgetUsd()).add(amount));
        return organizationRepository.save(organization);
    }
}

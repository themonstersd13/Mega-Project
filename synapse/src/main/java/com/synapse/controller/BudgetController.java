package com.synapse.controller;

import com.synapse.core.entity.Organization;
import com.synapse.core.repository.OrganizationRepository;
import com.synapse.governance.BudgetStatus;
import com.synapse.governance.BudgetTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/organizations")
@RequiredArgsConstructor
public class BudgetController {
    private final OrganizationRepository organizationRepository;
    private final BudgetTracker budgetTracker;

    @GetMapping("/{id}/budget")
    public ResponseEntity<Map<String, Object>> getBudget(@PathVariable UUID id) {
        Organization organization = organizationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + id));

        BudgetStatus status = budgetTracker.checkBudget(organization, BigDecimal.ZERO);
        Map<String, Object> payload = new HashMap<>();
        payload.put("organizationId", organization.getId());
        payload.put("name", organization.getName());
        payload.put("totalBudgetUsd", organization.getTotalBudgetUsd());
        payload.put("spentBudgetUsd", organization.getSpentBudgetUsd());
        payload.put("status", status.name());
        return ResponseEntity.ok(payload);
    }
}

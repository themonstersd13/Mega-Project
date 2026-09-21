package com.synapse.governance;

import com.synapse.core.entity.Approval;
import com.synapse.core.entity.Organization;
import com.synapse.core.entity.TaskItem;
import com.synapse.core.model.ApprovalEffectType;
import com.synapse.core.repository.ApprovalRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GovernanceServiceTest {

    @Test
    void budgetTrackerShouldFlagBudgetThresholds() {
        Organization organization = Organization.builder()
                .id(UUID.randomUUID())
                .name("Acme")
                .totalBudgetUsd(new BigDecimal("100.00"))
                .spentBudgetUsd(new BigDecimal("80.00"))
                .build();

        BudgetTracker tracker = new BudgetTracker(mock(com.synapse.core.repository.OrganizationRepository.class));

        assertEquals(BudgetStatus.AT_THRESHOLD, tracker.checkBudget(organization, new BigDecimal("20.00")));
        assertEquals(BudgetStatus.EXCEEDED, tracker.checkBudget(organization, new BigDecimal("30.00")));
    }

    @Test
    void approvalGateShouldRequireApprovalForHighRiskSpend() {
        ApprovalRepository approvalRepository = mock(ApprovalRepository.class);
        when(approvalRepository.save(any(Approval.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(approvalRepository.findById(any(UUID.class))).thenAnswer(invocation -> Optional.of(Approval.builder()
                .id(invocation.getArgument(0))
                .actionDescription("Spend request")
                .status(com.synapse.core.model.ApprovalStatus.PENDING)
                .build()));

        ApprovalGate gate = new ApprovalGate(approvalRepository);
        TaskItem task = TaskItem.builder()
                .id(UUID.randomUUID())
                .description("External data lookup that requires approval")
                .confidence(65)
                .build();

        ApprovalResult result = gate.evaluate(task, 65, ApprovalEffectType.SPEND);

        assertTrue(result.requiresApproval());
        assertNotNull(result.approvalId());

        Approval resolved = gate.resolve(result.approvalId(), ApprovalDecision.APPROVED, "operator");
        assertEquals(com.synapse.core.model.ApprovalStatus.APPROVED, resolved.getStatus());
        assertEquals("operator", resolved.getResolvedBy());
    }
}

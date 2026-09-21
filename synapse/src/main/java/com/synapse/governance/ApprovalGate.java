package com.synapse.governance;

import com.synapse.core.entity.Approval;
import com.synapse.core.entity.TaskItem;
import com.synapse.core.model.ApprovalEffectType;
import com.synapse.core.model.ApprovalStatus;
import com.synapse.core.repository.ApprovalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApprovalGate {
    private final ApprovalRepository approvalRepository;

    public ApprovalResult evaluate(TaskItem task, int confidence, ApprovalEffectType effectType) {
        if (task == null) {
            return ApprovalResult.notRequired();
        }

        boolean requireApproval = effectType == ApprovalEffectType.EXTERNAL
                || effectType == ApprovalEffectType.SPEND
                || confidence < 70;

        if (!requireApproval) {
            return ApprovalResult.notRequired();
        }

        Approval approval = Approval.builder()
                .id(UUID.randomUUID())
                .task(task)
                .actionDescription(task.getDescription())
                .effectType(effectType)
                .confidence(confidence)
                .status(ApprovalStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        approvalRepository.save(approval);
        return ApprovalResult.required(approval.getId());
    }

    @Transactional
    public Approval resolve(UUID approvalId, ApprovalDecision decision, String resolvedBy) {
        Approval approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new IllegalArgumentException("Approval not found: " + approvalId));

        approval.setStatus(decision == ApprovalDecision.APPROVED ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED);
        approval.setResolvedBy(resolvedBy);
        approval.setResolvedAt(Instant.now());
        return approvalRepository.save(approval);
    }
}

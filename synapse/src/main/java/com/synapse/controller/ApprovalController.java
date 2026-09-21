package com.synapse.controller;

import com.synapse.api.request.ResolveApprovalRequest;
import com.synapse.core.entity.Approval;
import com.synapse.core.repository.ApprovalRepository;
import com.synapse.governance.ApprovalGate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/approvals")
@RequiredArgsConstructor
public class ApprovalController {
    private final ApprovalGate approvalGate;
    private final ApprovalRepository approvalRepository;

    @GetMapping("/pending")
    public List<Approval> pendingApprovals() {
        return approvalRepository.findByStatus(com.synapse.core.model.ApprovalStatus.PENDING);
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<Approval> resolveApproval(
            @PathVariable UUID id,
            @RequestBody ResolveApprovalRequest request) {
        Approval approval = approvalGate.resolve(id, request.getDecision(), request.getResolvedBy());
        return ResponseEntity.ok(approval);
    }
}

package com.synapse.governance;

import java.util.UUID;

public record ApprovalResult(boolean requiresApproval, UUID approvalId) {
    public static ApprovalResult notRequired() {
        return new ApprovalResult(false, null);
    }

    public static ApprovalResult required(UUID approvalId) {
        return new ApprovalResult(true, approvalId);
    }
}

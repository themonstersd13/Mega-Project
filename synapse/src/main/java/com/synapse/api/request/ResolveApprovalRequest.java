package com.synapse.api.request;

import com.synapse.governance.ApprovalDecision;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolveApprovalRequest {
    private ApprovalDecision decision;
    private String resolvedBy;
}

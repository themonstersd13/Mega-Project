package com.synapse.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {
    private UUID id;
    private UUID runId;
    private UUID traceId;
    private UUID parentMessageId;
    private UUID senderAgentId;
    private UUID recipientAgentId;
    private String content;
    private Integer confidence;
    private Instant sentAt;
}

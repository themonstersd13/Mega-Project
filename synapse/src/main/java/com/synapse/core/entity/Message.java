package com.synapse.core.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Data
@Getter
@Setter
@Builder
@Entity
@Table(name = "message")
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    @Id
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "trace_id", nullable = false)
    private UUID traceId;

    @ManyToOne
    @JoinColumn(name = "parent_message_id")
    private Message parentMessage;

    @ManyToOne
    @JoinColumn(name = "sender_agent_id", nullable = false)
    private Agent senderAgent;

    @ManyToOne
    @JoinColumn(name = "recipient_agent_id")
    private Agent recipientAgent;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "confidence")
    private Integer confidence;

    @Column(name = "sent_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant sentAt;

    @PrePersist
    protected void onCreate() {
        if (sentAt == null) sentAt = Instant.now();
    }
}

package com.synapse.controller;

import com.synapse.api.response.MessageResponse;
import com.synapse.core.entity.Message;
import com.synapse.core.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
public class MessageController {
    private final MessageService messageService;

    @GetMapping("/run/{runId}")
    public ResponseEntity<List<MessageResponse>> byRun(@PathVariable UUID runId) {
        return ResponseEntity.ok(messageService.findByRunId(runId).stream().map(this::toResponse).collect(Collectors.toList()));
    }

    @GetMapping("/trace/{traceId}")
    public ResponseEntity<List<MessageResponse>> byTrace(@PathVariable UUID traceId) {
        return ResponseEntity.ok(messageService.findByTraceId(traceId).stream().map(this::toResponse).collect(Collectors.toList()));
    }

    private MessageResponse toResponse(Message message) {
        return MessageResponse.builder()
                .id(message.getId())
                .runId(message.getRunId())
                .traceId(message.getTraceId())
                .parentMessageId(message.getParentMessage() == null ? null : message.getParentMessage().getId())
                .senderAgentId(message.getSenderAgent() == null ? null : message.getSenderAgent().getId())
                .recipientAgentId(message.getRecipientAgent() == null ? null : message.getRecipientAgent().getId())
                .content(message.getContent())
                .confidence(message.getConfidence())
                .sentAt(message.getSentAt())
                .build();
    }
}

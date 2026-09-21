package com.synapse.controller;

import com.synapse.core.entity.MemoryEntry;
import com.synapse.core.entity.MemoryRelation;
import com.synapse.memory.MemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/memory")
@RequiredArgsConstructor
public class MemoryController {
    private final MemoryService memoryService;

    @GetMapping("/decisions/{memoryId}/explain")
    public ResponseEntity<Map<String, Object>> explain(@PathVariable UUID memoryId) {
        MemoryService.ExplainResult result = memoryService.explainWhy(memoryId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("decision", toMemoryMap(result.decision()));
        payload.put("evidence", result.evidence().stream().map(this::toMemoryMap).collect(Collectors.toList()));
        payload.put("alternatives", result.alternatives().stream().map(this::toMemoryMap).collect(Collectors.toList()));
        payload.put("relations", result.relations().stream().map(this::toRelationMap).collect(Collectors.toList()));
        return ResponseEntity.ok(payload);
    }

    private Map<String, Object> toMemoryMap(MemoryEntry memory) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", memory.getId());
        payload.put("organizationId", memory.getOrganization() == null ? null : memory.getOrganization().getId());
        payload.put("agentId", memory.getAgent() == null ? null : memory.getAgent().getId());
        payload.put("taskId", memory.getTask() == null ? null : memory.getTask().getId());
        payload.put("content", memory.getContent());
        payload.put("scope", memory.getScope());
        payload.put("confidence", memory.getConfidence());
        payload.put("superseded", memory.getSuperseded());
        payload.put("createdAt", memory.getCreatedAt());
        return payload;
    }

    private Map<String, Object> toRelationMap(MemoryRelation relation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", relation.getId());
        payload.put("fromMemoryId", relation.getFromMemory() == null ? null : relation.getFromMemory().getId());
        payload.put("toMemoryId", relation.getToMemory() == null ? null : relation.getToMemory().getId());
        payload.put("relationType", relation.getRelationType());
        payload.put("createdAt", relation.getCreatedAt());
        return payload;
    }
}

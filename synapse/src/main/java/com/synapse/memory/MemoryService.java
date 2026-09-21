package com.synapse.memory;

import com.synapse.core.entity.Agent;
import com.synapse.core.entity.MemoryEntry;
import com.synapse.core.entity.MemoryRelation;
import com.synapse.core.entity.Organization;
import com.synapse.core.entity.TaskItem;
import com.synapse.core.model.MemoryRelationType;
import com.synapse.core.model.MemoryScope;
import com.synapse.core.repository.MemoryEntryRepository;
import com.synapse.core.repository.MemoryRelationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MemoryService {
    private final MemoryEntryRepository memoryEntryRepository;
    private final MemoryRelationRepository memoryRelationRepository;
    private final EmbeddingProvider embeddingProvider;

    public MemoryEntry store(String content, Organization organization, Agent agent, TaskItem task, MemoryScope scope, int confidence) {
        MemoryEntry entry = MemoryEntry.builder()
                .id(UUID.randomUUID())
                .organization(organization)
                .agent(agent)
                .task(task)
                .scope(scope)
                .content(content)
                .embedding(embeddingProvider.embed(content))
                .confidence(confidence)
                .superseded(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        return memoryEntryRepository.save(entry);
    }

    public List<MemoryEntry> recall(String query, Organization organization, Agent agent, MemoryScope scope, int limit) {
        float[] queryVector = embeddingProvider.embed(query);
        List<MemoryEntry> candidates = memoryEntryRepository.findAll();

        return candidates.stream()
                .filter(memory -> matchesScope(memory, organization, agent, scope))
                .sorted(Comparator.comparingDouble(memory -> -cosineSimilarity(queryVector, memory.getEmbedding())))
                .limit(limit)
                .toList();
    }

    public MemoryRelation relate(UUID fromId, UUID toId, MemoryRelationType relationType) {
        MemoryEntry from = memoryEntryRepository.findById(fromId)
                .orElseThrow(() -> new IllegalArgumentException("Source memory not found: " + fromId));
        MemoryEntry to = memoryEntryRepository.findById(toId)
                .orElseThrow(() -> new IllegalArgumentException("Target memory not found: " + toId));

        MemoryRelation relation = MemoryRelation.builder()
                .id(UUID.randomUUID())
                .fromMemory(from)
                .toMemory(to)
                .relationType(relationType)
                .createdAt(Instant.now())
                .build();

        if (relationType == MemoryRelationType.SUPERSEDES && to != null) {
            to.setSuperseded(true);
            memoryEntryRepository.save(to);
        }

        return memoryRelationRepository.save(relation);
    }

    public ExplainResult explainWhy(UUID memoryId) {
        MemoryEntry decision = memoryEntryRepository.findById(memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found: " + memoryId));

        List<MemoryRelation> relations = memoryRelationRepository.findByToMemoryId(memoryId);
        List<MemoryEntry> evidence = new ArrayList<>();
        List<MemoryEntry> alternatives = new ArrayList<>();

        for (MemoryRelation relation : relations) {
            if (relation.getRelationType() == MemoryRelationType.EVIDENCE) {
                evidence.add(relation.getFromMemory());
            }
            if (relation.getRelationType() == MemoryRelationType.CONSIDERED_ALTERNATIVE) {
                alternatives.add(relation.getFromMemory());
            }
        }

        return new ExplainResult(decision, evidence, alternatives, relations);
    }

    private boolean matchesScope(MemoryEntry memory, Organization organization, Agent agent, MemoryScope requestedScope) {
        if (organization == null || memory.getOrganization() == null) {
            return false;
        }
        if (memory.getOrganization().getId() == null || !memory.getOrganization().getId().equals(organization.getId())) {
            return false;
        }

        if (requestedScope == null) {
            return true;
        }

        return switch (requestedScope) {
            case PRIVATE -> agent != null && memory.getAgent() != null && memory.getAgent().getId().equals(agent.getId());
            case TEAM -> agent != null && memory.getAgent() != null && memory.getAgent().getTeam() != null && agent.getTeam() != null && memory.getAgent().getTeam().getId().equals(agent.getTeam().getId());
            case ORG -> true;
        };
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0) {
            return 0.0;
        }
        int maxLen = Math.min(a.length, b.length);
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < maxLen; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        if (denominator == 0.0) {
            return 0.0;
        }
        return dot / denominator;
    }

    public record ExplainResult(MemoryEntry decision, List<MemoryEntry> evidence, List<MemoryEntry> alternatives, List<MemoryRelation> relations) {
    }
}

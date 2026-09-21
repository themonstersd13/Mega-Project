package com.synapse.memory;

import com.synapse.core.entity.Agent;
import com.synapse.core.entity.MemoryEntry;
import com.synapse.core.entity.MemoryRelation;
import com.synapse.core.entity.Organization;
import com.synapse.core.model.MemoryRelationType;
import com.synapse.core.model.MemoryScope;
import com.synapse.core.repository.MemoryEntryRepository;
import com.synapse.core.repository.MemoryRelationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryServiceTest {
    @Mock
    private MemoryEntryRepository memoryEntryRepository;

    @Mock
    private MemoryRelationRepository memoryRelationRepository;

    @Mock
    private EmbeddingProvider embeddingProvider;

    @InjectMocks
    private MemoryService memoryService;

    @Test
    void shouldStoreAndExplainDecision() {
        Organization org = new Organization();
        org.setId(UUID.randomUUID());
        org.setName("Org");

        Agent agent = new Agent();
        agent.setId(UUID.randomUUID());

        MemoryEntry decision = MemoryEntry.builder()
                .id(UUID.randomUUID())
                .organization(org)
                .agent(agent)
                .content("Chosen plan")
                .scope(MemoryScope.ORG)
                .embedding(new float[1024])
                .build();

        MemoryEntry evidence = MemoryEntry.builder()
                .id(UUID.randomUUID())
                .organization(org)
                .agent(agent)
                .content("Evidence")
                .scope(MemoryScope.ORG)
                .embedding(new float[1024])
                .build();

        when(embeddingProvider.embed(any())).thenReturn(new float[1024]);
        when(memoryEntryRepository.save(any(MemoryEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        UUID savedDecisionId = UUID.randomUUID();
        MemoryEntry savedDecision = MemoryEntry.builder()
                .id(savedDecisionId)
                .organization(org)
                .agent(agent)
                .content("Chosen plan")
                .scope(MemoryScope.ORG)
                .embedding(new float[1024])
                .build();

        when(memoryEntryRepository.findById(savedDecisionId)).thenReturn(Optional.of(savedDecision));
        when(memoryRelationRepository.findByToMemoryId(savedDecisionId)).thenReturn(List.of(
                MemoryRelation.builder()
                        .id(UUID.randomUUID())
                        .fromMemory(evidence)
                        .toMemory(savedDecision)
                        .relationType(MemoryRelationType.EVIDENCE)
                        .build()
        ));

        MemoryEntry saved = memoryService.store("Chosen plan", org, agent, null, MemoryScope.ORG, 90);
        saved.setId(savedDecisionId);
        MemoryService.ExplainResult result = memoryService.explainWhy(savedDecisionId);

        assertNotNull(saved);
        assertEquals(1, result.evidence().size());
        assertEquals("Evidence", result.evidence().get(0).getContent());
    }
}

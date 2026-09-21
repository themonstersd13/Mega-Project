package com.synapse.core.service;

import com.synapse.core.entity.Goal;
import com.synapse.core.entity.Organization;
import com.synapse.core.entity.TaskItem;
import com.synapse.core.model.GoalStatus;
import com.synapse.core.model.TaskItemStatus;
import com.synapse.core.repository.TaskItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DecomposerServiceTest {

    @Mock
    private TaskItemRepository taskItemRepository;

    @InjectMocks
    private DecomposerService decomposerService;

    private Goal testGoal;
    private Organization testOrg;

    @BeforeEach
    void setUp() {
        testOrg = Organization.builder()
                .id(UUID.randomUUID())
                .name("Test Org")
                .createdAt(Instant.now())
                .build();

        testGoal = Goal.builder()
                .id(UUID.randomUUID())
                .organization(testOrg)
                .description("Complete task: Write a test")
                .status(GoalStatus.PENDING)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void testDecomposeGoal() {
        when(taskItemRepository.save(any(TaskItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<TaskItem> tasks = decomposerService.decomposeGoal(testGoal);

        assertNotNull(tasks);
        assertEquals(3, tasks.size());
        assertEquals(TaskItemStatus.PENDING, tasks.get(0).getStatus());
        assertEquals(TaskItemStatus.PENDING, tasks.get(1).getStatus());
        assertEquals(TaskItemStatus.PENDING, tasks.get(2).getStatus());
        verify(taskItemRepository, times(3)).save(any(TaskItem.class));
    }

    @Test
    void testDecomposeGoalCreatesUniqueTaskId() {
        when(taskItemRepository.save(any(TaskItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<TaskItem> tasks1 = decomposerService.decomposeGoal(testGoal);
        List<TaskItem> tasks2 = decomposerService.decomposeGoal(testGoal);

        assertNotNull(tasks1.get(0).getId());
        assertNotNull(tasks2.get(0).getId());
        assertNotNull(tasks1.get(1).getId());
        assertNotNull(tasks1.get(2).getId());
    }

    @Test
    void testDecomposeGoalTaskBelongsToGoal() {
        when(taskItemRepository.save(any(TaskItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<TaskItem> tasks = decomposerService.decomposeGoal(testGoal);

        assertEquals(testGoal.getId(), tasks.get(0).getGoal().getId());
        assertEquals(testGoal.getId(), tasks.get(1).getGoal().getId());
        assertEquals(testGoal.getId(), tasks.get(2).getGoal().getId());
        assertEquals(testOrg.getId(), tasks.get(0).getOrganization().getId());
    }
}

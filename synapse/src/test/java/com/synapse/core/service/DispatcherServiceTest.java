package com.synapse.core.service;

import com.synapse.core.entity.Agent;
import com.synapse.core.entity.ExecutionLog;
import com.synapse.core.entity.Organization;
import com.synapse.core.entity.TaskItem;
import com.synapse.core.model.TaskItemStatus;
import com.synapse.core.repository.ExecutionLogRepository;
import com.synapse.core.repository.TaskItemRepository;
import com.synapse.llm.dto.LlmResponse;
import com.synapse.llm.provider.LlmProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DispatcherServiceTest {

    @Mock
    private LlmProvider llmProvider;

    @Mock
    private ExecutionLogRepository executionLogRepository;

    @Mock
    private TaskItemRepository taskItemRepository;

    @Mock
    private MessageService messageService;

    @InjectMocks
    private DispatcherService dispatcherService;

    private TaskItem testTask;
    private Agent testAgent;
    private Organization testOrg;

    @BeforeEach
    void setUp() {
        testOrg = Organization.builder()
                .id(UUID.randomUUID())
                .name("Test Org")
                .createdAt(Instant.now())
                .build();

        testTask = TaskItem.builder()
                .id(UUID.randomUUID())
                .organization(testOrg)
                .description("Test task description")
                .status(TaskItemStatus.PENDING)
                .retryCount(0)
                .build();

        testAgent = Agent.builder()
                .id(UUID.randomUUID())
                .build();
    }

    @Test
    void testDispatchTaskSuccess() {
        LlmResponse mockResponse = LlmResponse.builder()
                .result("Task completed successfully")
                .inputTokens(100L)
                .outputTokens(150L)
                .costUsd(BigDecimal.valueOf(0.0008))
                .build();

        when(llmProvider.execute(anyString())).thenReturn(mockResponse);
        when(executionLogRepository.save(any(ExecutionLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskItemRepository.save(any(TaskItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(messageService.createMessage(any(UUID.class), any(UUID.class), any(Agent.class), any(), anyString(), any()))
                .thenReturn(null);

        ExecutionLog log = dispatcherService.dispatchTask(testTask, testAgent);

        assertNotNull(log);
        assertTrue(log.getSucceeded());
        assertEquals(250L, log.getActualTokensUsed()); // 100 + 150
        assertEquals(BigDecimal.valueOf(0.0008), log.getActualCostUsd());
        verify(llmProvider, times(1)).execute(anyString());
        verify(executionLogRepository, times(1)).save(any(ExecutionLog.class));
    }

    @Test
    void testDispatchTaskUpdatesTaskStatus() {
        LlmResponse mockResponse = LlmResponse.builder()
                .result("Task completed")
                .inputTokens(50L)
                .outputTokens(100L)
                .costUsd(BigDecimal.valueOf(0.0006))
                .build();

        when(llmProvider.execute(anyString())).thenReturn(mockResponse);
        when(executionLogRepository.save(any(ExecutionLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskItemRepository.save(any(TaskItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(messageService.createMessage(any(UUID.class), any(UUID.class), any(Agent.class), any(), anyString(), any()))
                .thenReturn(null);

        ExecutionLog log = dispatcherService.dispatchTask(testTask, testAgent);

        assertNotNull(log);
        assertEquals(TaskItemStatus.COMPLETED, testTask.getStatus());
        assertNotNull(testTask.getActualCostUsd());
        assertNotNull(testTask.getActualLatencyMs());
    }

    @Test
    void testDispatchTaskFailure() {
        when(llmProvider.execute(anyString())).thenThrow(new RuntimeException("API Error"));
        when(executionLogRepository.save(any(ExecutionLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskItemRepository.save(any(TaskItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(messageService.createMessage(any(UUID.class), any(UUID.class), any(Agent.class), any(), anyString(), any()))
                .thenReturn(null);

        try {
            dispatcherService.dispatchTask(testTask, testAgent);
        } catch (RuntimeException e) {
            // Expected
        }

        assertEquals(TaskItemStatus.FAILED, testTask.getStatus());
        verify(executionLogRepository, times(1)).save(any(ExecutionLog.class));
    }
}

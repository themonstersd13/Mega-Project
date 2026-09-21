package com.synapse.controller;

import com.synapse.api.request.TaskUpdateRequest;
import com.synapse.api.response.TaskItemResponse;
import com.synapse.core.service.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {
    private final WorkspaceService workspaceService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list() {
        return ResponseEntity.ok(workspaceService.listWorkspaces());
    }

    @GetMapping("/{organizationId}")
    public ResponseEntity<Map<String, Object>> snapshot(@PathVariable UUID organizationId) {
        return ResponseEntity.ok(workspaceService.snapshot(organizationId));
    }

    @GetMapping("/{organizationId}/board")
    public ResponseEntity<Map<String, Object>> board(@PathVariable UUID organizationId) {
        return ResponseEntity.ok(workspaceService.board(organizationId));
    }

    @GetMapping("/{organizationId}/context")
    public ResponseEntity<Map<String, Object>> context(@PathVariable UUID organizationId) {
        return ResponseEntity.ok(workspaceService.context(organizationId));
    }

    @PatchMapping("/tasks/{taskId}")
    public ResponseEntity<TaskItemResponse> updateTask(
            @PathVariable UUID taskId,
            @RequestBody TaskUpdateRequest request) {
        return ResponseEntity.ok(workspaceService.updateTask(taskId, request));
    }
}

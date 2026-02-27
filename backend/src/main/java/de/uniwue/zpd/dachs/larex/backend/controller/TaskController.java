package de.uniwue.zpd.dachs.larex.backend.controller;

import de.uniwue.zpd.dachs.larex.backend.dto.BulkTaskDto;
import de.uniwue.zpd.dachs.larex.backend.dto.PaginatedResponse;
import de.uniwue.zpd.dachs.larex.backend.dto.TaskDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Task;
import de.uniwue.zpd.dachs.larex.backend.service.BulkTaskService;
import de.uniwue.zpd.dachs.larex.backend.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping
public class TaskController {

    private final TaskService taskService;
    private final BulkTaskService bulkTaskService;

    public TaskController(TaskService taskService, BulkTaskService bulkTaskService) {
        this.taskService = taskService;
        this.bulkTaskService = bulkTaskService;
    }

    @GetMapping("/workspaces/{workspaceId}/tasks")
    public ResponseEntity<?> listWorkspaceTasks(
            @PathVariable String workspaceId,
            @RequestParam(value = "status", required = false) Task.TaskStatus status,
            @RequestParam(value = "assignedToMe", defaultValue = "false") boolean assignedToMe,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "sort", required = false, defaultValue = "updated") String sort,
            @AuthenticationPrincipal(expression = "subject") String userId
    ) {
        if (page != null && size != null) {
            PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sort));
            PaginatedResponse<TaskDto.Response> tasks = taskService.listWorkspaceTasksPaginated(workspaceId, userId, status, assignedToMe, pageable);
            return ResponseEntity.ok(tasks);
        }
        List<TaskDto.Response> tasks = taskService.listWorkspaceTasks(workspaceId, userId, status, assignedToMe);
        return ResponseEntity.ok(tasks);
    }

    @PostMapping("/workspaces/{workspaceId}/tasks")
    public ResponseEntity<TaskDto.Response> createTask(
            @PathVariable String workspaceId,
            @Valid @RequestBody TaskDto.CreateRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId
    ) {
        TaskDto.Response created = taskService.createTask(workspaceId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<TaskDto.Response> getTask(
            @PathVariable String taskId,
            @AuthenticationPrincipal(expression = "subject") String userId
    ) {
        TaskDto.Response task = taskService.getTask(taskId, userId);
        return ResponseEntity.ok(task);
    }

    @PutMapping("/tasks/{taskId}")
    public ResponseEntity<TaskDto.Response> updateTask(
            @PathVariable String taskId,
            @Valid @RequestBody TaskDto.UpdateRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId
    ) {
        TaskDto.Response updated = taskService.updateTask(taskId, userId, request);
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/tasks/{taskId}/status")
    public ResponseEntity<TaskDto.Response> updateTaskStatus(
            @PathVariable String taskId,
            @Valid @RequestBody TaskDto.UpdateStatusRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId
    ) {
        TaskDto.Response updated = taskService.updateTaskStatus(taskId, userId, request);
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/tasks/{taskId}/assignees")
    public ResponseEntity<TaskDto.Response> updateAssignees(
            @PathVariable String taskId,
            @Valid @RequestBody TaskDto.UpdateAssigneesRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId
    ) {
        TaskDto.Response updated = taskService.updateAssignees(taskId, userId, request);
        return ResponseEntity.ok(updated);
    }

    // ==================== BULK OPERATIONS ====================

    @PutMapping("/workspaces/{workspaceId}/tasks/bulk/status")
    public ResponseEntity<BulkTaskDto.BulkOperationResponse> bulkUpdateStatus(
            @PathVariable String workspaceId,
            @Valid @RequestBody BulkTaskDto.BulkStatusRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId
    ) {
        BulkTaskDto.BulkOperationResponse response = bulkTaskService.bulkUpdateStatus(workspaceId, userId, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/workspaces/{workspaceId}/tasks/bulk/priority")
    public ResponseEntity<BulkTaskDto.BulkOperationResponse> bulkUpdatePriority(
            @PathVariable String workspaceId,
            @Valid @RequestBody BulkTaskDto.BulkPriorityRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId
    ) {
        BulkTaskDto.BulkOperationResponse response = bulkTaskService.bulkUpdatePriority(workspaceId, userId, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/workspaces/{workspaceId}/tasks/bulk/assignees")
    public ResponseEntity<BulkTaskDto.BulkOperationResponse> bulkUpdateAssignees(
            @PathVariable String workspaceId,
            @Valid @RequestBody BulkTaskDto.BulkAssigneesRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId
    ) {
        BulkTaskDto.BulkOperationResponse response = bulkTaskService.bulkUpdateAssignees(workspaceId, userId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/workspaces/{workspaceId}/tasks/bulk")
    public ResponseEntity<BulkTaskDto.BulkOperationResponse> bulkDelete(
            @PathVariable String workspaceId,
            @Valid @RequestBody BulkTaskDto.BulkDeleteRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId
    ) {
        BulkTaskDto.BulkOperationResponse response = bulkTaskService.bulkDelete(workspaceId, userId, request);
        return ResponseEntity.ok(response);
    }
}

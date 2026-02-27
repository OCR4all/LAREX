package de.uniwue.zpd.dachs.larex.backend.controller;

import de.uniwue.zpd.dachs.larex.backend.dto.SubtaskDto;
import de.uniwue.zpd.dachs.larex.backend.dto.TaskLinkDto;
import de.uniwue.zpd.dachs.larex.backend.service.SubtaskService;
import de.uniwue.zpd.dachs.larex.backend.service.TaskLinkService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping
public class TaskLinkController {

    private final TaskLinkService linkService;
    private final SubtaskService subtaskService;

    public TaskLinkController(TaskLinkService linkService, SubtaskService subtaskService) {
        this.linkService = linkService;
        this.subtaskService = subtaskService;
    }

    // ==================== TASK LINKS ====================

    @GetMapping("/tasks/{taskId}/links")
    public ResponseEntity<TaskLinkDto.TaskLinksResponse> getTaskLinks(
            @PathVariable String taskId,
            @AuthenticationPrincipal(expression = "subject") String userId
    ) {
        TaskLinkDto.TaskLinksResponse links = linkService.getTaskLinks(taskId, userId);
        return ResponseEntity.ok(links);
    }

    @PostMapping("/tasks/{taskId}/links/projects")
    public ResponseEntity<TaskLinkDto.ProjectLinkResponse> linkProject(
            @PathVariable String taskId,
            @Valid @RequestBody TaskLinkDto.LinkProjectRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId
    ) {
        TaskLinkDto.ProjectLinkResponse link = linkService.linkProject(taskId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(link);
    }

    @DeleteMapping("/tasks/{taskId}/links/projects/{projectId}")
    public ResponseEntity<Void> unlinkProject(
            @PathVariable String taskId,
            @PathVariable String projectId,
            @AuthenticationPrincipal(expression = "subject") String userId
    ) {
        linkService.unlinkProject(taskId, projectId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tasks/{taskId}/links/pages")
    public ResponseEntity<List<TaskLinkDto.PageLinkResponse>> linkPages(
            @PathVariable String taskId,
            @Valid @RequestBody TaskLinkDto.LinkPagesRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId
    ) {
        List<TaskLinkDto.PageLinkResponse> links = linkService.linkPages(taskId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(links);
    }

    @PostMapping("/tasks/{taskId}/subtasks/bulk/description")
    public ResponseEntity<SubtaskDto.BulkResponse> bulkUpdateSubtaskDescriptions(
            @PathVariable String taskId,
            @Valid @RequestBody SubtaskDto.BulkDescriptionRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId
    ) {
        SubtaskDto.BulkResponse response = subtaskService.bulkUpdateDescription(taskId, userId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/tasks/{taskId}/links/pages/{pageId}")
    public ResponseEntity<Void> unlinkPage(
            @PathVariable String taskId,
            @PathVariable String pageId,
            @AuthenticationPrincipal(expression = "subject") String userId
    ) {
        linkService.unlinkPage(taskId, pageId, userId);
        return ResponseEntity.noContent().build();
    }

    // ==================== REVERSE LOOKUPS ====================

    @GetMapping("/projects/{projectId}/tasks")
    public ResponseEntity<List<TaskLinkDto.LinkedTaskResponse>> getTasksLinkedToProject(
            @PathVariable String projectId,
            @AuthenticationPrincipal(expression = "subject") String userId
    ) {
        List<TaskLinkDto.LinkedTaskResponse> tasks = linkService.getTasksLinkedToProject(projectId, userId);
        return ResponseEntity.ok(tasks);
    }

    @GetMapping("/pages/{pageId}/tasks")
    public ResponseEntity<List<TaskLinkDto.LinkedTaskResponse>> getTasksLinkedToPage(
            @PathVariable String pageId,
            @RequestParam(defaultValue = "false") boolean onlyAssigned,
            @AuthenticationPrincipal(expression = "subject") String userId
    ) {
        List<TaskLinkDto.LinkedTaskResponse> tasks = linkService.getTasksLinkedToPage(pageId, userId, onlyAssigned);
        return ResponseEntity.ok(tasks);
    }

    @GetMapping("/pages/{pageId}/subtasks")
    public ResponseEntity<List<SubtaskDto.Response>> getSubtasksForPage(
            @PathVariable String pageId,
            @AuthenticationPrincipal(expression = "subject") String userId
    ) {
        List<SubtaskDto.Response> subtasks = subtaskService.getOpenSubtasksForPage(pageId, userId);
        return ResponseEntity.ok(subtasks);
    }
}

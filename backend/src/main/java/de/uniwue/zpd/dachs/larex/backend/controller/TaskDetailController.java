package de.uniwue.zpd.dachs.larex.backend.controller;

import de.uniwue.zpd.dachs.larex.backend.dto.SubtaskDto;
import de.uniwue.zpd.dachs.larex.backend.dto.TaskActivityLogDto;
import de.uniwue.zpd.dachs.larex.backend.dto.TaskCommentDto;
import de.uniwue.zpd.dachs.larex.backend.dto.TaskReminderDto;
import de.uniwue.zpd.dachs.larex.backend.service.SubtaskService;
import de.uniwue.zpd.dachs.larex.backend.service.TaskActivityService;
import de.uniwue.zpd.dachs.larex.backend.service.TaskCommentService;
import de.uniwue.zpd.dachs.larex.backend.service.TaskReminderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tasks/{taskId}")
public class TaskDetailController {

    private final TaskCommentService commentService;
    private final TaskActivityService activityService;
    private final TaskReminderService reminderService;
    private final SubtaskService subtaskService;

    public TaskDetailController(
            TaskCommentService commentService,
            TaskActivityService activityService,
            TaskReminderService reminderService,
            SubtaskService subtaskService
    ) {
        this.commentService = commentService;
        this.activityService = activityService;
        this.reminderService = reminderService;
        this.subtaskService = subtaskService;
    }

    // ==================== COMMENTS ====================

    @GetMapping("/comments")
    public ResponseEntity<List<TaskCommentDto.Response>> getComments(
            @PathVariable String taskId,
            @AuthenticationPrincipal(expression = "subject") String userId
    ) {
        List<TaskCommentDto.Response> comments = commentService.getTaskComments(taskId, userId);
        return ResponseEntity.ok(comments);
    }

    @PostMapping("/comments")
    public ResponseEntity<TaskCommentDto.Response> createComment(
            @PathVariable String taskId,
            @Valid @RequestBody TaskCommentDto.CreateRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId
    ) {
        TaskCommentDto.Response comment = commentService.createComment(taskId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(comment);
    }

    @PutMapping("/comments/{commentId}")
    public ResponseEntity<TaskCommentDto.Response> updateComment(
            @PathVariable String taskId,
            @PathVariable String commentId,
            @Valid @RequestBody TaskCommentDto.UpdateRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId
    ) {
        TaskCommentDto.Response comment = commentService.updateComment(taskId, commentId, userId, request);
        return ResponseEntity.ok(comment);
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable String taskId,
            @PathVariable String commentId,
            @AuthenticationPrincipal(expression = "subject") String userId
    ) {
        commentService.deleteComment(taskId, commentId, userId);
        return ResponseEntity.noContent().build();
    }

    // ==================== ACTIVITY ====================

    @GetMapping("/activity")
    public ResponseEntity<List<TaskActivityLogDto.Response>> getActivity(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal(expression = "subject") String userId
    ) {
        List<TaskActivityLogDto.Response> activity = activityService.getTaskActivity(taskId, userId, page, size);
        return ResponseEntity.ok(activity);
    }

    // ==================== REMINDERS ====================

    @GetMapping("/reminders")
    public ResponseEntity<List<TaskReminderDto.Response>> getReminders(
            @PathVariable String taskId,
            @AuthenticationPrincipal(expression = "subject") String userId
    ) {
        List<TaskReminderDto.Response> reminders = reminderService.getTaskReminders(taskId, userId);
        return ResponseEntity.ok(reminders);
    }

    @PostMapping("/reminders")
    public ResponseEntity<TaskReminderDto.Response> createReminder(
            @PathVariable String taskId,
            @Valid @RequestBody TaskReminderDto.CreateRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId
    ) {
        TaskReminderDto.Response reminder = reminderService.createReminder(taskId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(reminder);
    }

    @DeleteMapping("/reminders/{reminderId}")
    public ResponseEntity<Void> deleteReminder(
            @PathVariable String taskId,
            @PathVariable String reminderId,
            @AuthenticationPrincipal(expression = "subject") String userId
    ) {
        reminderService.deleteReminder(taskId, reminderId, userId);
        return ResponseEntity.noContent().build();
    }

    // ==================== SUBTASKS ====================

    @GetMapping("/subtasks")
    public ResponseEntity<List<SubtaskDto.Response>> getSubtasks(
            @PathVariable String taskId,
            @AuthenticationPrincipal(expression = "subject") String userId
    ) {
        List<SubtaskDto.Response> subtasks = subtaskService.getSubtasks(taskId, userId);
        return ResponseEntity.ok(subtasks);
    }

    @GetMapping("/subtasks/progress")
    public ResponseEntity<SubtaskDto.ProgressResponse> getSubtaskProgress(
            @PathVariable String taskId,
            @AuthenticationPrincipal(expression = "subject") String userId
    ) {
        SubtaskDto.ProgressResponse progress = subtaskService.getProgress(taskId, userId);
        return ResponseEntity.ok(progress);
    }

    @PostMapping("/subtasks")
    public ResponseEntity<SubtaskDto.Response> createSubtask(
            @PathVariable String taskId,
            @Valid @RequestBody SubtaskDto.CreateRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId
    ) {
        SubtaskDto.Response subtask = subtaskService.createSubtask(taskId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(subtask);
    }

    @PutMapping("/subtasks/{subtaskId}")
    public ResponseEntity<SubtaskDto.Response> updateSubtask(
            @PathVariable String taskId,
            @PathVariable String subtaskId,
            @Valid @RequestBody SubtaskDto.UpdateRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId
    ) {
        SubtaskDto.Response subtask = subtaskService.updateSubtask(taskId, subtaskId, userId, request);
        return ResponseEntity.ok(subtask);
    }

    @PutMapping("/subtasks/{subtaskId}/toggle")
    public ResponseEntity<SubtaskDto.Response> toggleSubtask(
            @PathVariable String taskId,
            @PathVariable String subtaskId,
            @AuthenticationPrincipal(expression = "subject") String userId
    ) {
        SubtaskDto.Response subtask = subtaskService.toggleSubtask(taskId, subtaskId, userId);
        return ResponseEntity.ok(subtask);
    }

    @PutMapping("/subtasks/reorder")
    public ResponseEntity<Void> reorderSubtasks(
            @PathVariable String taskId,
            @Valid @RequestBody SubtaskDto.ReorderRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId
    ) {
        subtaskService.reorderSubtasks(taskId, userId, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/subtasks/{subtaskId}")
    public ResponseEntity<Void> deleteSubtask(
            @PathVariable String taskId,
            @PathVariable String subtaskId,
            @AuthenticationPrincipal(expression = "subject") String userId
    ) {
        subtaskService.deleteSubtask(taskId, subtaskId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/subtasks/bulk/complete")
    public ResponseEntity<SubtaskDto.BulkResponse> bulkCompleteSubtasks(
            @PathVariable String taskId,
            @Valid @RequestBody SubtaskDto.BulkRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId
    ) {
        SubtaskDto.BulkResponse response = subtaskService.bulkComplete(taskId, userId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/subtasks/bulk/delete")
    public ResponseEntity<SubtaskDto.BulkResponse> bulkDeleteSubtasks(
            @PathVariable String taskId,
            @Valid @RequestBody SubtaskDto.BulkRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId
    ) {
        SubtaskDto.BulkResponse response = subtaskService.bulkDelete(taskId, userId, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/subtasks/{subtaskId}/assign")
    public ResponseEntity<SubtaskDto.Response> assignSubtask(
            @PathVariable String taskId,
            @PathVariable String subtaskId,
            @Valid @RequestBody SubtaskDto.AssignRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId
    ) {
        SubtaskDto.Response subtask = subtaskService.assignSubtask(taskId, subtaskId, userId, request);
        return ResponseEntity.ok(subtask);
    }

    @PostMapping("/subtasks/bulk/assign")
    public ResponseEntity<SubtaskDto.BulkResponse> bulkAssignSubtasks(
            @PathVariable String taskId,
            @Valid @RequestBody SubtaskDto.BulkAssignRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId
    ) {
        SubtaskDto.BulkResponse response = subtaskService.bulkAssign(taskId, userId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/subtasks/with-page")
    public ResponseEntity<SubtaskDto.Response> createSubtaskWithPage(
            @PathVariable String taskId,
            @Valid @RequestBody SubtaskDto.CreateWithPageRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId
    ) {
        SubtaskDto.Response subtask = subtaskService.createSubtaskWithPage(taskId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(subtask);
    }
}

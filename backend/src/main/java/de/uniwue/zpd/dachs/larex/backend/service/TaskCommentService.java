package de.uniwue.zpd.dachs.larex.backend.service;

import de.uniwue.zpd.dachs.larex.backend.dto.TaskCommentDto;
import de.uniwue.zpd.dachs.larex.backend.dto.UserDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Task;
import de.uniwue.zpd.dachs.larex.backend.entity.TaskComment;
import de.uniwue.zpd.dachs.larex.backend.exception.ResourceNotFoundException;
import de.uniwue.zpd.dachs.larex.backend.repository.TaskCommentRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.TaskRepository;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Transactional
public class TaskCommentService {

    private static final Pattern MENTION_PATTERN = Pattern.compile("@(\\w+)");

    private final TaskCommentRepository commentRepository;
    private final TaskRepository taskRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final UserService userService;
    private final TaskActivityService activityService;
    private final NotificationService notificationService;

    public TaskCommentService(
            TaskCommentRepository commentRepository,
            TaskRepository taskRepository,
            WorkspaceAccessService workspaceAccessService,
            UserService userService,
            TaskActivityService activityService,
            NotificationService notificationService
    ) {
        this.commentRepository = commentRepository;
        this.taskRepository = taskRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.userService = userService;
        this.activityService = activityService;
        this.notificationService = notificationService;
    }

    public List<TaskCommentDto.Response> getTaskComments(String taskId, String userId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        if (!workspaceAccessService.hasWorkspaceAccess(task.getWorkspaceId(), userId)) {
            throw new SecurityException("Access denied.");
        }

        List<TaskComment> comments = commentRepository.findByTaskIdOrderByCreatedAsc(taskId);
        return mapComments(comments);
    }

    public TaskCommentDto.Response createComment(String taskId, String userId, TaskCommentDto.CreateRequest request) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        if (!workspaceAccessService.hasWorkspaceAccess(task.getWorkspaceId(), userId)) {
            throw new SecurityException("Access denied.");
        }

        TaskComment comment = new TaskComment(taskId, userId, request.content().trim());

        // Extract @mentions
        List<String> mentionedUsernames = extractMentions(request.content());
        if (!mentionedUsernames.isEmpty()) {
            List<String> mentionedUserIds = resolveUsernames(mentionedUsernames);
            comment.setMentionedUserIds(mentionedUserIds);

            // Send notifications to mentioned users
            for (String mentionedUserId : mentionedUserIds) {
                if (!mentionedUserId.equals(userId)) {
                    notificationService.createTaskMentionedNotification(mentionedUserId, task.getTitle(), taskId);
                }
            }
        }

        TaskComment saved = commentRepository.save(comment);

        // Log activity
        activityService.logCommentAdded(taskId, userId, saved.getId());

        // Notify assignees about new comment (except the commenter and mentioned users)
        Set<String> notifyUsers = new LinkedHashSet<>();
        if (task.getAssignedUserIds() != null) {
            notifyUsers.addAll(task.getAssignedUserIds());
        }
        notifyUsers.add(task.getCreatedByUserId());
        notifyUsers.remove(userId);
        notifyUsers.removeAll(comment.getMentionedUserIds());

        for (String notifyUserId : notifyUsers) {
            notificationService.createTaskCommentAddedNotification(notifyUserId, task.getTitle(), taskId);
        }

        return mapComment(saved);
    }

    public TaskCommentDto.Response updateComment(String taskId, String commentId, String userId, TaskCommentDto.UpdateRequest request) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        if (!workspaceAccessService.hasWorkspaceAccess(task.getWorkspaceId(), userId)) {
            throw new SecurityException("Access denied.");
        }

        TaskComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));

        if (!comment.getTaskId().equals(taskId)) {
            throw new IllegalArgumentException("Comment does not belong to this task.");
        }

        // Only the comment author can edit their comment
        if (!comment.getUserId().equals(userId)) {
            throw new SecurityException("You can only edit your own comments.");
        }

        comment.setContent(request.content().trim());

        // Re-extract @mentions
        List<String> mentionedUsernames = extractMentions(request.content());
        List<String> newMentionedUserIds = mentionedUsernames.isEmpty() ? List.of() : resolveUsernames(mentionedUsernames);

        // Find newly mentioned users
        Set<String> previousMentions = new HashSet<>(comment.getMentionedUserIds());
        for (String mentionedUserId : newMentionedUserIds) {
            if (!previousMentions.contains(mentionedUserId) && !mentionedUserId.equals(userId)) {
                notificationService.createTaskMentionedNotification(mentionedUserId, task.getTitle(), taskId);
            }
        }

        comment.setMentionedUserIds(new ArrayList<>(newMentionedUserIds));
        TaskComment saved = commentRepository.save(comment);

        // Log activity
        activityService.logCommentUpdated(taskId, userId, saved.getId());

        return mapComment(saved);
    }

    public void deleteComment(String taskId, String commentId, String userId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        if (!workspaceAccessService.hasWorkspaceAccess(task.getWorkspaceId(), userId)) {
            throw new SecurityException("Access denied.");
        }

        TaskComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));

        if (!comment.getTaskId().equals(taskId)) {
            throw new IllegalArgumentException("Comment does not belong to this task.");
        }

        // Only the comment author or admin can delete
        boolean isAdmin = workspaceAccessService.isUserAdministrator(task.getWorkspaceId(), userId);
        if (!comment.getUserId().equals(userId) && !isAdmin) {
            throw new SecurityException("You can only delete your own comments.");
        }

        commentRepository.delete(comment);

        // Log activity
        activityService.logCommentDeleted(taskId, userId, commentId);
    }

    private List<String> extractMentions(String content) {
        List<String> mentions = new ArrayList<>();
        Matcher matcher = MENTION_PATTERN.matcher(content);
        while (matcher.find()) {
            mentions.add(matcher.group(1));
        }
        return mentions;
    }

    private List<String> resolveUsernames(List<String> usernames) {
        return userService.getUserIdsByUsernames(usernames);
    }

    private List<TaskCommentDto.Response> mapComments(List<TaskComment> comments) {
        if (comments.isEmpty()) return List.of();

        Set<String> userIds = new LinkedHashSet<>();
        for (TaskComment comment : comments) {
            userIds.add(comment.getUserId());
            userIds.addAll(comment.getMentionedUserIds());
        }

        Map<String, UserDto> users = userService.getUsersByIds(new ArrayList<>(userIds));

        return comments.stream().map(comment -> mapComment(comment, users)).toList();
    }

    private TaskCommentDto.Response mapComment(TaskComment comment) {
        Set<String> userIds = new LinkedHashSet<>();
        userIds.add(comment.getUserId());
        userIds.addAll(comment.getMentionedUserIds());
        Map<String, UserDto> users = userService.getUsersByIds(new ArrayList<>(userIds));
        return mapComment(comment, users);
    }

    private TaskCommentDto.Response mapComment(TaskComment comment, Map<String, UserDto> users) {
        List<UserDto> mentionedUsers = comment.getMentionedUserIds().stream()
                .map(users::get)
                .filter(Objects::nonNull)
                .toList();

        return new TaskCommentDto.Response(
                comment.getId(),
                comment.getTaskId(),
                comment.getUserId(),
                users.get(comment.getUserId()),
                comment.getContent(),
                comment.getMentionedUserIds(),
                mentionedUsers,
                comment.getCreated(),
                comment.getUpdated()
        );
    }
}

package de.uniwue.zpd.dachs.larex.backend.service.task;

import de.uniwue.zpd.dachs.larex.backend.dto.PaginatedResponse;
import de.uniwue.zpd.dachs.larex.backend.dto.TaskDto;
import de.uniwue.zpd.dachs.larex.backend.dto.UserDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Task;
import de.uniwue.zpd.dachs.larex.backend.entity.WorkspaceMember;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.AbstractWorkspace;
import de.uniwue.zpd.dachs.larex.backend.exception.ResourceNotFoundException;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceMemberRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceQueryService;
import de.uniwue.zpd.dachs.larex.backend.service.notification.NotificationService;
import de.uniwue.zpd.dachs.larex.backend.service.security.AuthorizationPolicyService;
import de.uniwue.zpd.dachs.larex.backend.service.user.UserService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TaskService {

    private final TaskRepository taskRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceQueryService workspaceQueryService;
    private final NotificationService notificationService;
    private final UserService userService;
    private final TaskActivityService activityService;
    private final AuthorizationPolicyService authorizationPolicyService;

    public TaskService(
            TaskRepository taskRepository,
            WorkspaceAccessService workspaceAccessService,
            WorkspaceMemberRepository workspaceMemberRepository,
            WorkspaceQueryService workspaceQueryService,
            NotificationService notificationService,
            UserService userService,
            AuthorizationPolicyService authorizationPolicyService,
            @org.springframework.context.annotation.Lazy TaskActivityService activityService
    ) {
        this.taskRepository = taskRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.workspaceQueryService = workspaceQueryService;
        this.notificationService = notificationService;
        this.userService = userService;
        this.authorizationPolicyService = authorizationPolicyService;
        this.activityService = activityService;
    }

    public List<TaskDto.Response> listWorkspaceTasks(String workspaceId, String userId, Task.TaskStatus status, boolean assignedToMe) {
        requireWorkspaceAccess(workspaceId, userId);

        List<Task> tasks;
        if (assignedToMe) {
            if (status != null) {
                tasks = taskRepository.findByWorkspaceIdAndAssignedUserIdAndStatus(workspaceId, userId, status);
            } else {
                tasks = taskRepository.findByWorkspaceIdAndAssignedUserId(workspaceId, userId);
            }
        } else {
            tasks = (status != null)
                    ? taskRepository.findByWorkspaceIdAndStatus(workspaceId, status)
                    : taskRepository.findByWorkspaceId(workspaceId);
        }

        tasks.sort(Comparator.comparing(Task::getUpdated).reversed());
        return mapTasks(tasks, userId);
    }

    public PaginatedResponse<TaskDto.Response> listWorkspaceTasksPaginated(String workspaceId, String userId, Task.TaskStatus status, boolean assignedToMe, Pageable pageable) {
        requireWorkspaceAccess(workspaceId, userId);

        Page<Task> taskPage;
        if (assignedToMe) {
            if (status != null) {
                taskPage = taskRepository.findByWorkspaceIdAndAssignedUserIdAndStatus(workspaceId, userId, status, pageable);
            } else {
                taskPage = taskRepository.findByWorkspaceIdAndAssignedUserId(workspaceId, userId, pageable);
            }
        } else {
            taskPage = (status != null)
                    ? taskRepository.findByWorkspaceIdAndStatus(workspaceId, status, pageable)
                    : taskRepository.findByWorkspaceId(workspaceId, pageable);
        }

        List<TaskDto.Response> content = mapTasks(taskPage.getContent(), userId);
        return new PaginatedResponse<>(content, taskPage.getNumber(), taskPage.getSize(), taskPage.getTotalElements(), taskPage.getTotalPages());
    }

    public TaskDto.Response getTask(String taskId, String userId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        requireWorkspaceAccess(task.getWorkspaceId(), userId);
        return mapTask(task, userId);
    }

    public TaskDto.Response createTask(String workspaceId, String userId, TaskDto.CreateRequest request) {
        AbstractWorkspace workspace = requireWorkspaceAccess(workspaceId, userId);

        boolean isAdmin = workspaceAccessService.isUserAdministrator(workspaceId, userId);
        List<String> requestedAssignees = request.assignedUserIds() == null ? List.of() : request.assignedUserIds();

        Set<String> normalizedAssignees = new LinkedHashSet<>();
        for (String assigneeId : requestedAssignees) {
            if (assigneeId == null || assigneeId.isBlank()) continue;
            normalizedAssignees.add(assigneeId);
        }

        if (normalizedAssignees.isEmpty()) {
            normalizedAssignees.add(userId);
        }

        if (workspace.isPersonal()) {
            normalizedAssignees = Set.of(userId);
        } else if (!isAdmin && !(normalizedAssignees.size() == 1 && normalizedAssignees.contains(userId))) {
            throw new SecurityException("Only workspace administrators can assign tasks to other users.");
        }

        requireAcceptedMembers(workspace, normalizedAssignees);

        Task task = new Task(
                request.title().trim(),
                request.description(),
                userId,
                request.priority(),
                workspaceId
        );
        task.setDueDate(request.dueDate());
        task.setAssignedUserIds(new ArrayList<>(normalizedAssignees));

        Task saved = taskRepository.save(task);

        // Log activity
        activityService.logTaskCreated(saved.getId(), userId);

        for (String assigneeId : normalizedAssignees) {
            if (!assigneeId.equals(userId)) {
                notificationService.createTaskAssignedNotification(assigneeId, saved.getTitle(), saved.getId());
            }
        }

        return mapTask(saved, userId);
    }

    public TaskDto.Response updateTask(String taskId, String userId, TaskDto.UpdateRequest request) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        boolean isAdmin = workspaceAccessService.isUserAdministrator(task.getWorkspaceId(), userId);
        if (!isAdmin && !task.getCreatedByUserId().equals(userId)) {
            throw new SecurityException("Only the task creator or a workspace administrator can update this task.");
        }

        // Track changes for activity logging
        String oldTitle = task.getTitle();
        String oldDescription = task.getDescription();
        Task.TaskPriority oldPriority = task.getPriority();
        String oldDueDate = task.getDueDate() != null ? task.getDueDate().toString() : null;

        if (request.title() != null) {
            String title = request.title().trim();
            if (title.isEmpty()) throw new IllegalArgumentException("Title must not be empty.");
            task.setTitle(title);
        }
        if (request.description() != null) {
            task.setDescription(request.description());
        }
        if (request.priority() != null) {
            task.setPriority(request.priority());
        }
        if (request.dueDate() != null) {
            task.setDueDate(request.dueDate());
        }

        Task saved = taskRepository.save(task);

        // Log activities for changed fields
        if (request.title() != null && !oldTitle.equals(saved.getTitle())) {
            activityService.logTitleChanged(taskId, userId, oldTitle, saved.getTitle());
        }
        if (request.description() != null && !Objects.equals(oldDescription, saved.getDescription())) {
            activityService.logDescriptionChanged(taskId, userId);
        }
        if (request.priority() != null && oldPriority != saved.getPriority()) {
            activityService.logPriorityChanged(taskId, userId, oldPriority, saved.getPriority());
        }
        String newDueDate = saved.getDueDate() != null ? saved.getDueDate().toString() : null;
        if (request.dueDate() != null && !Objects.equals(oldDueDate, newDueDate)) {
            activityService.logDueDateChanged(taskId, userId, oldDueDate, newDueDate);
        }

        return mapTask(saved, userId);
    }

    public TaskDto.Response updateTaskStatus(String taskId, String userId, TaskDto.UpdateStatusRequest request) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        requireWorkspaceAccess(task.getWorkspaceId(), userId);

        boolean isAdmin = workspaceAccessService.isUserAdministrator(task.getWorkspaceId(), userId);
        boolean isCreator = task.getCreatedByUserId().equals(userId);
        boolean isAssignee = task.getAssignedUserIds() != null && task.getAssignedUserIds().contains(userId);
        if (!isAdmin && !isCreator && !isAssignee) {
            throw new SecurityException("You do not have permission to update this task status.");
        }

        Task.TaskStatus oldStatus = task.getStatus();
        Task.TaskStatus newStatus = request.status();
        if (oldStatus == newStatus) {
            return mapTask(task, userId);
        }

        task.setStatus(newStatus);
        if (newStatus == Task.TaskStatus.COMPLETED) {
            task.setCompletedAt(LocalDateTime.now());
            task.setCompletedByUserId(userId);
        } else {
            task.setCompletedAt(null);
            task.setCompletedByUserId(null);
        }

        Task saved = taskRepository.save(task);

        // Log activity
        activityService.logStatusChanged(taskId, userId, oldStatus, newStatus);

        if (newStatus == Task.TaskStatus.COMPLETED) {
            Set<String> notifyUsers = new LinkedHashSet<>();
            if (saved.getAssignedUserIds() != null) {
                notifyUsers.addAll(saved.getAssignedUserIds());
            }
            notifyUsers.add(saved.getCreatedByUserId());
            notifyUsers.remove(userId);

            for (String notifyUserId : notifyUsers) {
                notificationService.createTaskCompletedNotification(notifyUserId, saved.getTitle(), saved.getId());
            }
        }

        return mapTask(saved, userId);
    }

    public TaskDto.Response updateAssignees(String taskId, String userId, TaskDto.UpdateAssigneesRequest request) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        AbstractWorkspace workspace = requireWorkspaceAccess(task.getWorkspaceId(), userId);

        boolean isAdmin = workspaceAccessService.isUserAdministrator(task.getWorkspaceId(), userId);
        boolean isCreator = task.getCreatedByUserId().equals(userId);

        Set<String> normalizedAssignees = new LinkedHashSet<>();
        for (String assigneeId : request.assignedUserIds()) {
            if (assigneeId == null || assigneeId.isBlank()) continue;
            normalizedAssignees.add(assigneeId);
        }

        if (normalizedAssignees.isEmpty()) {
            normalizedAssignees.add(userId);
        }

        if (workspace.isPersonal()) {
            normalizedAssignees.clear();
            normalizedAssignees.add(userId);
        } else if (!isAdmin && !(isCreator && normalizedAssignees.size() == 1 && normalizedAssignees.contains(userId))) {
            throw new SecurityException("Only workspace administrators can assign tasks to other users.");
        }

        requireAcceptedMembers(workspace, normalizedAssignees);

        Set<String> previousAssignees = new LinkedHashSet<>(task.getAssignedUserIds() == null ? List.of() : task.getAssignedUserIds());
        task.setAssignedUserIds(new ArrayList<>(normalizedAssignees));

        Task saved = taskRepository.save(task);

        // Compute added and removed assignees for activity log
        List<String> addedAssignees = normalizedAssignees.stream()
                .filter(id -> !previousAssignees.contains(id))
                .toList();
        List<String> removedAssignees = previousAssignees.stream()
                .filter(id -> !normalizedAssignees.contains(id))
                .toList();

        if (!addedAssignees.isEmpty() || !removedAssignees.isEmpty()) {
            activityService.logAssigneesChanged(taskId, userId, addedAssignees, removedAssignees);
        }

        for (String assigneeId : normalizedAssignees) {
            if (!previousAssignees.contains(assigneeId) && !assigneeId.equals(userId)) {
                notificationService.createTaskAssignedNotification(assigneeId, saved.getTitle(), saved.getId());
            }
        }

        return mapTask(saved, userId);
    }

    public void deleteTask(String taskId, String userId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        boolean isAdmin = workspaceAccessService.isUserAdministrator(task.getWorkspaceId(), userId);
        if (!isAdmin && !task.getCreatedByUserId().equals(userId)) {
            throw new SecurityException("Only the task creator or a workspace administrator can delete this task.");
        }

        taskRepository.delete(task);
    }

    private AbstractWorkspace requireWorkspaceAccess(String workspaceId, String userId) {
        if (!workspaceAccessService.hasWorkspaceAccess(workspaceId, userId)) {
            throw new SecurityException("Access denied.");
        }

        return workspaceQueryService.findWorkspaceById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));
    }

    private void requireAcceptedMembers(AbstractWorkspace workspace, Set<String> userIds) {
        if (workspace.isPersonal()) {
            return;
        }

        List<WorkspaceMember> members = workspaceMemberRepository.findByWorkspaceIdAndUserIdIn(workspace.getId(), userIds);
        Set<String> acceptedMemberIds = members.stream()
                .filter(member -> member.getInvitationStatus() == WorkspaceMember.InvitationStatus.ACCEPTED)
                .map(WorkspaceMember::getUserId)
                .collect(java.util.stream.Collectors.toSet());

        for (String assigneeId : userIds) {
            if (!acceptedMemberIds.contains(assigneeId)) {
                throw new IllegalArgumentException("Cannot assign tasks to users outside the workspace.");
            }
        }
    }

    private List<TaskDto.Response> mapTasks(List<Task> tasks, String userId) {
        if (tasks.isEmpty()) return List.of();

        Set<String> userIds = new LinkedHashSet<>();
        for (Task task : tasks) {
            userIds.add(task.getCreatedByUserId());
            if (task.getAssignedUserIds() != null) {
                userIds.addAll(task.getAssignedUserIds());
            }
            if (task.getCompletedByUserId() != null) {
                userIds.add(task.getCompletedByUserId());
            }
        }

        Map<String, UserDto> users = userService.getUsersByIds(new ArrayList<>(userIds));
        return tasks.stream().map(task -> mapTask(task, users, userId)).toList();
    }

    private TaskDto.Response mapTask(Task task, String userId) {
        Set<String> userIds = new LinkedHashSet<>();
        userIds.add(task.getCreatedByUserId());
        if (task.getAssignedUserIds() != null) userIds.addAll(task.getAssignedUserIds());
        if (task.getCompletedByUserId() != null) userIds.add(task.getCompletedByUserId());
        Map<String, UserDto> users = userService.getUsersByIds(new ArrayList<>(userIds));
        return mapTask(task, users, userId);
    }

    private TaskDto.Response mapTask(Task task, Map<String, UserDto> users, String userId) {
        List<String> assignedUserIds = task.getAssignedUserIds() == null ? List.of() : List.copyOf(task.getAssignedUserIds());
        List<UserDto> assignedUsers = assignedUserIds.stream()
                .map(users::get)
                .filter(Objects::nonNull)
                .toList();

        return new TaskDto.Response(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getCreatedByUserId(),
                users.get(task.getCreatedByUserId()),
                assignedUserIds,
                assignedUsers,
                task.getStatus(),
                task.getPriority(),
                task.getDueDate(),
                task.getCreated(),
                task.getUpdated(),
                task.getCompletedAt(),
                task.getCompletedByUserId(),
                task.getWorkspaceId(),
                authorizationPolicyService.resolveTaskCapabilities(task, userId)
        );
    }
}

package de.uniwue.zpd.dachs.larex.backend.service;

import de.uniwue.zpd.dachs.larex.backend.dto.TaskDto;
import de.uniwue.zpd.dachs.larex.backend.dto.TaskLinkDto;
import de.uniwue.zpd.dachs.larex.backend.dto.UserDto;
import de.uniwue.zpd.dachs.larex.backend.entity.*;
import de.uniwue.zpd.dachs.larex.backend.exception.ResourceNotFoundException;
import de.uniwue.zpd.dachs.larex.backend.repository.*;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class TaskLinkService {

    private final TaskProjectLinkRepository projectLinkRepository;
    private final TaskPageLinkRepository pageLinkRepository;
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final PageRepository pageRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final UserService userService;
    private final TaskActivityService activityService;

    public TaskLinkService(
            TaskProjectLinkRepository projectLinkRepository,
            TaskPageLinkRepository pageLinkRepository,
            TaskRepository taskRepository,
            ProjectRepository projectRepository,
            PageRepository pageRepository,
            WorkspaceAccessService workspaceAccessService,
            UserService userService,
            TaskActivityService activityService
    ) {
        this.projectLinkRepository = projectLinkRepository;
        this.pageLinkRepository = pageLinkRepository;
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.pageRepository = pageRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.userService = userService;
        this.activityService = activityService;
    }

    public TaskLinkDto.TaskLinksResponse getTaskLinks(String taskId, String userId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        if (!workspaceAccessService.hasWorkspaceAccess(task.getWorkspaceId(), userId)) {
            throw new SecurityException("Access denied.");
        }

        List<TaskProjectLink> projectLinks = projectLinkRepository.findByTaskId(taskId);
        List<TaskPageLink> pageLinks = pageLinkRepository.findByTaskId(taskId);

        return new TaskLinkDto.TaskLinksResponse(
                mapProjectLinks(projectLinks),
                mapPageLinks(pageLinks)
        );
    }

    public TaskLinkDto.ProjectLinkResponse linkProject(String taskId, String userId, TaskLinkDto.LinkProjectRequest request) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        if (!workspaceAccessService.hasWorkspaceAccess(task.getWorkspaceId(), userId)) {
            throw new SecurityException("Access denied.");
        }

        Project project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project", request.projectId()));

        if (projectLinkRepository.existsByTaskIdAndProjectId(taskId, request.projectId())) {
            throw new IllegalArgumentException("Project is already linked to this task.");
        }

        TaskProjectLink link = new TaskProjectLink(taskId, request.projectId(), userId);
        TaskProjectLink saved = projectLinkRepository.save(link);

        activityService.logLinkAdded(taskId, userId, "project", request.projectId());

        return new TaskLinkDto.ProjectLinkResponse(
                saved.getId(),
                saved.getTaskId(),
                saved.getProjectId(),
                project.getName(),
                saved.getCreatedByUserId(),
                saved.getCreated()
        );
    }

    public void unlinkProject(String taskId, String projectId, String userId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        if (!workspaceAccessService.hasWorkspaceAccess(task.getWorkspaceId(), userId)) {
            throw new SecurityException("Access denied.");
        }

        TaskProjectLink link = projectLinkRepository.findByTaskIdAndProjectId(taskId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Link", taskId + "-" + projectId));

        projectLinkRepository.delete(link);
        activityService.logLinkRemoved(taskId, userId, "project", projectId);
    }

    public List<TaskLinkDto.PageLinkResponse> linkPages(String taskId, String userId, TaskLinkDto.LinkPagesRequest request) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        if (!workspaceAccessService.hasWorkspaceAccess(task.getWorkspaceId(), userId)) {
            throw new SecurityException("Access denied.");
        }

        List<TaskPageLink> savedLinks = new ArrayList<>();

        if (request.byTag() != null && !request.byTag().isBlank()) {
            // Link pages by tag
            List<Page> pages = pageRepository.findByTagsContaining(request.byTag());
            Set<String> pageIds = pages.stream().map(Page::getId).collect(Collectors.toSet());
            Set<String> existingPageIds = pageLinkRepository.findByTaskIdAndPageIdIn(taskId, new ArrayList<>(pageIds)).stream()
                    .map(TaskPageLink::getPageId)
                    .collect(Collectors.toSet());
            for (Page page : pages) {
                if (!existingPageIds.contains(page.getId())) {
                    TaskPageLink link = new TaskPageLink(taskId, page.getId(), TaskPageLink.LinkType.BY_TAG, request.byTag(), userId);
                    savedLinks.add(pageLinkRepository.save(link));
                }
            }
        } else if (request.pageIds() != null && !request.pageIds().isEmpty()) {
            // Link pages manually
            List<String> requestedPageIds = request.pageIds().stream().distinct().toList();
            Map<String, Page> pagesById = pageRepository.findAllByIdIn(requestedPageIds).stream()
                    .collect(Collectors.toMap(Page::getId, p -> p));
            Set<String> existingPageIds = pageLinkRepository.findByTaskIdAndPageIdIn(taskId, requestedPageIds).stream()
                    .map(TaskPageLink::getPageId)
                    .collect(Collectors.toSet());

            for (String pageId : requestedPageIds) {
                if (!pagesById.containsKey(pageId)) {
                    throw new ResourceNotFoundException("Page", pageId);
                }
                if (!existingPageIds.contains(pageId)) {
                    TaskPageLink link = new TaskPageLink(taskId, pageId, TaskPageLink.LinkType.MANUAL, userId);
                    savedLinks.add(pageLinkRepository.save(link));
                }
            }
        }

        if (!savedLinks.isEmpty()) {
            activityService.logLinkAdded(taskId, userId, "pages", String.valueOf(savedLinks.size()));
        }

        return mapPageLinks(savedLinks);
    }

    public void unlinkPage(String taskId, String pageId, String userId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        if (!workspaceAccessService.hasWorkspaceAccess(task.getWorkspaceId(), userId)) {
            throw new SecurityException("Access denied.");
        }

        TaskPageLink link = pageLinkRepository.findByTaskIdAndPageId(taskId, pageId)
                .orElseThrow(() -> new ResourceNotFoundException("Link", taskId + "-" + pageId));

        pageLinkRepository.delete(link);
        activityService.logLinkRemoved(taskId, userId, "page", pageId);
    }

    public List<TaskLinkDto.LinkedTaskResponse> getTasksLinkedToProject(String projectId, String userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        List<TaskProjectLink> links = projectLinkRepository.findByProjectId(projectId);
        List<String> taskIds = links.stream().map(TaskProjectLink::getTaskId).toList();

        if (taskIds.isEmpty()) {
            return List.of();
        }

        List<Task> tasks = taskRepository.findAllById(taskIds);
        // Filter to tasks user has access to
        tasks = tasks.stream()
                .filter(t -> workspaceAccessService.hasWorkspaceAccess(t.getWorkspaceId(), userId))
                .toList();

        return mapLinkedTasks(tasks);
    }

    public List<TaskLinkDto.LinkedTaskResponse> getTasksLinkedToPage(String pageId, String userId, boolean onlyAssigned) {
        Page page = pageRepository.findById(pageId)
                .orElseThrow(() -> new ResourceNotFoundException("Page", pageId));

        List<TaskPageLink> links;
        if (onlyAssigned) {
            links = pageLinkRepository.findByPageIdAndAssignedUserId(pageId, userId);
        } else {
            links = pageLinkRepository.findByPageId(pageId);
        }

        List<String> taskIds = links.stream().map(TaskPageLink::getTaskId).toList();

        if (taskIds.isEmpty()) {
            return List.of();
        }

        List<Task> tasks = taskRepository.findAllById(taskIds);
        // Filter to tasks user has access to
        tasks = tasks.stream()
                .filter(t -> workspaceAccessService.hasWorkspaceAccess(t.getWorkspaceId(), userId))
                .toList();

        return mapLinkedTasks(tasks);
    }

    private List<TaskLinkDto.ProjectLinkResponse> mapProjectLinks(List<TaskProjectLink> links) {
        if (links.isEmpty()) return List.of();

        List<String> projectIds = links.stream().map(TaskProjectLink::getProjectId).toList();
        Map<String, Project> projects = projectRepository.findAllById(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, p -> p));

        return links.stream().map(link -> {
            Project project = projects.get(link.getProjectId());
            return new TaskLinkDto.ProjectLinkResponse(
                    link.getId(),
                    link.getTaskId(),
                    link.getProjectId(),
                    project != null ? project.getName() : "Unknown",
                    link.getCreatedByUserId(),
                    link.getCreated()
            );
        }).toList();
    }

    private List<TaskLinkDto.PageLinkResponse> mapPageLinks(List<TaskPageLink> links) {
        if (links.isEmpty()) return List.of();

        List<String> pageIds = links.stream().map(TaskPageLink::getPageId).toList();
        Map<String, Page> pages = pageRepository.findAllById(pageIds).stream()
                .collect(Collectors.toMap(Page::getId, p -> p));

        Set<String> projectIds = pages.values().stream()
                .map(p -> p.getProject().getId())
                .collect(Collectors.toSet());
        Map<String, Project> projects = projectRepository.findAllById(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, p -> p));

        return links.stream().map(link -> {
            Page page = pages.get(link.getPageId());
            Project project = page != null ? projects.get(page.getProject().getId()) : null;
            return new TaskLinkDto.PageLinkResponse(
                    link.getId(),
                    link.getTaskId(),
                    link.getPageId(),
                    page != null ? page.getName() : "Unknown",
                    project != null ? project.getId() : null,
                    project != null ? project.getName() : "Unknown",
                    link.getLinkType(),
                    link.getTagFilter(),
                    link.getCreatedByUserId(),
                    link.getCreated()
            );
        }).toList();
    }

    private List<TaskLinkDto.LinkedTaskResponse> mapLinkedTasks(List<Task> tasks) {
        if (tasks.isEmpty()) return List.of();

        Set<String> userIds = new LinkedHashSet<>();
        for (Task task : tasks) {
            if (task.getAssignedUserIds() != null) {
                userIds.addAll(task.getAssignedUserIds());
            }
        }

        Map<String, UserDto> users = userService.getUsersByIds(new ArrayList<>(userIds));

        return tasks.stream().map(task -> {
            List<UserDto> assignedUsers = task.getAssignedUserIds() == null ? List.of() :
                    task.getAssignedUserIds().stream()
                            .map(users::get)
                            .filter(Objects::nonNull)
                            .toList();

            return new TaskLinkDto.LinkedTaskResponse(
                    task.getId(),
                    task.getTitle(),
                    task.getStatus().name(),
                    task.getPriority().name(),
                    task.getDueDate(),
                    assignedUsers
            );
        }).toList();
    }
}

package de.uniwue.zpd.dachs.larex.backend.service.project;

import de.uniwue.zpd.dachs.larex.backend.dto.ProjectTransferDto;
import de.uniwue.zpd.dachs.larex.backend.entity.*;
import de.uniwue.zpd.dachs.larex.backend.repository.library.LibraryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageImageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectTransferRequestRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceQueryService;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageOrderService;
import de.uniwue.zpd.dachs.larex.backend.service.security.AuthorizationPolicyService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceService;
import de.uniwue.zpd.dachs.larex.backend.exception.ProjectNameConflictException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProjectTransferService {

    private final ProjectTransferRequestRepository transferRequestRepository;
    private final ProjectRepository projectRepository;
    private final PageRepository pageRepository;
    private final PageImageRepository pageImageRepository;
    private final PageXmlRepository pageXmlRepository;
    private final LibraryRepository libraryRepository;
    private final WorkspaceQueryService workspaceQueryService;
    private final WorkspaceService workspaceService;
    private final ProjectStarService projectStarService;
    private final PageOrderService pageOrderService;
    private final AuthorizationPolicyService authorizationPolicyService;

    public ProjectTransferService(
            ProjectTransferRequestRepository transferRequestRepository,
            ProjectRepository projectRepository,
            PageRepository pageRepository,
            PageImageRepository pageImageRepository,
            PageXmlRepository pageXmlRepository,
            LibraryRepository libraryRepository,
            WorkspaceQueryService workspaceQueryService,
            WorkspaceService workspaceService,
            ProjectStarService projectStarService,
            PageOrderService pageOrderService,
            AuthorizationPolicyService authorizationPolicyService) {
        this.transferRequestRepository = transferRequestRepository;
        this.projectRepository = projectRepository;
        this.pageRepository = pageRepository;
        this.pageImageRepository = pageImageRepository;
        this.pageXmlRepository = pageXmlRepository;
        this.libraryRepository = libraryRepository;
        this.workspaceQueryService = workspaceQueryService;
        this.workspaceService = workspaceService;
        this.projectStarService = projectStarService;
        this.pageOrderService = pageOrderService;
        this.authorizationPolicyService = authorizationPolicyService;
    }

    public Optional<ProjectTransferRequest> requestProjectTransfer(String projectId, String targetWorkspaceId,
                                                                String requestedByUserId, String message,
                                                                ProjectTransferRequest.TransferType transferType) {
        return requestProjectTransfer(projectId, targetWorkspaceId, requestedByUserId, message, transferType, null);
    }

    public Optional<ProjectTransferRequest> requestProjectTransfer(String projectId, String targetWorkspaceId,
                                                                String requestedByUserId, String message,
                                                                ProjectTransferRequest.TransferType transferType,
                                                                String requestedProjectName) {
        Optional<Project> projectOpt = projectRepository.findById(projectId);
        if (projectOpt.isEmpty()) {
            return Optional.empty();
        }

        Project project = projectOpt.get();
        String sourceWorkspaceId = project.getLibrary().getWorkspaceId();

        if (!isUserAdministratorInWorkspace(sourceWorkspaceId, requestedByUserId)) {
            return Optional.empty();
        }

        if (workspaceQueryService.findWorkspaceById(targetWorkspaceId).isEmpty()) {
            return Optional.empty();
        }

        if (sourceWorkspaceId.equals(targetWorkspaceId)) {
            return Optional.empty();
        }

        if (transferRequestRepository.existsByProjectIdAndStatus(projectId, ProjectTransferRequest.Status.PENDING)) {
            return Optional.empty();
        }

        boolean canAutoApprove = isUserAdministratorInWorkspace(targetWorkspaceId, requestedByUserId);

        ProjectTransferRequest request = new ProjectTransferRequest(
                projectId, sourceWorkspaceId, targetWorkspaceId, requestedByUserId, message, transferType
        );
        String targetProjectName = resolveTargetProjectName(project, transferType, requestedProjectName);
        request.setTargetProjectName(targetProjectName);
        findTargetLibrary(targetWorkspaceId).ifPresent(library -> ensureProjectNameAvailable(
                project, library, transferType, targetProjectName
        ));

        if (canAutoApprove) {
            request.setStatus(ProjectTransferRequest.Status.APPROVED);
            request.setApprovedByUserId(requestedByUserId);
            request = transferRequestRepository.save(request);
            executeTransfer(request);
            return Optional.of(request);
        } else {
            request.setStatus(ProjectTransferRequest.Status.PENDING);
            // Lock project for pending MOVE requests
            if (transferType == ProjectTransferRequest.TransferType.MOVE) {
                project.setLocked(true);
                project.setLockedReason("Pending transfer to another workspace");
                projectRepository.save(project);
            }
            return Optional.of(transferRequestRepository.save(request));
        }
    }

    public List<ProjectTransferRequest> requestProjectTransfers(List<String> projectIds,
                                                                String targetWorkspaceId,
                                                                String requestedByUserId,
                                                                String message,
                                                                ProjectTransferRequest.TransferType transferType) {
        List<ProjectTransferRequest> requests = new ArrayList<>();
        for (String projectId : new java.util.LinkedHashSet<>(projectIds)) {
            requestProjectTransfer(projectId, targetWorkspaceId, requestedByUserId, message, transferType)
                    .ifPresent(requests::add);
        }
        return requests;
    }

    public boolean approveTransferRequest(String requestId, String approvingUserId) {
        return approveTransferRequest(requestId, approvingUserId, null);
    }

    public boolean approveTransferRequest(String requestId, String approvingUserId, String requestedProjectName) {
        Optional<ProjectTransferRequest> requestOpt = transferRequestRepository.findById(requestId);
        if (requestOpt.isEmpty()) {
            return false;
        }

        ProjectTransferRequest request = requestOpt.get();

        if (request.getStatus() != ProjectTransferRequest.Status.PENDING) {
            return false;
        }

        // Check if user has admin rights in target workspace
        if (!isUserAdministratorInWorkspace(request.getTargetWorkspaceId(), approvingUserId)) {
            return false;
        }

        if (requestedProjectName != null && !requestedProjectName.isBlank()) {
            request.setTargetProjectName(requestedProjectName.trim());
        }

        request.setStatus(ProjectTransferRequest.Status.APPROVED);
        request.setApprovedByUserId(approvingUserId);
        transferRequestRepository.save(request);

        executeTransfer(request);
        return true;
    }

    public boolean rejectTransferRequest(String requestId, String rejectingUserId, String rejectionReason) {
        Optional<ProjectTransferRequest> requestOpt = transferRequestRepository.findById(requestId);
        if (requestOpt.isEmpty()) {
            return false;
        }

        ProjectTransferRequest request = requestOpt.get();

        if (request.getStatus() != ProjectTransferRequest.Status.PENDING) {
            return false;
        }

        if (!isUserAdministratorInWorkspace(request.getTargetWorkspaceId(), rejectingUserId)) {
            return false;
        }

        request.setStatus(ProjectTransferRequest.Status.REJECTED);
        request.setApprovedByUserId(rejectingUserId);
        request.setRejectionReason(rejectionReason);
        transferRequestRepository.save(request);

        // Unlock project if it was a MOVE request
        if (request.getTransferType() == ProjectTransferRequest.TransferType.MOVE) {
            unlockProject(request.getProjectId());
        }

        return true;
    }

    public boolean cancelTransferRequest(String requestId, String cancellingUserId) {
        Optional<ProjectTransferRequest> requestOpt = transferRequestRepository.findById(requestId);
        if (requestOpt.isEmpty()) {
            return false;
        }

        ProjectTransferRequest request = requestOpt.get();

        if (request.getStatus() != ProjectTransferRequest.Status.PENDING) {
            return false;
        }

        if (!request.getRequestedByUserId().equals(cancellingUserId)) {
            return false;
        }

        request.setStatus(ProjectTransferRequest.Status.CANCELLED);
        transferRequestRepository.save(request);

        // Unlock project if it was a MOVE request
        if (request.getTransferType() == ProjectTransferRequest.TransferType.MOVE) {
            unlockProject(request.getProjectId());
        }

        return true;
    }

    private void unlockProject(String projectId) {
        projectRepository.findById(projectId).ifPresent(project -> {
            project.setLocked(false);
            project.setLockedReason(null);
            projectRepository.save(project);
        });
    }

    public List<ProjectTransferRequest> getPendingIncomingRequestsForWorkspace(String workspaceId) {
        return transferRequestRepository.findPendingRequestsForTargetWorkspace(workspaceId);
    }

    public boolean isProjectNameAvailable(String projectId, String targetWorkspaceId,
                                          String projectName, String userId) {
        String normalizedProjectName = projectName == null ? "" : projectName.trim();
        if (projectId == null || projectId.isBlank()
                || targetWorkspaceId == null || targetWorkspaceId.isBlank()
                || normalizedProjectName.isBlank()) {
            return false;
        }

        Optional<Project> projectOpt = projectRepository.findById(projectId);
        if (projectOpt.isEmpty()) {
            return false;
        }

        Project project = projectOpt.get();
        String sourceWorkspaceId = project.getLibrary().getWorkspaceId();
        boolean canManageSource = isUserAdministratorInWorkspace(sourceWorkspaceId, userId);
        boolean canManageTarget = isUserAdministratorInWorkspace(targetWorkspaceId, userId);
        if (!canManageSource && !canManageTarget) {
            throw new SecurityException("Access denied to project transfer");
        }

        if (workspaceQueryService.findWorkspaceById(targetWorkspaceId).isEmpty()
                || sourceWorkspaceId.equals(targetWorkspaceId)) {
            return false;
        }

        return findTargetLibrary(targetWorkspaceId)
                .map(library -> projectRepository.findByNameAndLibraryId(
                        normalizedProjectName, library.getId()
                ).isEmpty())
                .orElse(true);
    }

    public List<ProjectTransferRequest> getPendingOutgoingRequestsForWorkspace(String workspaceId) {
        return transferRequestRepository.findPendingRequestsFromSourceWorkspace(workspaceId);
    }

    public List<ProjectTransferRequest> getUserTransferRequests(String userId) {
        return transferRequestRepository.findByRequestedByUserId(userId);
    }

    public List<ProjectTransferDto.Response> toResponses(List<ProjectTransferRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }

        Set<String> workspaceIds = new HashSet<>();
        Set<String> projectIds = new HashSet<>();
        for (ProjectTransferRequest request : requests) {
            workspaceIds.add(request.getSourceWorkspaceId());
            workspaceIds.add(request.getTargetWorkspaceId());
            projectIds.add(request.getProjectId());
        }

        Map<String, String> workspaceNames = workspaceQueryService.findWorkspaceNamesByIds(workspaceIds);
        Map<String, String> projectNames = new HashMap<>();
        for (Project project : projectRepository.findAllById(projectIds)) {
            projectNames.put(project.getId(), project.getName());
        }

        List<ProjectTransferDto.Response> responses = new ArrayList<>(requests.size());
        for (ProjectTransferRequest request : requests) {
            responses.add(new ProjectTransferDto.Response(
                    request.getId(),
                    request.getProjectId(),
                    projectNames.getOrDefault(request.getProjectId(), "Unknown"),
                    request.getSourceWorkspaceId(),
                    workspaceNames.getOrDefault(request.getSourceWorkspaceId(), "Unknown"),
                    request.getTargetWorkspaceId(),
                    workspaceNames.getOrDefault(request.getTargetWorkspaceId(), "Unknown"),
                    request.getRequestedByUserId(),
                    request.getApprovedByUserId(),
                    request.getStatus(),
                    request.getTransferType(),
                    request.getMessage(),
                    request.getRejectionReason(),
                    request.getCreated(),
                    request.getUpdated()
            ));
        }
        return responses;
    }

    public ProjectTransferDto.Response toResponse(ProjectTransferRequest request) {
        if (request == null) {
            return null;
        }
        List<ProjectTransferDto.Response> responses = toResponses(List.of(request));
        return responses.isEmpty() ? null : responses.getFirst();
    }

    private void executeTransfer(ProjectTransferRequest request) {
        Optional<Project> projectOpt = projectRepository.findById(request.getProjectId());
        if (projectOpt.isEmpty() || workspaceQueryService.findWorkspaceById(request.getTargetWorkspaceId()).isEmpty()) {
            request.setStatus(ProjectTransferRequest.Status.REJECTED);
            request.setRejectionReason("Project or target workspace not found");
            transferRequestRepository.save(request);
            return;
        }

        Project project = projectOpt.get();
        Library targetLibrary = getOrCreateTargetLibrary(request.getTargetWorkspaceId());
        String targetProjectName = resolveTargetProjectName(
                project, request.getTransferType(), request.getTargetProjectName()
        );
        ensureProjectNameAvailable(project, targetLibrary, request.getTransferType(), targetProjectName);

        if (request.getTransferType() == ProjectTransferRequest.TransferType.COPY) {
            executeCopy(project, targetLibrary, targetProjectName);
        } else {
            executeMove(project, targetLibrary, request, targetProjectName);
        }

        request.setStatus(ProjectTransferRequest.Status.COMPLETED);
        transferRequestRepository.save(request);
    }

    private void executeMove(Project project, Library targetLibrary, ProjectTransferRequest request,
                             String targetProjectName) {
        projectStarService.handleProjectTransfer(
                request.getProjectId(),
                request.getSourceWorkspaceId(),
                request.getTargetWorkspaceId()
        );

        project.setName(targetProjectName);
        project.setLibrary(targetLibrary);
        project.setLocked(false);
        project.setLockedReason(null);
        projectRepository.save(project);
    }

    private void executeCopy(Project sourceProject, Library targetLibrary, String targetProjectName) {
        // Create new project
        Project newProject = new Project(
                targetProjectName,
                sourceProject.getDescription(),
                targetLibrary
        );
        newProject.setTags(new ArrayList<>(sourceProject.getTags()));
        newProject = projectRepository.save(newProject);

        // Copy pages with images and XMLs
        List<Page> sourcePages = pageOrderService.sortPages(pageRepository.findByProjectId(sourceProject.getId()));
        List<String> sourcePageIds = sourcePages.stream().map(Page::getId).toList();
        Map<String, List<PageImage>> imagesByPageId = pageImageRepository.findByPageIdIn(sourcePageIds).stream()
                .collect(java.util.stream.Collectors.groupingBy(image -> image.getPage().getId()));
        Map<String, PageXml> xmlByPageId = pageXmlRepository.findByPage_IdIn(sourcePageIds).stream()
                .collect(java.util.stream.Collectors.toMap(xml -> xml.getPage().getId(), xml -> xml));

        for (Page sourcePage : sourcePages) {
            Page newPage = new Page(sourcePage.getName(), sourcePage.getDescription(), newProject);
            newPage.setSortOrder(sourcePage.getSortOrder());
            newPage.setTags(new ArrayList<>(sourcePage.getTags()));
            newPage = pageRepository.save(newPage);

            // Copy images
            for (PageImage sourceImage : imagesByPageId.getOrDefault(sourcePage.getId(), List.of())) {
                String newFilePath = copyFile(sourceImage.getFilePath(), newProject.getId(), newPage.getId());
                PageImage newImage = new PageImage(
                        sourceImage.getFileName(), newFilePath, sourceImage.getMimeType(),
                        sourceImage.getFileSize(), sourceImage.getVariant(), sourceImage.getBaseName(), newPage
                );
                pageImageRepository.save(newImage);
            }

            // Copy XMLs
            for (PageXml sourceXml : Optional.ofNullable(xmlByPageId.get(sourcePage.getId())).stream().toList()) {
                String newFilePath = copyFile(sourceXml.getFilePath(), newProject.getId(), newPage.getId());
                PageXml newXml = new PageXml(
                        sourceXml.getFileName(), newFilePath, sourceXml.getMimeType(),
                        sourceXml.getFileSize(), sourceXml.getVariant(), sourceXml.getBaseName(),
                        sourceXml.getSchema(), sourceXml.getSchemaVersion(), newPage
                );
                pageXmlRepository.save(newXml);
            }
        }
    }

    private String copyFile(String sourcePath, String newProjectId, String newPageId) {
        try {
            Path source = Path.of(sourcePath);
            if (!Files.exists(source)) {
                return sourcePath; // Return original if file doesn't exist
            }
            String fileName = source.getFileName().toString();
            Path targetDir = source.getParent().getParent().getParent()
                    .resolve(newProjectId).resolve(newPageId);
            Files.createDirectories(targetDir);
            Path target = targetDir.resolve(fileName);
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            return target.toString();
        } catch (IOException e) {
            return sourcePath; // Return original on error
        }
    }

    private Library getOrCreateTargetLibrary(String targetWorkspaceId) {
        return libraryRepository.findByWorkspaceId(targetWorkspaceId)
                .orElseGet(() -> {
                    String name = workspaceQueryService.findWorkspaceById(targetWorkspaceId)
                            .map(w -> w.getName()).orElse("Unknown Workspace");
                    return libraryRepository.save(new Library(targetWorkspaceId, name));
                });
    }

    private Optional<Library> findTargetLibrary(String targetWorkspaceId) {
        return libraryRepository.findByWorkspaceId(targetWorkspaceId);
    }

    private String resolveTargetProjectName(Project project, ProjectTransferRequest.TransferType transferType,
                                            String requestedProjectName) {
        if (requestedProjectName != null && !requestedProjectName.isBlank()) {
            return requestedProjectName.trim();
        }
        return transferType == ProjectTransferRequest.TransferType.COPY
                ? project.getName() + " (Copy)"
                : project.getName();
    }

    private void ensureProjectNameAvailable(Project project, Library targetLibrary,
                                             ProjectTransferRequest.TransferType transferType,
                                             String targetProjectName) {
        projectRepository.findByNameAndLibraryId(targetProjectName, targetLibrary.getId())
                .filter(existing -> transferType == ProjectTransferRequest.TransferType.COPY
                        || !existing.getId().equals(project.getId()))
                .ifPresent(existing -> {
                    throw new ProjectNameConflictException(
                            targetProjectName, targetLibrary.getWorkspaceId()
                    );
                });
    }

    private boolean isUserAdministratorInWorkspace(String workspaceId, String userId) {
        return authorizationPolicyService.canManageProjects(workspaceId, userId);
    }
}

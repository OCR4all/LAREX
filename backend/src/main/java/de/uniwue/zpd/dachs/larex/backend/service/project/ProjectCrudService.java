package de.uniwue.zpd.dachs.larex.backend.service.project;

import de.uniwue.zpd.dachs.larex.backend.entity.Codec;
import de.uniwue.zpd.dachs.larex.backend.entity.LabelSet;
import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.entity.TagSet;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.WorkspaceMember;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.AbstractWorkspace;
import de.uniwue.zpd.dachs.larex.backend.repository.CodecRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.LabelSetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.LibraryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.TagSetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.WorkspaceMemberRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceQueryService;
import de.uniwue.zpd.dachs.larex.backend.service.NotificationService;
import de.uniwue.zpd.dachs.larex.backend.service.ProjectStarService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import de.uniwue.zpd.dachs.larex.backend.util.TextIndexDefaultsUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ProjectCrudService {
    private static final String PAGE_XML_STANDARD_LABEL_SET_NAME = "PAGE XML Standard";

    private final ProjectRepository projectRepository;
    private final LibraryRepository libraryRepository;
    private final CodecRepository codecRepository;
    private final LabelSetRepository labelSetRepository;
    private final TagSetRepository tagSetRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceQueryService workspaceQueryService;
    private final WorkspaceAccessService workspaceAccessService;
    private final NotificationService notificationService;
    private final ProjectStarService projectStarService;
    private final ProjectFileService projectFileService;

    public ProjectCrudService(ProjectRepository projectRepository,
                              LibraryRepository libraryRepository,
                              CodecRepository codecRepository,
                              LabelSetRepository labelSetRepository,
                              TagSetRepository tagSetRepository,
                              WorkspaceMemberRepository workspaceMemberRepository,
                              WorkspaceQueryService workspaceQueryService,
                              WorkspaceAccessService workspaceAccessService,
                              NotificationService notificationService,
                              ProjectStarService projectStarService,
                              ProjectFileService projectFileService) {
        this.projectRepository = projectRepository;
        this.libraryRepository = libraryRepository;
        this.codecRepository = codecRepository;
        this.labelSetRepository = labelSetRepository;
        this.tagSetRepository = tagSetRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.workspaceQueryService = workspaceQueryService;
        this.workspaceAccessService = workspaceAccessService;
        this.notificationService = notificationService;
        this.projectStarService = projectStarService;
        this.projectFileService = projectFileService;
    }

    public List<Project> getWorkspaceProjects(String workspaceId, String userId) {
        if (workspaceAccessService.hasWorkspaceAccess(workspaceId, userId)) {
            return projectRepository.findByLibraryWorkspaceId(workspaceId);
        }
        return List.of();
    }

    public Optional<Project> getProjectById(String projectId, String userId) {
        Optional<Project> projectOpt = projectRepository.findWithAssociationsById(projectId);
        if (projectOpt.isPresent()) {
            Project project = projectOpt.get();
            String workspaceId = project.getLibrary().getWorkspaceId();
            if (workspaceAccessService.hasWorkspaceAccess(workspaceId, userId)) {
                return projectOpt;
            }
        }
        return Optional.empty();
    }

    public Optional<Project> createProject(String workspaceId, String name, String description, List<String> tags,
                                           String codecId, String labelSetId, String tagSetId,
                                           Integer defaultGtIndex, List<Integer> defaultRecognitionIndices,
                                           String userId) {
        if (!workspaceAccessService.hasWorkspaceAccess(workspaceId, userId)) {
            throw new SecurityException("Cannot create project in this workspace. You may not have access to the workspace");
        }

        Optional<Library> libraryOpt = libraryRepository.findByWorkspaceId(workspaceId);
        if (libraryOpt.isEmpty()) {
            throw new IllegalArgumentException("No library found for this workspace. Please contact an administrator.");
        }

        Library library = libraryOpt.get();
        if (projectRepository.existsByNameAndLibraryId(name, library.getId())) {
            throw new IllegalArgumentException("Project name '" + name + "' already exists in this workspace");
        }

        Project project = new Project(name, description, library);
        if (tags != null) {
            project.setTags(tags);
        }

        AbstractWorkspace workspace = workspaceQueryService.findWorkspaceById(workspaceId).orElse(null);

        Codec codec = null;
        if (codecId != null && !codecId.trim().isEmpty()) {
            codec = codecRepository.findById(codecId).orElse(null);
        } else if (workspace != null && workspace.getCodec() != null) {
            codec = workspace.getCodec();
        }
        project.setCodec(codec);

        LabelSet labelSet = null;
        if (labelSetId != null && !labelSetId.trim().isEmpty()) {
            labelSet = labelSetRepository.findById(labelSetId).orElse(null);
        } else if (workspace != null && workspace.getLabelSet() != null) {
            labelSet = workspace.getLabelSet();
        } else {
            labelSet = labelSetRepository
                    .findByNameAndWorkspaceId(PAGE_XML_STANDARD_LABEL_SET_NAME, workspaceId)
                    .orElse(null);
        }
        project.setLabelSet(labelSet);

        TagSet tagSet = null;
        if (tagSetId != null && !tagSetId.trim().isEmpty()) {
            tagSet = tagSetRepository.findById(tagSetId).orElse(null);
        } else if (workspace != null && workspace.getTagSet() != null) {
            tagSet = workspace.getTagSet();
        }
        project.setTagSet(tagSet);

        var resolvedTextIndexDefaults = TextIndexDefaultsUtil.resolve(
                defaultGtIndex,
                defaultRecognitionIndices,
                workspace != null ? workspace.getDefaultGtIndex() : null,
                workspace != null ? workspace.getDefaultRecognitionIndicesList() : null
        );
        if ((defaultGtIndex != null || defaultRecognitionIndices != null) && workspace != null) {
            boolean changedFromWorkspace = !TextIndexDefaultsUtil.equalsDefaults(
                    workspace.getEffectiveDefaultGtIndex(),
                    workspace.getDefaultRecognitionIndicesList(),
                    resolvedTextIndexDefaults.gtIndex(),
                    resolvedTextIndexDefaults.recognitionIndices()
            );
            if (changedFromWorkspace && !userId.equals(workspace.getOwnerUserId())) {
                throw new SecurityException("Only the workspace owner can set custom project default text indices.");
            }
        }
        project.setDefaultGtIndex(resolvedTextIndexDefaults.gtIndex());
        project.setDefaultRecognitionIndicesList(resolvedTextIndexDefaults.recognitionIndices());

        project = projectRepository.save(project);
        notifyWorkspaceMembers(workspaceId, userId, "New project created: " + name, project.getId());

        return Optional.of(project);
    }

    public Optional<Project> updateProject(String projectId, String name, String description, List<String> tags,
                                           String codecId, String labelSetId, String tagSetId,
                                           Integer defaultGtIndex, List<Integer> defaultRecognitionIndices,
                                           String userId) {
        Optional<Project> projectOpt = getProjectById(projectId, userId);

        if (projectOpt.isPresent()) {
            Project project = projectOpt.get();
            if (!project.getName().equals(name) && projectRepository.existsByNameAndLibraryId(name, project.getLibrary().getId())) {
                throw new IllegalArgumentException("Project name '" + name + "' already exists in this workspace");
            }
            project.setName(name);
            project.setDescription(description);
            if (tags != null) {
                project.setTags(tags);
            }

            Codec codec = null;
            if (codecId != null && !codecId.trim().isEmpty()) {
                codec = codecRepository.findById(codecId).orElse(null);
            }
            project.setCodec(codec);

            LabelSet labelSet = null;
            if (labelSetId != null && !labelSetId.trim().isEmpty()) {
                labelSet = labelSetRepository.findById(labelSetId).orElse(null);
            }
            project.setLabelSet(labelSet);

            TagSet tagSet = null;
            if (tagSetId != null && !tagSetId.trim().isEmpty()) {
                tagSet = tagSetRepository.findById(tagSetId).orElse(null);
            }
            project.setTagSet(tagSet);

            if (defaultGtIndex != null || defaultRecognitionIndices != null) {
                String workspaceId = project.getLibrary().getWorkspaceId();
                AbstractWorkspace workspace = workspaceQueryService.findWorkspaceById(workspaceId).orElse(null);
                var resolvedTextIndexDefaults = TextIndexDefaultsUtil.resolve(
                        defaultGtIndex,
                        defaultRecognitionIndices,
                        project.getDefaultGtIndex(),
                        project.getDefaultRecognitionIndicesList()
                );

                boolean changedDefaults = !TextIndexDefaultsUtil.equalsDefaults(
                        project.getEffectiveDefaultGtIndex(),
                        project.getDefaultRecognitionIndicesList(),
                        resolvedTextIndexDefaults.gtIndex(),
                        resolvedTextIndexDefaults.recognitionIndices()
                );
                if (changedDefaults && (workspace == null || !userId.equals(workspace.getOwnerUserId()))) {
                    throw new SecurityException("Only the workspace owner can change project default text indices.");
                }

                project.setDefaultGtIndex(resolvedTextIndexDefaults.gtIndex());
                project.setDefaultRecognitionIndicesList(resolvedTextIndexDefaults.recognitionIndices());
            }

            return Optional.of(projectRepository.save(project));
        }
        return Optional.empty();
    }

    public boolean deleteProject(String projectId, String userId) {
        Optional<Project> projectOpt = getProjectById(projectId, userId);

        if (projectOpt.isPresent()) {
            Project project = projectOpt.get();
            String workspaceId = project.getLibrary().getWorkspaceId();

            if (workspaceAccessService.isUserAdministrator(workspaceId, userId)) {
                String projectName = project.getName();

                projectFileService.deleteProjectFiles(project);
                // Database cascade handles cleanup of all related data (stars, indices, recent projects, etc.)
                projectRepository.delete(project);

                notifyWorkspaceMembers(workspaceId, userId, "Project deleted: " + projectName, projectId);
                return true;
            }
        }
        return false;
    }

    public List<Project> searchProjects(String workspaceId, String searchTerm, String userId) {
        if (workspaceAccessService.hasWorkspaceAccess(workspaceId, userId)) {
            Optional<Library> libraryOpt = libraryRepository.findByWorkspaceId(workspaceId);
            if (libraryOpt.isPresent()) {
                return projectRepository.findByLibraryIdAndNameContainingIgnoreCase(libraryOpt.get().getId(), searchTerm);
            }
        }
        return List.of();
    }

    public List<Project> getProjectsByTags(String workspaceId, List<String> tags, String userId) {
        if (workspaceAccessService.hasWorkspaceAccess(workspaceId, userId)) {
            return projectRepository.findByLibraryWorkspaceIdAndTagsIn(workspaceId, tags);
        }
        return List.of();
    }

    private void notifyWorkspaceMembers(String workspaceId, String excludeUserId, String message, String projectId) {
        List<WorkspaceMember> members = workspaceMemberRepository.findByWorkspaceId(workspaceId);

        for (WorkspaceMember member : members) {
            if (!member.getUserId().equals(excludeUserId) &&
                    member.getInvitationStatus() == WorkspaceMember.InvitationStatus.ACCEPTED) {
                notificationService.createProjectCreatedNotification(member.getUserId(), message, projectId);
            }
        }
    }
}

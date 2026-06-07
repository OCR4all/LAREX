package de.uniwue.zpd.dachs.larex.backend.service.project;

import de.uniwue.zpd.dachs.larex.backend.entity.Codec;
import de.uniwue.zpd.dachs.larex.backend.entity.ControlledDictionary;
import de.uniwue.zpd.dachs.larex.backend.entity.LabelSet;
import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.entity.NormalizationProfile;
import de.uniwue.zpd.dachs.larex.backend.entity.TagSet;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.ValidationRuleset;
import de.uniwue.zpd.dachs.larex.backend.entity.VirtualKeyboard;
import de.uniwue.zpd.dachs.larex.backend.entity.WorkspaceMember;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.AbstractWorkspace;
import de.uniwue.zpd.dachs.larex.backend.exception.ResourceNotFoundException;
import de.uniwue.zpd.dachs.larex.backend.repository.codec.CodecRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.dictionary.ControlledDictionaryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.label.LabelSetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.library.LibraryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.normalization.NormalizationProfileRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.keyboard.VirtualKeyboardRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.tag.TagSetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.validation.ValidationRulesetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceMemberRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceQueryService;
import de.uniwue.zpd.dachs.larex.backend.service.notification.NotificationService;
import de.uniwue.zpd.dachs.larex.backend.service.project.ProjectStarService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaRefreshService;
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
    private final ControlledDictionaryRepository dictionaryRepository;
    private final LabelSetRepository labelSetRepository;
    private final TagSetRepository tagSetRepository;
    private final NormalizationProfileRepository normalizationProfileRepository;
    private final ValidationRulesetRepository validationRulesetRepository;
    private final VirtualKeyboardRepository virtualKeyboardRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceQueryService workspaceQueryService;
    private final WorkspaceAccessService workspaceAccessService;
    private final NotificationService notificationService;
    private final ProjectStarService projectStarService;
    private final ProjectFileService projectFileService;
    private final WorkspaceQuotaRefreshService workspaceQuotaRefreshService;

    public ProjectCrudService(ProjectRepository projectRepository,
                              LibraryRepository libraryRepository,
                              CodecRepository codecRepository,
                              ControlledDictionaryRepository dictionaryRepository,
                              LabelSetRepository labelSetRepository,
                              TagSetRepository tagSetRepository,
                              NormalizationProfileRepository normalizationProfileRepository,
                              ValidationRulesetRepository validationRulesetRepository,
                              VirtualKeyboardRepository virtualKeyboardRepository,
                              WorkspaceMemberRepository workspaceMemberRepository,
                              WorkspaceQueryService workspaceQueryService,
                              WorkspaceAccessService workspaceAccessService,
                              NotificationService notificationService,
                              ProjectStarService projectStarService,
                              ProjectFileService projectFileService,
                              WorkspaceQuotaRefreshService workspaceQuotaRefreshService) {
        this.projectRepository = projectRepository;
        this.libraryRepository = libraryRepository;
        this.codecRepository = codecRepository;
        this.dictionaryRepository = dictionaryRepository;
        this.labelSetRepository = labelSetRepository;
        this.tagSetRepository = tagSetRepository;
        this.normalizationProfileRepository = normalizationProfileRepository;
        this.validationRulesetRepository = validationRulesetRepository;
        this.virtualKeyboardRepository = virtualKeyboardRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.workspaceQueryService = workspaceQueryService;
        this.workspaceAccessService = workspaceAccessService;
        this.notificationService = notificationService;
        this.projectStarService = projectStarService;
        this.projectFileService = projectFileService;
        this.workspaceQuotaRefreshService = workspaceQuotaRefreshService;
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
                                           String codecId, String labelSetId, String dictionaryId, String tagSetId,
                                           String normalizationProfileId, String validationRulesetId,
                                           String virtualKeyboardId,
                                           Boolean allowCodecOverride, Boolean allowDictionaryOverride,
                                           Boolean allowVirtualKeyboardOverride, Boolean allowLabelSetOverride,
                                           Boolean allowTagSetOverride, Boolean allowNormalizationProfileOverride,
                                           Boolean allowValidationRulesetOverride,
                                           Integer defaultGtIndex, List<Integer> defaultRecognitionIndices,
                                           String userId) {
        workspaceAccessService.requireManageProjectsAccess(workspaceId, userId);

        Optional<Library> libraryOpt = libraryRepository.findByWorkspaceId(workspaceId);
        if (libraryOpt.isEmpty()) {
            throw new IllegalArgumentException("No projects found for this workspace. Please contact an administrator.");
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
        if (hasText(codecId)) {
            codec = codecRepository.findByIdAndLibraryWorkspaceId(codecId, workspaceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Codec", codecId));
        } else if (workspace != null && workspace.getCodec() != null) {
            codec = workspace.getCodec();
        }
        project.setCodec(codec);

        LabelSet labelSet = null;
        if (hasText(labelSetId)) {
            labelSet = labelSetRepository.findByIdAndWorkspaceId(labelSetId, workspaceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Label set", labelSetId));
        } else if (workspace != null && workspace.getLabelSet() != null) {
            labelSet = workspace.getLabelSet();
        } else {
            labelSet = labelSetRepository
                    .findByNameAndWorkspaceId(PAGE_XML_STANDARD_LABEL_SET_NAME, workspaceId)
                    .orElse(null);
        }
        project.setLabelSet(labelSet);

        ControlledDictionary dictionary = null;
        if (hasText(dictionaryId)) {
            dictionary = dictionaryRepository.findByIdAndLibraryWorkspaceId(dictionaryId, workspaceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Dictionary", dictionaryId));
        } else if (workspace != null && workspace.getDictionary() != null) {
            dictionary = workspace.getDictionary();
        }
        project.setDictionary(dictionary);

        TagSet tagSet = null;
        if (hasText(tagSetId)) {
            tagSet = tagSetRepository.findByIdAndWorkspaceId(tagSetId, workspaceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Tag set", tagSetId));
        } else if (workspace != null && workspace.getTagSet() != null) {
            tagSet = workspace.getTagSet();
        }
        project.setTagSet(tagSet);

        NormalizationProfile normalizationProfile = null;
        if (hasText(normalizationProfileId)) {
            normalizationProfile = normalizationProfileRepository.findByIdAndWorkspaceId(normalizationProfileId, workspaceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Normalization profile", normalizationProfileId));
        } else if (workspace != null && workspace.getNormalizationProfile() != null) {
            normalizationProfile = workspace.getNormalizationProfile();
        }
        project.setNormalizationProfile(normalizationProfile);

        ValidationRuleset validationRuleset = null;
        if (hasText(validationRulesetId)) {
            validationRuleset = validationRulesetRepository.findByIdAndWorkspaceId(validationRulesetId, workspaceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Validation ruleset", validationRulesetId));
        } else if (workspace != null && workspace.getValidationRuleset() != null) {
            validationRuleset = workspace.getValidationRuleset();
        }
        project.setValidationRuleset(validationRuleset);

        VirtualKeyboard virtualKeyboard = null;
        if (hasText(virtualKeyboardId)) {
            virtualKeyboard = virtualKeyboardRepository.findByIdAndWorkspaceId(virtualKeyboardId, workspaceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Virtual keyboard", virtualKeyboardId));
        }
        project.setVirtualKeyboard(virtualKeyboard);
        applyToolkitOverrideFlags(project,
                allowCodecOverride,
                allowDictionaryOverride,
                allowVirtualKeyboardOverride,
                allowLabelSetOverride,
                allowTagSetOverride,
                allowNormalizationProfileOverride,
                allowValidationRulesetOverride,
                true);

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
            if (changedFromWorkspace && !workspaceAccessService.canSetPresets(workspaceId, userId)) {
                throw new SecurityException("You do not have permission to set custom project default text indices.");
            }
        }
        project.setDefaultGtIndex(resolvedTextIndexDefaults.gtIndex());
        project.setDefaultRecognitionIndicesList(resolvedTextIndexDefaults.recognitionIndices());

        project = projectRepository.save(project);
        notifyWorkspaceMembers(workspaceId, userId, "New project created: " + name, project.getId());

        return Optional.of(project);
    }

    public Optional<Project> updateProject(String projectId, String name, String description, List<String> tags,
                                           String codecId, String labelSetId, String dictionaryId, String tagSetId,
                                           String normalizationProfileId, String validationRulesetId,
                                           String virtualKeyboardId,
                                           Boolean allowCodecOverride, Boolean allowDictionaryOverride,
                                           Boolean allowVirtualKeyboardOverride, Boolean allowLabelSetOverride,
                                           Boolean allowTagSetOverride, Boolean allowNormalizationProfileOverride,
                                           Boolean allowValidationRulesetOverride,
                                           Integer defaultGtIndex, List<Integer> defaultRecognitionIndices,
                                           String userId) {
        Optional<Project> projectOpt = getProjectById(projectId, userId);

        if (projectOpt.isPresent()) {
            Project project = projectOpt.get();
            String workspaceId = project.getLibrary().getWorkspaceId();
            workspaceAccessService.requireManageProjectsAccess(workspaceId, userId);
            if (!project.getName().equals(name) && projectRepository.existsByNameAndLibraryId(name, project.getLibrary().getId())) {
                throw new IllegalArgumentException("Project name '" + name + "' already exists in this workspace");
            }
            project.setName(name);
            project.setDescription(description);
            if (tags != null) {
                project.setTags(tags);
            }

            applyToolkitPresets(project, workspaceId, codecId, labelSetId, dictionaryId, tagSetId,
                    normalizationProfileId, validationRulesetId, virtualKeyboardId);
            applyToolkitOverrideFlags(project,
                    allowCodecOverride,
                    allowDictionaryOverride,
                    allowVirtualKeyboardOverride,
                    allowLabelSetOverride,
                    allowTagSetOverride,
                    allowNormalizationProfileOverride,
                    allowValidationRulesetOverride,
                    false);

            if (defaultGtIndex != null || defaultRecognitionIndices != null) {
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
                if (changedDefaults && !workspaceAccessService.canSetPresets(workspaceId, userId)) {
                    throw new SecurityException("You do not have permission to change project default text indices.");
                }

                project.setDefaultGtIndex(resolvedTextIndexDefaults.gtIndex());
                project.setDefaultRecognitionIndicesList(resolvedTextIndexDefaults.recognitionIndices());
            }

            return Optional.of(projectRepository.save(project));
        }
        return Optional.empty();
    }

    public Optional<Project> updateToolkitPresets(String workspaceId, String projectId,
                                                  String codecId, String labelSetId, String dictionaryId, String tagSetId,
                                                  String normalizationProfileId, String validationRulesetId,
                                                  String virtualKeyboardId,
                                                  Boolean allowCodecOverride, Boolean allowDictionaryOverride,
                                                  Boolean allowVirtualKeyboardOverride, Boolean allowLabelSetOverride,
                                                  Boolean allowTagSetOverride, Boolean allowNormalizationProfileOverride,
                                                  Boolean allowValidationRulesetOverride,
                                                  String userId) {
        workspaceAccessService.requireSetPresetsAccess(workspaceId, userId);
        Optional<Project> projectOpt = projectRepository.findByIdAndLibraryWorkspaceIdForUpdate(projectId, workspaceId);
        if (projectOpt.isEmpty()) {
            return Optional.empty();
        }

        Project project = projectOpt.get();
        if (project.isLocked()) {
            throw new SecurityException("Cannot change toolkit presets for a locked project.");
        }

        applyToolkitPresets(project, workspaceId, codecId, labelSetId, dictionaryId, tagSetId,
                normalizationProfileId, validationRulesetId, virtualKeyboardId);
        applyToolkitOverrideFlags(project,
                allowCodecOverride,
                allowDictionaryOverride,
                allowVirtualKeyboardOverride,
                allowLabelSetOverride,
                allowTagSetOverride,
                allowNormalizationProfileOverride,
                allowValidationRulesetOverride,
                false);

        return Optional.of(projectRepository.save(project));
    }

    public boolean deleteProject(String projectId, String userId) {
        Optional<Project> projectOpt = getProjectById(projectId, userId);

        if (projectOpt.isPresent()) {
            Project project = projectOpt.get();
            String workspaceId = project.getLibrary().getWorkspaceId();

            if (workspaceAccessService.canManageProjects(workspaceId, userId)) {
                String projectName = project.getName();

                projectFileService.deleteProjectFiles(project);
                // Database cascade handles cleanup of all related data (stars, indices, recent projects, etc.)
                projectRepository.delete(project);

                notifyWorkspaceMembers(workspaceId, userId, "Project deleted: " + projectName, projectId);
                workspaceQuotaRefreshService.scheduleUsageRefresh(workspaceId);
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

    private void applyToolkitPresets(Project project, String workspaceId,
                                     String codecId, String labelSetId, String dictionaryId, String tagSetId,
                                     String normalizationProfileId, String validationRulesetId,
                                     String virtualKeyboardId) {
        project.setCodec(hasText(codecId)
                ? codecRepository.findByIdAndLibraryWorkspaceId(codecId, workspaceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Codec", codecId))
                : null);
        project.setLabelSet(hasText(labelSetId)
                ? labelSetRepository.findByIdAndWorkspaceId(labelSetId, workspaceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Label set", labelSetId))
                : null);
        project.setDictionary(hasText(dictionaryId)
                ? dictionaryRepository.findByIdAndLibraryWorkspaceId(dictionaryId, workspaceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Dictionary", dictionaryId))
                : null);
        project.setTagSet(hasText(tagSetId)
                ? tagSetRepository.findByIdAndWorkspaceId(tagSetId, workspaceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Tag set", tagSetId))
                : null);
        project.setNormalizationProfile(hasText(normalizationProfileId)
                ? normalizationProfileRepository.findByIdAndWorkspaceId(normalizationProfileId, workspaceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Normalization profile", normalizationProfileId))
                : null);
        project.setValidationRuleset(hasText(validationRulesetId)
                ? validationRulesetRepository.findByIdAndWorkspaceId(validationRulesetId, workspaceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Validation ruleset", validationRulesetId))
                : null);
        project.setVirtualKeyboard(hasText(virtualKeyboardId)
                ? virtualKeyboardRepository.findByIdAndWorkspaceId(virtualKeyboardId, workspaceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Virtual keyboard", virtualKeyboardId))
                : null);
    }

    private void applyToolkitOverrideFlags(Project project,
                                           Boolean allowCodecOverride,
                                           Boolean allowDictionaryOverride,
                                           Boolean allowVirtualKeyboardOverride,
                                           Boolean allowLabelSetOverride,
                                           Boolean allowTagSetOverride,
                                           Boolean allowNormalizationProfileOverride,
                                           Boolean allowValidationRulesetOverride,
                                           boolean defaultWhenNull) {
        if (allowCodecOverride != null || defaultWhenNull) {
            project.setAllowCodecOverride(allowCodecOverride == null || allowCodecOverride);
        }
        if (allowDictionaryOverride != null || defaultWhenNull) {
            project.setAllowDictionaryOverride(allowDictionaryOverride == null || allowDictionaryOverride);
        }
        if (allowVirtualKeyboardOverride != null || defaultWhenNull) {
            project.setAllowVirtualKeyboardOverride(allowVirtualKeyboardOverride == null || allowVirtualKeyboardOverride);
        }
        if (allowLabelSetOverride != null || defaultWhenNull) {
            project.setAllowLabelSetOverride(allowLabelSetOverride == null || allowLabelSetOverride);
        }
        if (allowTagSetOverride != null || defaultWhenNull) {
            project.setAllowTagSetOverride(allowTagSetOverride == null || allowTagSetOverride);
        }
        if (allowNormalizationProfileOverride != null || defaultWhenNull) {
            project.setAllowNormalizationProfileOverride(allowNormalizationProfileOverride == null || allowNormalizationProfileOverride);
        }
        if (allowValidationRulesetOverride != null || defaultWhenNull) {
            project.setAllowValidationRulesetOverride(allowValidationRulesetOverride == null || allowValidationRulesetOverride);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

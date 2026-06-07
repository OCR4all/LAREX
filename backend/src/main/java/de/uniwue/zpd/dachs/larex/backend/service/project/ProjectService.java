package de.uniwue.zpd.dachs.larex.backend.service.project;

import de.uniwue.zpd.dachs.larex.backend.dto.BulkDeleteDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.exception.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProjectService {

    private final ProjectCrudService projectCrudService;

    public ProjectService(ProjectCrudService projectCrudService) {
        this.projectCrudService = projectCrudService;
    }

    public List<Project> getWorkspaceProjects(String workspaceId, String userId) {
        return projectCrudService.getWorkspaceProjects(workspaceId, userId);
    }

    public Optional<Project> getProjectById(String projectId, String userId) {
        return projectCrudService.getProjectById(projectId, userId);
    }

    public Optional<Project> createProject(String workspaceId, String name, String description, List<String> tags, String codecId, String labelSetId, String dictionaryId, String tagSetId,
                                           String normalizationProfileId, String validationRulesetId,
                                           String virtualKeyboardId,
                                           Boolean allowCodecOverride, Boolean allowDictionaryOverride,
                                           Boolean allowVirtualKeyboardOverride, Boolean allowLabelSetOverride,
                                           Boolean allowTagSetOverride, Boolean allowNormalizationProfileOverride,
                                           Boolean allowValidationRulesetOverride,
                                           Integer defaultGtIndex, List<Integer> defaultRecognitionIndices, String userId) {
        return projectCrudService.createProject(workspaceId, name, description, tags, codecId, labelSetId, dictionaryId, tagSetId,
                normalizationProfileId, validationRulesetId, virtualKeyboardId,
                allowCodecOverride, allowDictionaryOverride, allowVirtualKeyboardOverride, allowLabelSetOverride,
                allowTagSetOverride, allowNormalizationProfileOverride, allowValidationRulesetOverride,
                defaultGtIndex, defaultRecognitionIndices, userId);
    }

    public Optional<Project> updateProject(String projectId, String name, String description, List<String> tags, String codecId, String labelSetId, String dictionaryId, String tagSetId,
                                           String normalizationProfileId, String validationRulesetId,
                                           String virtualKeyboardId,
                                           Boolean allowCodecOverride, Boolean allowDictionaryOverride,
                                           Boolean allowVirtualKeyboardOverride, Boolean allowLabelSetOverride,
                                           Boolean allowTagSetOverride, Boolean allowNormalizationProfileOverride,
                                           Boolean allowValidationRulesetOverride,
                                           Integer defaultGtIndex, List<Integer> defaultRecognitionIndices, String userId) {
        return projectCrudService.updateProject(projectId, name, description, tags, codecId, labelSetId, dictionaryId, tagSetId,
                normalizationProfileId, validationRulesetId, virtualKeyboardId,
                allowCodecOverride, allowDictionaryOverride, allowVirtualKeyboardOverride, allowLabelSetOverride,
                allowTagSetOverride, allowNormalizationProfileOverride, allowValidationRulesetOverride,
                defaultGtIndex, defaultRecognitionIndices, userId);
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
        return projectCrudService.updateToolkitPresets(workspaceId, projectId, codecId, labelSetId, dictionaryId, tagSetId,
                normalizationProfileId, validationRulesetId, virtualKeyboardId,
                allowCodecOverride, allowDictionaryOverride, allowVirtualKeyboardOverride, allowLabelSetOverride,
                allowTagSetOverride, allowNormalizationProfileOverride, allowValidationRulesetOverride,
                userId);
    }

    public boolean deleteProject(String projectId, String userId) {
        return projectCrudService.deleteProject(projectId, userId);
    }

    public BulkDeleteDto.BulkDeleteResponse bulkDeleteProjects(String workspaceId, List<String> ids, String userId) {
        List<String> deletedIds = new ArrayList<>();
        List<String> failedIds = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (String projectId : new LinkedHashSet<>(ids)) {
            if (projectId == null || projectId.isBlank()) {
                failedIds.add(Objects.toString(projectId, "<null>"));
                errors.add("Cannot delete project with a blank ID.");
                continue;
            }

            try {
                Project project = getProjectById(projectId, userId)
                        .filter(candidate -> workspaceId.equals(candidate.getLibrary().getWorkspaceId()))
                        .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

                if (deleteProject(project.getId(), userId)) {
                    deletedIds.add(projectId);
                } else {
                    failedIds.add(projectId);
                    errors.add("Could not delete project " + projectId + ".");
                }
            } catch (RuntimeException ex) {
                failedIds.add(projectId);
                errors.add("Failed to delete project " + projectId + ": " + describeError(ex));
            }
        }

        return new BulkDeleteDto.BulkDeleteResponse(
                deletedIds.size(),
                failedIds.size(),
                deletedIds,
                failedIds,
                errors
        );
    }

    public List<Project> searchProjects(String workspaceId, String searchTerm, String userId) {
        return projectCrudService.searchProjects(workspaceId, searchTerm, userId);
    }

    public List<Project> getProjectsByTags(String workspaceId, List<String> tags, String userId) {
        return projectCrudService.getProjectsByTags(workspaceId, tags, userId);
    }

    private String describeError(RuntimeException ex) {
        return ex.getMessage() == null || ex.getMessage().isBlank() ? "Unexpected error" : ex.getMessage();
    }
}

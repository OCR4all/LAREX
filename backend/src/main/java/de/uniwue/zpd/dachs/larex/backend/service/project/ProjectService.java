package de.uniwue.zpd.dachs.larex.backend.service.project;

import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.service.project.ProjectCrudService;
import de.uniwue.zpd.dachs.larex.backend.service.upload.UnifiedUploadService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class ProjectService {

    private final ProjectCrudService projectCrudService;
    private final UnifiedUploadService unifiedUploadService;

    public ProjectService(ProjectCrudService projectCrudService, UnifiedUploadService unifiedUploadService) {
        this.projectCrudService = projectCrudService;
        this.unifiedUploadService = unifiedUploadService;
    }

    public List<Project> getWorkspaceProjects(String workspaceId, String userId) {
        return projectCrudService.getWorkspaceProjects(workspaceId, userId);
    }

    public Optional<Project> getProjectById(String projectId, String userId) {
        return projectCrudService.getProjectById(projectId, userId);
    }

    public Optional<Project> createProject(String workspaceId, String name, String description, List<String> tags, String codecId, String labelSetId, String tagSetId,
                                           Integer defaultGtIndex, List<Integer> defaultRecognitionIndices, String userId) {
        return projectCrudService.createProject(workspaceId, name, description, tags, codecId, labelSetId, tagSetId, defaultGtIndex, defaultRecognitionIndices, userId);
    }

    public Optional<Project> updateProject(String projectId, String name, String description, List<String> tags, String codecId, String labelSetId, String tagSetId,
                                           Integer defaultGtIndex, List<Integer> defaultRecognitionIndices, String userId) {
        return projectCrudService.updateProject(projectId, name, description, tags, codecId, labelSetId, tagSetId, defaultGtIndex, defaultRecognitionIndices, userId);
    }

    public boolean deleteProject(String projectId, String userId) {
        return projectCrudService.deleteProject(projectId, userId);
    }

    public List<Project> searchProjects(String workspaceId, String searchTerm, String userId) {
        return projectCrudService.searchProjects(workspaceId, searchTerm, userId);
    }

    public List<Project> getProjectsByTags(String workspaceId, List<String> tags, String userId) {
        return projectCrudService.getProjectsByTags(workspaceId, tags, userId);
    }

    public Map<String, Object> bulkUploadImages(String projectId, List<MultipartFile> files, String userId) throws IOException {
        projectCrudService.getProjectById(projectId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found or access denied"));
        return unifiedUploadService.bulkUploadImages(projectId, files, userId);
    }

    public Map<String, Object> importDataset(String projectId, List<MultipartFile> files, String userId) throws IOException {
        projectCrudService.getProjectById(projectId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found or access denied"));
        return unifiedUploadService.importDataset(projectId, files, userId);
    }
}

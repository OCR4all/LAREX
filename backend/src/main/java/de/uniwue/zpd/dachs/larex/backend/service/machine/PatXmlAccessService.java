package de.uniwue.zpd.dachs.larex.backend.service.machine;

import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageImageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import org.springframework.stereotype.Service;

@Service
public class PatXmlAccessService {

    private final ProjectRepository projectRepository;
    private final PageRepository pageRepository;
    private final PageImageRepository pageImageRepository;
    private final PageXmlRepository pageXmlRepository;

    public PatXmlAccessService(ProjectRepository projectRepository,
                               PageRepository pageRepository,
                               PageImageRepository pageImageRepository,
                               PageXmlRepository pageXmlRepository) {
        this.projectRepository = projectRepository;
        this.pageRepository = pageRepository;
        this.pageImageRepository = pageImageRepository;
        this.pageXmlRepository = pageXmlRepository;
    }

    public boolean projectBelongsToWorkspace(String projectId, String workspaceId) {
        return projectRepository.findByIdAndLibraryWorkspaceId(projectId, workspaceId).isPresent();
    }

    public boolean pageBelongsToProject(String pageId, String projectId) {
        return pageRepository.findByIdAndProjectId(pageId, projectId).isPresent();
    }

    public boolean xmlBelongsToProjectAndWorkspace(String xmlId, String projectId, String workspaceId) {
        return pageXmlRepository.findById(xmlId)
                .filter(xml -> xml.getPage() != null
                        && xml.getPage().getProject() != null
                        && xml.getPage().getProject().getLibrary() != null
                        && projectId.equals(xml.getPage().getProject().getId())
                        && workspaceId.equals(xml.getPage().getProject().getLibrary().getWorkspaceId()))
                .isPresent();
    }

    public boolean imageBelongsToProjectAndWorkspace(String imageId, String projectId, String workspaceId) {
        return pageImageRepository.findById(imageId)
                .filter(image -> image.getPage() != null
                        && image.getPage().getProject() != null
                        && image.getPage().getProject().getLibrary() != null
                        && projectId.equals(image.getPage().getProject().getId())
                        && workspaceId.equals(image.getPage().getProject().getLibrary().getWorkspaceId()))
                .isPresent();
    }

    public boolean xmlBelongsToPageInWorkspace(String xmlId,
                                               String pageId,
                                               String projectId,
                                               String workspaceId) {
        if (!projectBelongsToWorkspace(projectId, workspaceId)) {
            return false;
        }

        if (!pageBelongsToProject(pageId, projectId)) {
            return false;
        }

        return pageXmlRepository.findById(xmlId)
                .filter(xml -> xml.getPage() != null
                        && pageId.equals(xml.getPage().getId()))
                .isPresent();
    }
}

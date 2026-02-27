package de.uniwue.zpd.dachs.larex.backend.service.project;

import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.repository.PageImageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.service.HierarchicalFileStorageService;
import de.uniwue.zpd.dachs.larex.backend.service.PageXmlVersionService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProjectFileService {

    private final PageRepository pageRepository;
    private final PageImageRepository pageImageRepository;
    private final PageXmlRepository pageXmlRepository;
    private final HierarchicalFileStorageService hierarchicalFileStorageService;
    private final PageXmlVersionService pageXmlVersionService;

    public ProjectFileService(PageRepository pageRepository,
                              PageImageRepository pageImageRepository,
                              PageXmlRepository pageXmlRepository,
                              HierarchicalFileStorageService hierarchicalFileStorageService,
                              PageXmlVersionService pageXmlVersionService) {
        this.pageRepository = pageRepository;
        this.pageImageRepository = pageImageRepository;
        this.pageXmlRepository = pageXmlRepository;
        this.hierarchicalFileStorageService = hierarchicalFileStorageService;
        this.pageXmlVersionService = pageXmlVersionService;
    }

    public void deleteProjectFiles(Project project) {
        String workspaceId = project.getLibrary().getWorkspaceId();
        hierarchicalFileStorageService.deleteProjectTree(workspaceId, project.getId());

        List<Page> pages = pageRepository.findByProjectId(project.getId());
        for (Page page : pages) {
            deletePageFiles(page);
        }
    }

    public void deletePageFiles(Page page) {
        List<PageXml> xmlFiles = pageXmlRepository.findByPage_Id(page.getId());
        for (PageXml xml : xmlFiles) {
            pageXmlVersionService.deleteVersionDirectory(xml.getId());
            hierarchicalFileStorageService.deleteStoredFile(xml.getFilePath());
        }

        List<PageImage> images = pageImageRepository.findByPageId(page.getId());
        for (PageImage image : images) {
            hierarchicalFileStorageService.deleteStoredFile(image.getFilePath());
            if (image.getThumbnailPath() != null) {
                hierarchicalFileStorageService.deleteStoredFile(image.getThumbnailPath());
            }
        }
    }
}

package de.uniwue.zpd.dachs.larex.backend.service.project;

import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageImageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.service.storage.HierarchicalFileStorageService;
import de.uniwue.zpd.dachs.larex.backend.service.version.PageXmlVersionService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
        List<String> storagePaths = new ArrayList<>(xmlFiles.size() * 3);
        List<String> xmlIds = xmlFiles.stream()
                .map(PageXml::getId)
                .filter(Objects::nonNull)
                .toList();
        pageXmlVersionService.deleteVersionDirectories(xmlIds);

        for (PageXml xml : xmlFiles) {
            if (xml.getFilePath() != null) {
                storagePaths.add(xml.getFilePath());
            }
        }

        List<PageImage> images = pageImageRepository.findByPageId(page.getId());
        for (PageImage image : images) {
            if (image.getFilePath() != null) {
                storagePaths.add(image.getFilePath());
            }
            if (image.getThumbnailPath() != null) {
                storagePaths.add(image.getThumbnailPath());
            }
        }

        hierarchicalFileStorageService.deleteStoredFiles(storagePaths);
    }
}

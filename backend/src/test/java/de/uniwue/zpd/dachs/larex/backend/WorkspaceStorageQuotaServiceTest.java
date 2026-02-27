package de.uniwue.zpd.dachs.larex.backend;

import de.uniwue.zpd.dachs.larex.backend.entity.*;
import de.uniwue.zpd.dachs.larex.backend.repository.*;
import de.uniwue.zpd.dachs.larex.backend.service.WorkspaceStorageQuotaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class WorkspaceStorageQuotaServiceTest {

    @Autowired
    private WorkspaceStorageQuotaService quotaService;

    @Autowired
    private WorkspaceStorageQuotaRepository quotaRepository;

    @Autowired
    private LibraryRepository libraryRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private PageImageRepository pageImageRepository;

    @Autowired
    private PageXmlRepository pageXmlRepository;

    @Test
    void getOrCreateQuotaSeedsUsageFromWorkspaceFiles() {
        String workspaceId = "ws-usage-seed";

        Library library = libraryRepository.save(new Library(workspaceId, "Library"));
        Project project = projectRepository.save(new Project("Project", null, library));
        Page page = pageRepository.save(new Page("Page", null, project));

        pageImageRepository.save(new PageImage(
                "image.png",
                "/tmp/image.png",
                "image/png",
                100L,
                "original",
                "image",
                page
        ));

        pageXmlRepository.save(new PageXml(
                "page.xml",
                "/tmp/page.xml",
                "application/xml",
                200L,
                "default",
                "page",
                XmlSchema.PAGE_XML,
                "1.0",
                page
        ));

        WorkspaceStorageQuota quota = quotaService.getOrCreateQuota(workspaceId);
        assertEquals(300L, quota.getCurrentUsageBytes());
    }

    @Test
    void getOrCreateQuotaAutoSyncsExistingZeroUsageQuota() {
        String workspaceId = "ws-usage-sync";

        Library library = libraryRepository.save(new Library(workspaceId, "Library"));
        Project project = projectRepository.save(new Project("Project", null, library));
        Page page = pageRepository.save(new Page("Page", null, project));

        pageImageRepository.save(new PageImage(
                "image.png",
                "/tmp/image.png",
                "image/png",
                123L,
                "original",
                "image",
                page
        ));

        quotaRepository.save(new WorkspaceStorageQuota(workspaceId, 1_073_741_824L));

        WorkspaceStorageQuota quota = quotaService.getOrCreateQuota(workspaceId);
        assertEquals(123L, quota.getCurrentUsageBytes());
    }
}


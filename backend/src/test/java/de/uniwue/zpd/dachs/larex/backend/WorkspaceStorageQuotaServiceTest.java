package de.uniwue.zpd.dachs.larex.backend;

import de.uniwue.zpd.dachs.larex.backend.entity.*;
import de.uniwue.zpd.dachs.larex.backend.repository.library.LibraryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.dataset.DatasetItemRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.dataset.DatasetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.dataset.DatasetItemCopyFileRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageImageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceStorageQuotaRepository;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceStorageQuotaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Autowired
    private DatasetItemCopyFileRepository datasetItemCopyFileRepository;

    @Autowired
    private DatasetRepository datasetRepository;

    @Autowired
    private DatasetItemRepository datasetItemRepository;

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

    @Test
    void reserveBytesUsesEffectiveAvailableCapacity() {
        String workspaceId = "ws-reserve-capacity";

        quotaRepository.save(new WorkspaceStorageQuota(workspaceId, 1_000L));

        assertTrue(quotaService.reserveBytes(workspaceId, 600L));
        assertFalse(quotaService.reserveBytes(workspaceId, 500L));

        WorkspaceStorageQuota quota = quotaService.getOrCreateQuota(workspaceId);
        assertEquals(600L, quota.getReservedBytes());
        assertEquals(400L, quota.getAvailableBytes());
    }

    @Test
    void syncUsageAndReleaseReservationReleasesReservedBytes() {
        String workspaceId = "ws-sync-release";

        Library library = libraryRepository.save(new Library(workspaceId, "Library"));
        Project project = projectRepository.save(new Project("Project", null, library));
        Page page = pageRepository.save(new Page("Page", null, project));

        pageImageRepository.save(new PageImage(
                "image.png",
                "/tmp/image.png",
                "image/png",
                250L,
                "original",
                "image",
                page
        ));

        quotaRepository.save(new WorkspaceStorageQuota(workspaceId, 1_000L));
        assertTrue(quotaService.reserveBytes(workspaceId, 300L));

        WorkspaceStorageQuota updated = quotaService.syncUsageAndReleaseReservation(workspaceId, 300L);
        assertEquals(250L, updated.getCurrentUsageBytes());
        assertEquals(0L, updated.getReservedBytes());
        assertEquals(750L, updated.getAvailableBytes());
    }

    @Test
    void getOrCreateQuotaCountsDatasetCopyFiles() {
        String workspaceId = "ws-dataset-copy-usage";

        Dataset dataset = new Dataset();
        dataset.setWorkspaceId(workspaceId);
        dataset.setName("Training dataset");
        dataset = datasetRepository.save(dataset);

        DatasetItem item = new DatasetItem();
        item.setDataset(dataset);
        item.setSourceProjectId("project-1");
        item.setSourceProjectName("Project");
        item.setSourcePageId("page-1");
        item.setSourcePageName("Page");
        item.setMode(DatasetItem.Mode.COPY);
        item.setSelectedSourceXmlId("xml-1");
        item.setSelectedSourceXmlFileName("page.xml");
        item = datasetItemRepository.save(item);

        DatasetItemCopyFile xmlCopy = new DatasetItemCopyFile();
        xmlCopy.setDatasetItem(item);
        xmlCopy.setKind(DatasetItemCopyFile.Kind.XML);
        xmlCopy.setSourceFileId("xml-1");
        xmlCopy.setFileName("page.xml");
        xmlCopy.setFilePath("/tmp/dataset/page.xml");
        xmlCopy.setMimeType("application/xml");
        xmlCopy.setFileSize(125L);
        xmlCopy.setChecksumSha256("xml-checksum");

        DatasetItemCopyFile imageCopy = new DatasetItemCopyFile();
        imageCopy.setDatasetItem(item);
        imageCopy.setKind(DatasetItemCopyFile.Kind.IMAGE);
        imageCopy.setSourceFileId("img-1");
        imageCopy.setFileName("page.png");
        imageCopy.setFilePath("/tmp/dataset/page.png");
        imageCopy.setMimeType("image/png");
        imageCopy.setFileSize(375L);
        imageCopy.setChecksumSha256("img-checksum");

        datasetItemCopyFileRepository.save(xmlCopy);
        datasetItemCopyFileRepository.save(imageCopy);

        WorkspaceStorageQuota quota = quotaService.getOrCreateQuota(workspaceId);
        assertEquals(500L, quota.getCurrentUsageBytes());
    }
}

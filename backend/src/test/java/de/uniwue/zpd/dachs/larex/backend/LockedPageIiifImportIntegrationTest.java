package de.uniwue.zpd.dachs.larex.backend;

import de.uniwue.zpd.dachs.larex.backend.dto.IiifImportDto;
import de.uniwue.zpd.dachs.larex.backend.entity.*;
import de.uniwue.zpd.dachs.larex.backend.entity.StoredFile.StoredFileType;
import de.uniwue.zpd.dachs.larex.backend.repository.importing.IiifImportJobItemRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.importing.IiifImportJobRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.library.LibraryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageImageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.service.importer.AsyncIiifImportProcessor;
import de.uniwue.zpd.dachs.larex.backend.service.importer.IiifImageDownloader;
import de.uniwue.zpd.dachs.larex.backend.service.importer.IiifImportQueueService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.HierarchicalFileStorageService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.ThumbnailService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LockedPageIiifImportIntegrationTest {

    enum PageState {
        OPEN(true, null),
        ACTION_LOCKED(true, "Page is locked: LAREX Action running: OCR"),
        DONE(true, "Page is locked: Page workflow state is Done"),
        IN_REVIEW_WITHOUT_PERMISSION(false, "Page is locked: Page is in review"),
        IN_REVIEW_WITHOUT_CAPTURED_PERMISSION(null, "Page is locked: Page is in review"),
        IN_REVIEW_WITH_PERMISSION(true, null);

        final Boolean canReviewPages;
        final String lockReason;

        PageState(Boolean canReviewPages, String lockReason) {
            this.canReviewPages = canReviewPages;
            this.lockReason = lockReason;
        }

        boolean writable() {
            return lockReason == null;
        }

        void apply(Page page) {
            switch (this) {
                case ACTION_LOCKED -> {
                    page.setLocked(true);
                    page.setLockedReason("LAREX Action running: OCR");
                }
                case DONE -> page.setWorkflowState(Page.WorkflowState.DONE);
                case IN_REVIEW_WITHOUT_PERMISSION, IN_REVIEW_WITHOUT_CAPTURED_PERMISSION, IN_REVIEW_WITH_PERMISSION ->
                        page.setWorkflowState(Page.WorkflowState.IN_REVIEW);
                default -> { }
            }
        }
    }

    private static final String USER = "user";
    private static final String OLD_IMAGE = "old image";
    private static final String NEW_IMAGE = "new image";
    private static final String OLD_THUMBNAIL = "old thumbnail";
    private static final String NEW_THUMBNAIL = "new thumbnail";

    @TempDir static Path uploadDir;
    @TempDir static Path downloads;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("file.upload-dir", () -> uploadDir.toString());
    }

    @MockitoBean WorkspaceAccessService access;
    @MockitoBean IiifImageDownloader downloader;
    @MockitoBean IiifImportQueueService queue;
    @MockitoBean ThumbnailService thumbnails;
    @Autowired AsyncIiifImportProcessor imports;
    @Autowired HierarchicalFileStorageService storage;
    @Autowired LibraryRepository libraries;
    @Autowired ProjectRepository projects;
    @Autowired PageRepository pages;
    @Autowired PageImageRepository images;
    @Autowired IiifImportJobRepository jobs;
    @Autowired IiifImportJobItemRepository items;
    @Autowired ObjectMapper objectMapper;
    @Autowired PlatformTransactionManager transactionManager;

    private String workspace;
    private Project project;
    private TransactionTemplate transaction;

    @BeforeEach
    void setUp() throws Exception {
        workspace = "iiif-locks-" + UUID.randomUUID();
        Library library = libraries.save(new Library(workspace, "Library"));
        project = projects.save(new Project("Project", null, library));
        transaction = new TransactionTemplate(transactionManager);
        when(downloader.download(anyString(), anyString(), anyLong())).thenAnswer(invocation -> download());
        when(thumbnails.generateThumbnail(anyString())).thenAnswer(invocation -> thumbnail(NEW_THUMBNAIL));
    }

    @ParameterizedTest
    @EnumSource(PageState.class)
    void replacementRespectsThePageLock(PageState state) throws Exception {
        Page locked = createPage("0001", state);
        Page open = createPage("0002", PageState.OPEN);
        PageImage oldImage = iiifImage(locked);
        IiifImportJob job = job(
                replacement(0, locked, state.canReviewPages),
                replacement(1, open, state.canReviewPages));

        imports.processImportJob(job.getId(), "worker");

        assertImage(locked, state.writable());
        assertImage(open, true);
        assertThat(uploadDir.resolve(oldImage.getFilePath()).toFile().exists()).isEqualTo(!state.writable());
        assertThat(uploadDir.resolve(oldImage.getThumbnailPath()).toFile().exists()).isEqualTo(!state.writable());
        assertThat(pages.findById(locked.getId()).orElseThrow().getExternalSourceId())
                .isEqualTo(state.writable() ? "canvas-0" : null);
        IiifImportJob finished = jobs.findById(job.getId()).orElseThrow();
        assertThat(finished.getStatus()).isEqualTo(IiifImportJob.Status.COMPLETED);
        assertThat(finished.getFailedCanvases()).isZero();
        assertThat(finished.getProcessedCanvases()).isEqualTo(state.writable() ? 2 : 1);
        assertThat(finished.getSkippedCanvases()).isEqualTo(state.writable() ? 0 : 1);
        assertThat(result(job, 0).status()).isEqualTo(state.writable() ? "IMPORTED" : "SKIPPED");
        assertThat(result(job, 0).pageId()).isEqualTo(locked.getId());
        assertThat(result(job, 1).status()).isEqualTo("IMPORTED");
        if (state.writable()) {
            assertThat(finished.getErrorMessage()).isNull();
        } else {
            assertThat(result(job, 0).message()).isEqualTo(state.lockReason);
            assertThat(finished.getImportLog()).contains(state.lockReason);
            assertThat(finished.getErrorMessage())
                    .isEqualTo("Skipped images of locked pages: " + state.lockReason + " (1)");
            assertThat(items.sumImportedBytes(job.getId())).isEqualTo(NEW_IMAGE.length());
        }
        verifyNoInteractions(access);
    }

    @Test
    void replacementRespectsALockTakenDuringTheDownload() throws Exception {
        Page page = createPage("0001", PageState.OPEN);
        IiifImportJob job = job(replacement(0, page, true));
        when(downloader.download(anyString(), eq("0001"), anyLong())).thenAnswer(invocation -> {
            transaction.executeWithoutResult(status ->
                    PageState.ACTION_LOCKED.apply(pages.findById(page.getId()).orElseThrow()));
            return download();
        });

        imports.processImportJob(job.getId(), "worker");

        assertThat(result(job, 0))
                .returns("SKIPPED", IiifImportDto.ItemResult::status)
                .returns(PageState.ACTION_LOCKED.lockReason, IiifImportDto.ItemResult::message);
        assertImage(page, false);
    }

    @Test
    void replacementRespectsTheProjectLock() throws Exception {
        Page page = createPage("0001", PageState.OPEN);
        IiifImportJob job = job(replacement(0, page, true));
        transaction.executeWithoutResult(status -> {
            Project current = projects.findById(project.getId()).orElseThrow();
            current.setLocked(true);
            current.setLockedReason("Maintenance");
        });

        imports.processImportJob(job.getId(), "worker");

        assertThat(result(job, 0))
                .returns("SKIPPED", IiifImportDto.ItemResult::status)
                .returns("Page is locked: Maintenance", IiifImportDto.ItemResult::message);
        assertImage(page, false);
    }

    @Test
    void oldImageExistsUntilTheReplacementIsCommitted() throws Exception {
        Page page = createPage("0001", PageState.OPEN);
        PageImage oldImage = iiifImage(page);
        IiifImportJob job = job(replacement(0, page, true));
        when(thumbnails.generateThumbnail(anyString())).thenAnswer(invocation -> {
            assertThat(uploadDir.resolve(oldImage.getFilePath())).hasContent(OLD_IMAGE);
            assertThat(uploadDir.resolve(oldImage.getThumbnailPath())).hasContent(OLD_THUMBNAIL);
            return thumbnail(NEW_THUMBNAIL);
        });

        imports.processImportJob(job.getId(), "worker");

        assertThat(result(job, 0).status()).isEqualTo("IMPORTED");
        assertImage(page, true);
        assertThat(uploadDir.resolve(oldImage.getFilePath())).doesNotExist();
        assertThat(uploadDir.resolve(oldImage.getThumbnailPath())).doesNotExist();
    }

    @Test
    void failedReplacementKeepsTheExistingImage() throws Exception {
        Page page = createPage("0001", PageState.OPEN);
        Page next = createPage("0002", PageState.OPEN);
        IiifImportJob job = job(replacement(0, page, true), replacement(1, next, true));
        when(thumbnails.generateThumbnail(anyString()))
                .thenThrow(new IllegalStateException("thumbnail failed"))
                .thenAnswer(invocation -> thumbnail(NEW_THUMBNAIL));
        long storedFiles = storedFileCount();

        imports.processImportJob(job.getId(), "worker");

        assertThat(result(job, 0))
                .returns("FAILED", IiifImportDto.ItemResult::status)
                .returns("thumbnail failed", IiifImportDto.ItemResult::message);
        assertThat(result(job, 1).status()).isEqualTo("IMPORTED");
        assertImage(page, false);
        assertImage(next, true);
        assertThat(storedFileCount()).isEqualTo(storedFiles);
    }

    private Page createPage(String name, PageState state) throws IOException {
        Page page = new Page(name, null, project);
        state.apply(page);
        page = pages.save(page);
        Path input = Files.createTempFile(downloads, "input", ".png");
        Files.writeString(input, OLD_IMAGE);
        var stored = storage.storeFromPath(input, name + ".png", "image/png", workspace, project.getId(),
                StoredFileType.IMG, USER, true);
        PageImage image = new PageImage(name + ".png", stored.storagePath(), "image/png", stored.sizeBytes(), "iiif", name, page);
        image.setThumbnailPath(thumbnail(OLD_THUMBNAIL));
        images.save(image);
        return page;
    }

    private IiifImageDownloader.DownloadedImage download() throws IOException {
        Path path = Files.createTempFile(downloads, "download", ".png");
        Files.writeString(path, NEW_IMAGE);
        return new IiifImageDownloader.DownloadedImage(path, "image/png", "png", Files.size(path));
    }

    private String thumbnail(String content) throws IOException {
        Path directory = Files.createDirectories(uploadDir.resolve("thumbnails"));
        Path path = Files.createTempFile(directory, "thumb_", ".png");
        Files.writeString(path, content);
        return "thumbnails/" + path.getFileName();
    }

    private Map<String, Object> replacement(int index, Page page, Boolean canReviewPages) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("canvasId", "canvas-" + index);
        payload.put("canvasLabel", "Canvas " + index);
        payload.put("index", index);
        payload.put("requestedPageName", page.getName());
        payload.put("finalPageName", page.getName());
        payload.put("action", "REPLACE");
        payload.put("imageUrl", "https://iiif.example/" + index);
        payload.put("targetPageId", page.getId());
        payload.put("externalSourceMetadataJson", "{}");
        if (canReviewPages != null) {
            payload.put("canReviewPages", canReviewPages);
        }
        return payload;
    }

    @SafeVarargs
    private IiifImportJob job(Map<String, Object>... payloads) {
        IiifImportJob job = new IiifImportJob();
        job.setProjectId(project.getId());
        job.setWorkspaceId(workspace);
        job.setCreatedByUserId(USER);
        job.setSourceType(IiifImportJob.SourceType.MANIFEST_URL);
        job.setSourceReference("https://iiif.example/manifest");
        job.setTotalCanvases(payloads.length);
        job.setCanvasPayloadJson(objectMapper.writeValueAsString(Arrays.asList(payloads)));
        return jobs.save(job);
    }

    private IiifImportDto.ItemResult result(IiifImportJob job, int index) {
        return items.findByJobIdOrderByCanvasIndexAsc(job.getId()).get(index).toResult();
    }

    private PageImage iiifImage(Page page) {
        List<PageImage> current = images.findByPageIdAndVariant(page.getId(), "iiif");
        assertThat(current).hasSize(1);
        return current.getFirst();
    }

    private void assertImage(Page page, boolean replaced) {
        PageImage image = iiifImage(page);
        assertThat(uploadDir.resolve(image.getFilePath())).hasContent(replaced ? NEW_IMAGE : OLD_IMAGE);
        assertThat(uploadDir.resolve(image.getThumbnailPath())).hasContent(replaced ? NEW_THUMBNAIL : OLD_THUMBNAIL);
    }

    private long storedFileCount() throws IOException {
        try (var paths = Files.walk(uploadDir)) {
            return paths.filter(Files::isRegularFile).count();
        }
    }
}

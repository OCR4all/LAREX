package de.uniwue.zpd.dachs.larex.backend;

import de.uniwue.zpd.dachs.larex.backend.dto.NormalizationProfileDto;
import de.uniwue.zpd.dachs.larex.backend.dto.UploadConflictDto;
import de.uniwue.zpd.dachs.larex.backend.entity.*;
import de.uniwue.zpd.dachs.larex.backend.entity.StoredFile.StoredFileType;
import de.uniwue.zpd.dachs.larex.backend.entity.UploadSessionFile.UploadFileStatus;
import de.uniwue.zpd.dachs.larex.backend.repository.importing.ImportJobRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.library.LibraryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.normalization.NormalizationProfileRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.*;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.upload.UploadSessionFileRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.upload.UploadSessionRepository;
import de.uniwue.zpd.dachs.larex.backend.service.dataset.LinkedDatasetItemSync;
import de.uniwue.zpd.dachs.larex.backend.service.importer.AsyncImportProcessor;
import de.uniwue.zpd.dachs.larex.backend.service.normalization.NormalizationProfileService;
import de.uniwue.zpd.dachs.larex.backend.service.notification.NotificationService;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageWriteGuard;
import de.uniwue.zpd.dachs.larex.backend.service.storage.HierarchicalFileStorageService;
import de.uniwue.zpd.dachs.larex.backend.service.upload.AsyncUploadProcessor;
import de.uniwue.zpd.dachs.larex.backend.service.upload.UploadConflictService;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LockedPageWriteIntegrationTest {

    enum PageState {
        OPEN(true, null),
        ACTION_LOCKED(true, "Page is locked: LAREX Action running: OCR"),
        DONE(true, "Page is locked: Page workflow state is Done"),
        IN_REVIEW_WITHOUT_PERMISSION(false, "Page is locked: Page is in review"),
        IN_REVIEW_WITH_PERMISSION(true, null);

        final boolean canReviewPages;
        final String lockReason;

        PageState(boolean canReviewPages, String lockReason) {
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
                case IN_REVIEW_WITHOUT_PERMISSION, IN_REVIEW_WITH_PERMISSION ->
                        page.setWorkflowState(Page.WorkflowState.IN_REVIEW);
                default -> { }
            }
        }
    }

    private static final String USER = "user";
    private static final String OLD = "<Unicode>old</Unicode>";
    private static final String NEW = "<Unicode>new</Unicode>";

    @TempDir static Path uploadDir;
    @TempDir static Path tempDirectory;
    @TempDir Path source;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("file.upload-dir", () -> uploadDir.toString());
        registry.add("larex.upload.temp-directory", () -> tempDirectory.toString());
    }

    @MockitoBean WorkspaceAccessService access;
    @MockitoBean NotificationService notifications;
    @MockitoSpyBean LinkedDatasetItemSync linkedDatasetItemSync;
    @Autowired AsyncUploadProcessor uploads;
    @Autowired UploadConflictService conflicts;
    @Autowired AsyncImportProcessor imports;
    @Autowired NormalizationProfileService normalization;
    @Autowired PageWriteGuard guard;
    @Autowired HierarchicalFileStorageService storage;
    @Autowired LibraryRepository libraries;
    @Autowired ProjectRepository projects;
    @Autowired PageRepository pages;
    @Autowired PageImageRepository images;
    @Autowired PageXmlRepository xmls;
    @Autowired PageTextContentRepository texts;
    @Autowired UploadSessionRepository sessions;
    @Autowired UploadSessionFileRepository sessionFiles;
    @Autowired ImportJobRepository jobs;
    @Autowired NormalizationProfileRepository profiles;
    @Autowired PlatformTransactionManager transactionManager;

    private String workspace;
    private Project project;
    private TransactionTemplate transaction;

    @BeforeEach
    void setUp() {
        workspace = "locks-" + UUID.randomUUID();
        Library library = libraries.save(new Library(workspace, "Library"));
        project = projects.save(new Project("Project", null, library));
        transaction = new TransactionTemplate(transactionManager);
    }

    @ParameterizedTest
    @EnumSource(PageState.class)
    void uploadReplacementRespectsThePageLock(PageState state) throws Exception {
        Page locked = createPage("0001", state);
        Page open = createPage("0002", PageState.OPEN);
        UploadSession session = session(UploadSession.UploadSessionStatus.PROCESSING, 4);
        UploadSessionFile xml = upload(session, "0001.xml", pageXml("new"), UploadFileStatus.UPLOADED);
        UploadSessionFile image = upload(session, "0001.png", "new image", UploadFileStatus.UPLOADED);
        UploadSessionFile openXml = upload(session, "0002.xml", pageXml("new"), UploadFileStatus.UPLOADED);
        UploadSessionFile openImage = upload(session, "0002.png", "new image", UploadFileStatus.UPLOADED);

        uploads.doProcessUploadSession(session.getId());

        assertSettled(xml, state);
        assertSettled(image, state);
        assertFiles(locked, state.writable());
        assertSettled(openXml, PageState.OPEN);
        assertSettled(openImage, PageState.OPEN);
        assertFiles(open, true);
        assertThat(sessions.findById(session.getId()).orElseThrow().getStatus())
                .isEqualTo(UploadSession.UploadSessionStatus.COMPLETED);
    }

    @ParameterizedTest
    @EnumSource(PageState.class)
    void uploadAddsNoFilesToALockedPage(PageState state) throws Exception {
        Page page = new Page("0001", null, project);
        state.apply(page);
        page = pages.save(page);
        permit(state);
        UploadSession session = session(UploadSession.UploadSessionStatus.PROCESSING, 2);
        UploadSessionFile xml = upload(session, "0001.xml", pageXml("new"), UploadFileStatus.UPLOADED);
        UploadSessionFile image = upload(session, "0001.png", "new image", UploadFileStatus.UPLOADED);

        uploads.doProcessUploadSession(session.getId());

        assertSettled(xml, state);
        assertSettled(image, state);
        assertThat(xmls.findByPage_Id(page.getId()).isPresent()).isEqualTo(state.writable());
        assertThat(images.findByPageId(page.getId())).hasSize(state.writable() ? 1 : 0);
    }

    @Test
    void uploadCreatesNoPageInALockedProject() throws Exception {
        lockProject();
        UploadSession session = session(UploadSession.UploadSessionStatus.PROCESSING, 1);
        UploadSessionFile image = upload(session, "0001.png", "new image", UploadFileStatus.UPLOADED);

        uploads.doProcessUploadSession(session.getId());

        assertThat(sessionFiles.findById(image.getId()).orElseThrow().getErrorMessage())
                .isEqualTo("Project is locked: Maintenance");
        assertThat(pages.countByProjectId(project.getId())).isZero();
    }

    @Test
    void failedUploadReplacementKeepsTheExistingXml() throws Exception {
        Page page = createPage("0001", PageState.OPEN);
        UploadSession session = session(UploadSession.UploadSessionStatus.PROCESSING, 1);
        UploadSessionFile xml = upload(session, "0001.xml", pageXml("new"), UploadFileStatus.UPLOADED);
        doThrow(new IllegalStateException("relink failed")).when(linkedDatasetItemSync).relink(any());
        List<Path> before = storedFiles();

        uploads.doProcessUploadSession(session.getId());

        assertThat(sessionFiles.findById(xml.getId()).orElseThrow())
                .returns(UploadFileStatus.FAILED, UploadSessionFile::getStatus)
                .returns("relink failed", UploadSessionFile::getErrorMessage);
        assertThat(storedXml(page)).contains(OLD);
        assertThat(storedFiles()).isEqualTo(before);
    }

    @ParameterizedTest
    @EnumSource(PageState.class)
    void conflictResolutionRespectsThePageLock(PageState state) throws Exception {
        Page locked = createPage("0001", state);
        Page open = createPage("0002", PageState.OPEN);
        UploadSession session = session(UploadSession.UploadSessionStatus.COMPLETED, 3);
        UploadSessionFile xml = conflict(session, "0001.xml", pageXml("new"), "XML_FILE_EXISTS");
        UploadSessionFile image = conflict(session, "0001.png", "new image", "IMAGE_VARIANT_EXISTS");
        UploadSessionFile openXml = conflict(session, "0002.xml", pageXml("new"), "XML_FILE_EXISTS");
        UploadSessionFile openImage = conflict(session, "0002.png", "new image", "IMAGE_VARIANT_EXISTS");

        var response = resolve("REPLACE", xml, image, openXml, openImage);

        assertSettled(xml, state);
        assertSettled(image, state);
        assertFiles(locked, state.writable());
        assertSettled(openXml, PageState.OPEN);
        assertSettled(openImage, PageState.OPEN);
        assertFiles(open, true);
        if (state.writable()) {
            assertThat(response.skipped()).isEmpty();
        } else {
            assertThat(response.skipped()).containsExactlyInAnyOrder(
                    new UploadConflictDto.SkippedConflict(xml.getId(), "0001.xml", locked.getId(), "0001", state.lockReason),
                    new UploadConflictDto.SkippedConflict(image.getId(), "0001.png", locked.getId(), "0001", state.lockReason));
        }
    }

    @Test
    void keepingExistingFilesStaysPossibleOnALockedPage() throws Exception {
        Page page = createPage("0001", PageState.DONE);
        UploadSession session = session(UploadSession.UploadSessionStatus.COMPLETED, 1);
        UploadSessionFile xml = conflict(session, "0001.xml", pageXml("new"), "XML_FILE_EXISTS");

        assertThat(resolve("KEEP_EXISTING", xml).skipped()).isEmpty();

        assertThat(sessionFiles.findById(xml.getId()).orElseThrow())
                .returns(UploadFileStatus.SKIPPED, UploadSessionFile::getStatus)
                .returns(null, UploadSessionFile::getErrorMessage);
        assertFiles(page, false);
    }

    @ParameterizedTest
    @EnumSource(PageState.class)
    void serverImportRespectsThePageLock(PageState state) throws Exception {
        Page locked = createPage("0001", state);
        Page open = createPage("0002", PageState.OPEN);
        for (String name : List.of("0001", "0002")) {
            Files.writeString(source.resolve(name + ".xml"), pageXml("new"));
            Files.writeString(source.resolve(name + ".png"), "new image");
        }
        ImportJob job = new ImportJob(project.getId(), workspace, USER, source.toString());
        job.setOverwriteExisting(true);
        job = jobs.save(job);

        imports.doProcessImportJob(job.getId(), state.canReviewPages);

        assertFiles(locked, state.writable());
        assertFiles(open, true);
        ImportJob finished = jobs.findById(job.getId()).orElseThrow();
        assertThat(finished.getStatus()).isEqualTo(ImportJob.ImportJobStatus.COMPLETED);
        assertThat(finished.getFailedFiles()).isZero();
        assertThat(finished.getProcessedFiles()).isEqualTo(state.writable() ? 4 : 2);
        assertThat(finished.getSkippedFiles()).isEqualTo(state.writable() ? 0 : 2);
        if (state.writable()) {
            assertThat(finished.getErrorMessage()).isNull();
        } else {
            assertThat(finished.getErrorMessage()).isEqualTo("Skipped files of locked pages: " + state.lockReason + " (1)");
            assertThat(finished.getImportLog()).contains("Skipped files of 0001: " + state.lockReason);
        }
    }

    @ParameterizedTest
    @EnumSource(PageState.class)
    void normalizationRespectsThePageLock(PageState state) throws Exception {
        Page locked = createPage("0001", state);
        Page open = createPage("0002", PageState.OPEN);
        addText(locked);
        addText(open);
        permit(state);

        var response = applyNormalization();

        assertThat(storedXml(open)).contains(NEW);
        assertThat(storedXml(locked)).contains(state.writable() ? NEW : OLD);
        assertThat(response.changedPageCount()).isEqualTo(state.writable() ? 2 : 1);
        if (state.writable()) {
            assertThat(response.skippedPages()).isEmpty();
        } else {
            assertThat(response.skippedPages()).containsExactly(
                    new NormalizationProfileDto.SkippedPage(locked.getId(), "0001", state.lockReason));
            assertThat(response.message()).endsWith("Skipped locked pages: " + state.lockReason + " (1).");
        }
    }

    @Test
    void normalizationRejectsALockedProjectBeforeChangingPages() throws Exception {
        Page page = createPage("0001", PageState.OPEN);
        addText(page);
        lockProject();

        assertThatThrownBy(this::applyNormalization).isInstanceOf(IllegalStateException.class)
                .hasMessage("Project 'Project' is locked");
        assertThat(storedXml(page)).contains(OLD);
    }

    @Test
    void guardSeesALockTakenAfterThePageWasLoaded() throws Exception {
        Page page = createPage("0001", PageState.OPEN);
        transaction.executeWithoutResult(status -> {
            assertThat(pages.findById(page.getId()).orElseThrow().isLocked()).isFalse();
            CompletableFuture.runAsync(() -> lock(page)).join();

            assertThat(guard.lockPages(List.of(page.getId()), true))
                    .containsEntry(page.getId(), "Page is locked: LAREX Action running: OCR");
        });
    }

    @Test
    void guardWaitsForAConcurrentLock() throws Exception {
        Page page = createPage("0001", PageState.OPEN);
        CountDownLatch lockTaken = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var action = executor.submit(() -> transaction.executeWithoutResult(status -> {
                Page claimed = pages.findByIdInAndProjectIdForUpdate(List.of(page.getId()), project.getId()).getFirst();
                PageState.ACTION_LOCKED.apply(claimed);
                pages.saveAndFlush(claimed);
                lockTaken.countDown();
                await(commit);
            }));
            await(lockTaken);
            var writer = executor.submit(() -> transaction.execute(
                    status -> guard.lockPages(List.of(page.getId()), true)));
            Thread.sleep(300);
            assertThat(writer.isDone()).isFalse();
            commit.countDown();
            action.get(10, TimeUnit.SECONDS);
            assertThat(writer.get(10, TimeUnit.SECONDS)).containsKey(page.getId());
        }
    }

    private void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private void lock(Page page) {
        transaction.executeWithoutResult(status -> {
            Page current = pages.findById(page.getId()).orElseThrow();
            PageState.ACTION_LOCKED.apply(current);
        });
    }

    private void lockProject() {
        transaction.executeWithoutResult(status -> {
            Project current = projects.findById(project.getId()).orElseThrow();
            current.setLocked(true);
            current.setLockedReason("Maintenance");
        });
    }

    private void permit(PageState state) {
        when(access.canReviewPages(anyString(), anyString())).thenReturn(state.canReviewPages);
    }

    private Page createPage(String name, PageState state) throws IOException {
        Page page = new Page(name, null, project);
        state.apply(page);
        page = pages.save(page);
        var image = store("old image", name + ".png", "image/png", StoredFileType.IMG);
        images.save(new PageImage(name + ".png", image.storagePath(), "image/png", image.sizeBytes(), "png", name, page));
        var xml = store(pageXml("old"), name + ".xml", "application/xml", StoredFileType.XML);
        xmls.save(new PageXml(name + ".xml", xml.storagePath(), "application/xml", xml.sizeBytes(), "original", name,
                XmlSchema.PAGE_XML, null, page));
        if (state != PageState.OPEN) {
            permit(state);
        }
        return page;
    }

    private HierarchicalFileStorageService.StoredFileDescriptor store(String content, String filename, String mime,
                                                                      StoredFileType type) throws IOException {
        Path input = Files.createTempFile(tempDirectory, "input", filename);
        Files.writeString(input, content);
        return storage.storeFromPath(input, filename, mime, workspace, project.getId(), type, USER, true);
    }

    private UploadSession session(UploadSession.UploadSessionStatus status, int files) {
        UploadSession session = new UploadSession(project.getId(), workspace, USER, files, files);
        session.setStatus(status);
        return sessions.save(session);
    }

    private UploadSessionFile upload(UploadSession session, String filename, String content, UploadFileStatus status)
            throws IOException {
        Path path = tempDirectory.resolve(session.getId()).resolve(filename);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        boolean image = filename.endsWith(".png");
        UploadSessionFile file = new UploadSessionFile(filename, Files.size(path), image ? "image/png" : "application/xml",
                filename.substring(0, filename.indexOf('.')), image ? "png" : "xml", 1);
        file.setSession(session);
        file.setTempFilePath(path.toString());
        file.setStatus(status);
        file.setConflictResolution("replace");
        return sessionFiles.save(file);
    }

    private UploadSessionFile conflict(UploadSession session, String filename, String content, String type) throws IOException {
        UploadSessionFile file = upload(session, filename, content, UploadFileStatus.CONFLICT);
        file.setConflictType(type);
        return sessionFiles.save(file);
    }

    private UploadConflictDto.BatchConflictResolutionResponse resolve(String action, UploadSessionFile... files) {
        return conflicts.resolveConflicts(project.getId(), USER, new UploadConflictDto.BatchConflictResolutionRequest(
                java.util.Arrays.stream(files)
                        .map(file -> new UploadConflictDto.ConflictResolution(file.getId(), action))
                        .toList()));
    }

    private void addText(Page page) {
        texts.save(new PageTextContent(page, "l1", "r1", "old", 0));
    }

    private NormalizationProfileDto.ApplySourcesResponse applyNormalization() {
        NormalizationProfile profile = new NormalizationProfile();
        profile.setWorkspaceId(workspace);
        profile.setName("Profile " + UUID.randomUUID());
        NormalizationReplacementRule rule = new NormalizationReplacementRule();
        rule.setSearch("old");
        rule.setReplacement("new");
        profile.setReplacementRules(List.of(rule));
        profile = profiles.save(profile);
        return normalization.applySources(USER, workspace, profile.getId(), new NormalizationProfileDto.NormalizeSourcesRequest(
                List.of(new NormalizationProfileDto.ProjectScope(project.getId(), List.of())), null, null, null, null, null, null));
    }

    private void assertSettled(UploadSessionFile file, PageState state) {
        UploadSessionFile settled = sessionFiles.findById(file.getId()).orElseThrow();
        assertThat(settled.getErrorMessage()).isEqualTo(state.lockReason);
        assertThat(settled.getStatus()).isEqualTo(state.writable() ? UploadFileStatus.COMPLETED : UploadFileStatus.SKIPPED);
        assertThat(Path.of(file.getTempFilePath())).doesNotExist();
    }

    private void assertFiles(Page page, boolean replaced) throws IOException {
        assertThat(storedXml(page)).contains(replaced ? NEW : OLD);
        assertThat(images.findByPageId(page.getId())).singleElement().satisfies(image ->
                assertThat(uploadDir.resolve(image.getFilePath())).hasContent(replaced ? "new image" : "old image"));
    }

    private String storedXml(Page page) throws IOException {
        return Files.readString(uploadDir.resolve(xmls.findByPage_Id(page.getId()).orElseThrow().getFilePath()));
    }

    private List<Path> storedFiles() throws IOException {
        try (var paths = Files.walk(uploadDir.resolve("ws"))) {
            return paths.filter(Files::isRegularFile).sorted().toList();
        }
    }

    private static String pageXml(String text) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <PcGts xmlns="http://schema.primaresearch.org/PAGE/gts/pagecontent/2024-07-15">
                  <Metadata><Creator>test</Creator><Created>2026-01-01T00:00:00</Created><LastChange>2026-01-01T00:00:00</LastChange></Metadata>
                  <Page imageFilename="page.png" imageWidth="100" imageHeight="100">
                    <TextRegion id="r1"><Coords points="0,0 90,0 90,90 0,90"/>
                      <TextLine id="l1"><Coords points="0,0 90,0 90,20 0,20"/><TextEquiv index="0"><Unicode>%s</Unicode></TextEquiv></TextLine>
                    </TextRegion>
                  </Page>
                </PcGts>
                """.formatted(text);
    }
}

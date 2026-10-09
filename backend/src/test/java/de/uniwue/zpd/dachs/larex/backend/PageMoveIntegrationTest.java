package de.uniwue.zpd.dachs.larex.backend;

import de.uniwue.zpd.dachs.larex.backend.config.UploadDirectoryProperties;
import de.uniwue.zpd.dachs.larex.backend.dto.PageMoveDto.*;
import de.uniwue.zpd.dachs.larex.backend.entity.*;
import de.uniwue.zpd.dachs.larex.backend.entity.StoredFile.StoredFileType;
import de.uniwue.zpd.dachs.larex.backend.exception.PageMoveConflictException;
import de.uniwue.zpd.dachs.larex.backend.repository.library.LibraryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.*;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.search.SearchLexiconEntryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.storage.StoredFileRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.*;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.collaboration.AnnotationLeaseService;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageMoveService;
import de.uniwue.zpd.dachs.larex.backend.service.project.ProjectFileService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.HierarchicalFileStorageService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaRefreshService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import de.uniwue.zpd.dachs.larex.backend.repository.dataset.DatasetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.dataset.DatasetItemRepository;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class PageMoveIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired DatasetRepository datasets;
    @Autowired DatasetItemRepository datasetItems;
    @Autowired PageMoveService service;
    @Autowired HierarchicalFileStorageService storage;
    @Autowired ProjectFileService projectFiles;
    @Autowired UploadDirectoryProperties uploadDirectories;
    @Autowired LibraryRepository libraries;
    @Autowired ProjectRepository projects;
    @Autowired PageRepository pages;
    @Autowired PageImageRepository images;
    @Autowired PageXmlRepository xmls;
    @Autowired PageXmlVersionRepository versions;
    @Autowired PageTextContentRepository texts;
    @Autowired SearchLexiconEntryRepository lexicon;
    @Autowired StoredFileRepository storedFiles;
    @Autowired TaskRepository tasks;
    @Autowired TaskPageLinkRepository taskLinks;
    @Autowired SubtaskRepository subtasks;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean WorkspaceAccessService access;
    @Autowired AnnotationLeaseService leases;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    de.uniwue.zpd.dachs.larex.backend.service.page.PageMoveFileService moveFiles;
    @MockitoBean WorkspaceQuotaRefreshService quota;
    @TempDir Path root;
    String workspace;
    Project source;
    Project destination;
    TransactionTemplate transaction;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(storage, "uploadRoot", root.toAbsolutePath().normalize());
        uploadDirectories.setRootDirectory(root);
        when(access.canManageProjects(anyString(), anyString())).thenReturn(true);
        workspace = "move-" + UUID.randomUUID();
        Library library = libraries.save(new Library(workspace, "Library"));
        source = projects.save(new Project("Source", null, library));
        destination = projects.save(new Project("Destination", null, library));
        transaction = new TransactionTemplate(transactionManager);
    }

    @Test
    void preventsJoiningDuringCopyAndRejectsStaleContextsAfterCommit() throws Exception {
        Page page = createPage(source, "lease-race", "race");
        PageXml xml = xmls.findByPage_Id(page.getId()).orElseThrow();
        var user = new de.uniwue.zpd.dachs.larex.backend.dto.AnnotationCollaborationDto.UserSummary("user", "user", "User", null);
        var staleContext = new AnnotationLeaseService.RoomAccessContext(workspace, source.getId(), page.getId(),
                xml.getId(), source.getId() + ":" + page.getId() + ":" + xml.getId(),
                "Source", page.getName(), true, false, user, xml);
        Request reviewed = reviewed(List.of(page.getId()), ConflictPolicy.SKIP, "", "");
        CountDownLatch copying = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            copying.countDown();
            assertThat(resume.await(10, TimeUnit.SECONDS)).isTrue();
            return invocation.callRealMethod();
        }).when(moveFiles).copyAssets(org.mockito.ArgumentMatchers.anyList(), anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.any());
        try (var executor = Executors.newSingleThreadExecutor()) {
            var move = executor.submit(() -> service.move(workspace, source.getId(), reviewed, "user"));
            try {
                assertThat(copying.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> leases.joinLease(staleContext, "editor"))
                        .isInstanceOf(de.uniwue.zpd.dachs.larex.backend.exception.AnnotationLeaseLockedException.class)
                        .hasMessageContaining("being moved");
            } finally {
                resume.countDown();
            }
            assertThat(move.get(15, TimeUnit.SECONDS).movedCount()).isEqualTo(1);
        }
        assertThatThrownBy(() -> leases.joinLease(staleContext, "editor"))
                .isInstanceOf(de.uniwue.zpd.dachs.larex.backend.exception.AnnotationLeaseLockedException.class)
                .hasMessageContaining("moved or was replaced");
        var destinationContext = new AnnotationLeaseService.RoomAccessContext(workspace, destination.getId(), page.getId(),
                xml.getId(), destination.getId() + ":" + page.getId() + ":" + xml.getId(),
                "Destination", page.getName(), true, false, user, xml);
        assertThat(leases.joinLease(destinationContext, "editor").editor()).isNotNull();
        leases.releaseLease(destinationContext, "editor");
    }

    @Test
    void movesAssetsHistoryTasksAndSearchAndSurvivesSourceProjectDeletion() throws Exception {
        Page sourcePage = createPage(source, "001", "source");
        Page existing = pages.save(new Page("existing", null, destination));
        PageXml xml = xmls.findByPage_Id(sourcePage.getId()).orElseThrow();
        PageImage image = images.findByPageId(sourcePage.getId()).getFirst();
        String originalImagePath = image.getFilePath();
        String originalXmlPath = xml.getFilePath();
        String versionPath = createVersion(xml);
        Task task = tasks.save(new Task("Task", null, "user", Task.TaskPriority.MEDIUM, workspace));
        taskLinks.save(new TaskPageLink(task.getId(), sourcePage.getId(), TaskPageLink.LinkType.MANUAL, "user"));
        texts.save(new PageTextContent(sourcePage, "line", "region", "movedtoken", 0));

        Preview result = execute(List.of(sourcePage.getId()), ConflictPolicy.SKIP, "", "");
        assertThat(result.movedCount()).isEqualTo(1);
        transaction.executeWithoutResult(status -> {
            Page moved = pages.findById(sourcePage.getId()).orElseThrow();
            assertThat(moved.getProject().getId()).isEqualTo(destination.getId());
            assertThat(moved.getTags()).containsExactly("tag");
            assertThat(moved.getDescription()).isEqualTo("source");
            PageImage movedImage = images.findById(image.getId()).orElseThrow();
            PageXml movedXml = xmls.findById(xml.getId()).orElseThrow();
            assertThat(movedImage.getFilePath()).contains("/pr/" + destination.getId() + "/");
            assertThat(movedXml.getFilePath()).contains("/pr/" + destination.getId() + "/");
            assertThat(storage.resolveUploadPath(movedImage.getFilePath())).hasContent("source-image");
            assertThat(storage.resolveUploadPath(movedImage.getThumbnailPath())).hasContent("source-thumb");
            assertThat(storage.resolveUploadPath(movedXml.getFilePath())).hasContent("<PcGts>source</PcGts>");
            assertThat(versions.findByPageXml_IdOrderByVersionNumberDesc(xml.getId())).hasSize(1);
            assertThat(taskLinks.findByPageId(sourcePage.getId())).hasSize(1);
            assertThat(pages.findByProjectId(destination.getId())).hasSize(2);
            assertThat(pages.findById(existing.getId())).isPresent();
        });
        assertThat(root.resolve(versionPath)).hasContent("history");
        assertThat(root.resolve(originalImagePath)).doesNotExist();
        assertThat(root.resolve(originalXmlPath)).doesNotExist();
        assertThat(lexicon.findByWorkspaceId(workspace)).allMatch(entry -> entry.getProjectId().equals(destination.getId()));
        assertThat(lexicon.findByWorkspaceId(workspace)).hasSize(1);

        transaction.executeWithoutResult(status -> {
            Project project = projects.findById(source.getId()).orElseThrow();
            projectFiles.deleteProjectFiles(project);
            projects.delete(project);
        });
        PageImage movedImage = images.findById(image.getId()).orElseThrow();
        assertThat(root.resolve(movedImage.getFilePath())).hasContent("source-image");
        assertThat(root.resolve(versionPath)).hasContent("history");
        assertThat(storedFiles.findByStoragePath(movedImage.getFilePath()).orElseThrow().getProjectId())
                .isEqualTo(destination.getId());
    }

    @Test
    void overwriteDeletesDestinationRecordsAndFilesButKeepsSourceIdsAndHistory() throws Exception {
        Page incoming = createPage(source, "001", "incoming");
        Page replaced = createPage(destination, "001", "replaced");
        PageXml incomingXml = xmls.findByPage_Id(incoming.getId()).orElseThrow();
        PageXml replacedXml = xmls.findByPage_Id(replaced.getId()).orElseThrow();
        String keptVersion = createVersion(incomingXml);
        String deletedVersion = createVersion(replacedXml);
        Task task = tasks.save(new Task("Task", null, "user", Task.TaskPriority.MEDIUM, workspace));
        taskLinks.save(new TaskPageLink(task.getId(), replaced.getId(), TaskPageLink.LinkType.MANUAL, "user"));
        Subtask subtask = new Subtask(task.getId(), "Review page", 0);
        subtask.setPageId(replaced.getId());
        subtasks.save(subtask);
        texts.save(new PageTextContent(replaced, "line", "region", "obsolete", 0));

        execute(List.of(incoming.getId()), ConflictPolicy.OVERWRITE, "", "");
        assertThat(pages.findById(replaced.getId())).isEmpty();
        assertThat(xmls.findById(replacedXml.getId())).isEmpty();
        assertThat(images.findByPageId(replaced.getId())).isEmpty();
        assertThat(taskLinks.findByPageId(replaced.getId())).isEmpty();
        assertThat(subtasks.findByPageId(replaced.getId())).isEmpty();
        assertThat(root.resolve(replacedXml.getFilePath())).doesNotExist();
        assertThat(root.resolve(deletedVersion)).doesNotExist();
        assertThat(root.resolve(keptVersion)).hasContent("history");
        assertThat(xmls.findByPage_Id(incoming.getId()).orElseThrow().getId()).isEqualTo(incomingXml.getId());
        transaction.executeWithoutResult(status -> assertThat(pages.findByProjectId(destination.getId()))
                .extracting(Page::getId).containsExactly(incoming.getId()));
    }

    @Test
    void transactionRollbackRestoresPagesAssetsAndOverwriteTargetsAndRemovesCopiedFiles() throws Exception {
        Page incoming = createPage(source, "001", "incoming");
        Page replaced = createPage(destination, "001", "replaced");
        PageXml originalXml = xmls.findByPage_Id(incoming.getId()).orElseThrow();
        String history = createVersion(xmls.findByPage_Id(replaced.getId()).orElseThrow());
        List<String> before = physicalFiles();
        Request request = reviewed(List.of(incoming.getId()), ConflictPolicy.OVERWRITE, "", "");
        transaction.executeWithoutResult(status -> {
            try {
                service.move(workspace, source.getId(), request, "user");
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
            status.setRollbackOnly();
        });
        assertThat(physicalFiles()).containsExactlyElementsOf(before);
        transaction.executeWithoutResult(status -> {
            assertThat(pages.findById(incoming.getId()).orElseThrow().getProject().getId()).isEqualTo(source.getId());
            assertThat(pages.findById(replaced.getId())).isPresent();
            assertThat(xmls.findByPage_Id(incoming.getId()).orElseThrow().getFilePath()).isEqualTo(originalXml.getFilePath());
        });
        assertThat(root.resolve(history)).hasContent("history");
    }

    @Test
    void missingFileRollsBackAlreadyCopiedAssets() throws Exception {
        Page incoming = createPage(source, "001", "incoming");
        PageXml xml = xmls.findByPage_Id(incoming.getId()).orElseThrow();
        Files.delete(root.resolve(xml.getFilePath()));
        List<String> before = physicalFiles();
        Request request = reviewed(List.of(incoming.getId()), ConflictPolicy.SKIP, "", "");
        assertThatThrownBy(() -> service.move(workspace, source.getId(), request, "user")).isInstanceOf(IOException.class);
        assertThat(physicalFiles()).containsExactlyElementsOf(before);
        transaction.executeWithoutResult(status -> assertThat(pages.findById(incoming.getId()).orElseThrow()
                .getProject().getId()).isEqualTo(source.getId()));
    }

    @Test
    void renameKeepsNonclashingNamesAndAppendsInSourceOrder() throws Exception {
        Page first = createPage(source, "001", "one");
        Page second = createPage(source, "002", "two");
        transaction.executeWithoutResult(status -> {
            pages.findById(first.getId()).orElseThrow().setSortOrder(2000);
            pages.findById(second.getId()).orElseThrow().setSortOrder(1000);
        });
        createPage(destination, "001", "existing");
        Preview result = execute(List.of(first.getId(), second.getId()), ConflictPolicy.RENAME, "pre-", "-post");
        assertThat(result.items()).extracting(Item::resultingName).containsExactly("002", "pre-001-post");
        assertThat(result.renamedCount()).isEqualTo(1);
        transaction.executeWithoutResult(status -> {
            Page movedFirst = pages.findById(first.getId()).orElseThrow();
            Page movedSecond = pages.findById(second.getId()).orElseThrow();
            assertThat(movedFirst.getSortOrder()).isGreaterThan(movedSecond.getSortOrder());
            assertThat(movedFirst.getName()).isEqualTo("pre-001-post");
            assertThat(xmls.findByPage_Id(first.getId()).orElseThrow().getFileName()).isEqualTo("001.xml");
        });
    }

    @Test
    void changedDestinationRejectsReviewedMoveWithoutCopyingAnyFiles() throws Exception {
        Page incoming = createPage(source, "001", "incoming");
        Request request = reviewed(List.of(incoming.getId()), ConflictPolicy.OVERWRITE, "", "");
        createPage(destination, "001", "new clash");
        List<String> before = physicalFiles();
        assertThatThrownBy(() -> service.move(workspace, source.getId(), request, "user"))
                .isInstanceOf(PageMoveConflictException.class);
        assertThat(physicalFiles()).containsExactlyElementsOf(before);
    }

    @Test
    void endpointsValidateRequestsAndReturnReviewedResultsAndConflicts() throws Exception {
        Page incoming = createPage(source, "001", "incoming");
        String route = "/workspaces/" + workspace + "/projects/" + source.getId() + "/pages/move";
        Request request = new Request(List.of(incoming.getId()), destination.getId(), ConflictPolicy.SKIP, "", "", null);
        String json = mvc.perform(post(route + "/preview")
                .with(jwt().jwt(jwt -> jwt.subject("user")))
                .contentType("application/json").content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movedCount").value(1))
                .andReturn().getResponse().getContentAsString();
        Preview preview = mapper.readValue(json, Preview.class);
        Request reviewed = new Request(request.pageIds(), destination.getId(), ConflictPolicy.SKIP, "", "", preview.fingerprint());
        mvc.perform(post(route)
                .with(jwt().jwt(jwt -> jwt.subject("user")))
                .contentType("application/json").content(mapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAGE_MOVE_CONFLICT"));
        mvc.perform(post(route + "/preview")
                .with(jwt().jwt(jwt -> jwt.subject("user")))
                .contentType("application/json").content("{\"pageIds\":[],\"destinationProjectId\":\"destination\",\"conflictPolicy\":\"SKIP\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(route)
                .with(jwt().jwt(jwt -> jwt.subject("user")))
                .contentType("application/json").content(mapper.writeValueAsString(reviewed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].pageId").value(incoming.getId()));
    }

    @Test
    void endpointsRejectForbiddenAndCrossWorkspaceMoves() throws Exception {
        Page incoming = createPage(source, "001", "incoming");
        String route = "/workspaces/" + workspace + "/projects/" + source.getId() + "/pages/move/preview";
        Library otherLibrary = libraries.save(new Library("other-" + workspace, "Other workspace"));
        Project other = projects.save(new Project("Other project", null, otherLibrary));
        Request crossWorkspace = new Request(List.of(incoming.getId()), other.getId(), ConflictPolicy.SKIP, "", "", null);
        mvc.perform(post(route)
                .with(jwt().jwt(jwt -> jwt.subject("user")))
                .contentType("application/json").content(mapper.writeValueAsString(crossWorkspace)))
                .andExpect(status().isNotFound());
        when(access.canManageProjects(workspace, "user")).thenReturn(false);
        mvc.perform(post(route)
                .with(jwt().jwt(jwt -> jwt.subject("user")))
                .contentType("application/json").content(mapper.writeValueAsString(crossWorkspace)))
                .andExpect(status().isForbidden());
    }

    @Test
    void linkedDatasetsFollowMovedPagesWhileCopiedSnapshotsKeepProvenance() throws Exception {
        Page incoming = createPage(source, "001", "incoming");
        PageXml xml = xmls.findByPage_Id(incoming.getId()).orElseThrow();
        Dataset linkedDataset = new Dataset();
        linkedDataset.setWorkspaceId(workspace);
        linkedDataset.setName("Linked dataset");
        linkedDataset = datasets.save(linkedDataset);
        Dataset copiedDataset = new Dataset();
        copiedDataset.setWorkspaceId(workspace);
        copiedDataset.setName("Copied dataset");
        copiedDataset = datasets.save(copiedDataset);
        DatasetItem linked = datasetItems.save(datasetItem(linkedDataset, incoming, xml, DatasetItem.Mode.LINK));
        DatasetItem copied = datasetItems.save(datasetItem(copiedDataset, incoming, xml, DatasetItem.Mode.COPY));
        createPage(destination, "001", "existing");
        execute(List.of(incoming.getId()), ConflictPolicy.RENAME, "pre-", "");
        DatasetItem updated = datasetItems.findById(linked.getId()).orElseThrow();
        assertThat(updated.getSourceProjectId()).isEqualTo(destination.getId());
        assertThat(updated.getSourceProjectName()).isEqualTo(destination.getName());
        assertThat(updated.getSourcePageName()).isEqualTo("pre-001");
        DatasetItem snapshot = datasetItems.findById(copied.getId()).orElseThrow();
        assertThat(snapshot.getSourceProjectId()).isEqualTo(source.getId());
        assertThat(snapshot.getSourcePageName()).isEqualTo("001");
    }

    @Test
    void concurrentMovesCannotOverwriteAPageThatWasAbsentFromThePreview() throws Exception {
        Project otherSource = projects.save(new Project("Other source", null, source.getLibrary()));
        Page first = createPage(source, "001", "first");
        Page second = createPage(otherSource, "001", "second");
        Request firstRequest = reviewed(List.of(first.getId()), ConflictPolicy.OVERWRITE, "", "");
        Request secondDraft = new Request(List.of(second.getId()), destination.getId(), ConflictPolicy.OVERWRITE, "", "", null);
        Request secondRequest = new Request(secondDraft.pageIds(), destination.getId(), ConflictPolicy.OVERWRITE, "", "",
                service.preview(workspace, otherSource.getId(), secondDraft, "user").fingerprint());
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstMove = executor.submit(() -> concurrentMove(start, source.getId(), firstRequest));
            var secondMove = executor.submit(() -> concurrentMove(start, otherSource.getId(), secondRequest));
            start.countDown();
            assertThat(List.of(firstMove.get(20, TimeUnit.SECONDS),
                    secondMove.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("moved", "stale");
        }
        transaction.executeWithoutResult(status -> {
            assertThat(pages.findByProjectId(destination.getId())).hasSize(1);
            assertThat(pages.findByProjectId(source.getId()).size() + pages.findByProjectId(otherSource.getId()).size())
                    .isEqualTo(1);
        });
    }

    private String concurrentMove(CountDownLatch start, String sourceId, Request request) throws Exception {
        start.await();
        try {
            service.move(workspace, sourceId, request, "user");
            return "moved";
        } catch (PageMoveConflictException exception) {
            return "stale";
        }
    }

    private DatasetItem datasetItem(Dataset dataset, Page page, PageXml xml, DatasetItem.Mode mode) {
        DatasetItem item = new DatasetItem();
        item.setDataset(dataset);
        item.setSourceProjectId(source.getId());
        item.setSourceProjectName(source.getName());
        item.setSourcePageId(page.getId());
        item.setSourcePageName(page.getName());
        item.setMode(mode);
        item.setSelectedSourceXmlId(xml.getId());
        item.setSelectedSourceXmlFileName(xml.getFileName());
        return item;
    }

    private Page createPage(Project project, String name, String content) throws IOException {
        Page page = new Page(name, content, project);
        page.setTags(List.of("tag"));
        page = pages.save(page);
        var image = store(content + "-image", project, name + ".png", "image/png", StoredFileType.IMG);
        var thumb = store(content + "-thumb", project, name + ".png", "image/png", StoredFileType.THUMB);
        PageImage pageImage = new PageImage(name + ".png", image.storagePath(), "image/png", image.sizeBytes(), "original", name, page);
        pageImage.setThumbnailPath(thumb.storagePath());
        images.save(pageImage);
        var xml = store("<PcGts>" + content + "</PcGts>", project, name + ".xml", "application/xml", StoredFileType.XML);
        xmls.save(new PageXml(name + ".xml", xml.storagePath(), "application/xml", xml.sizeBytes(), "original", name, XmlSchema.PAGE_XML, null, page));
        return page;
    }

    private HierarchicalFileStorageService.StoredFileDescriptor store(String content, Project project,
            String filename, String mime, StoredFileType type) throws IOException {
        Path input = root.resolve("input");
        Files.writeString(input, content);
        return storage.storeFromPath(input, filename, mime, workspace, project.getId(), type, "user", false);
    }

    private String createVersion(PageXml xml) throws IOException {
        String path = "xml/versions/" + xml.getId() + "/1.xml";
        Files.createDirectories(root.resolve(path).getParent());
        Files.writeString(root.resolve(path), "history");
        versions.save(new PageXmlVersion(xml, 1, path, 7L, "user", "Initial"));
        return path;
    }

    private List<String> physicalFiles() throws IOException {
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).map(path -> root.relativize(path).toString()).sorted().toList();
        }
    }

    private Request reviewed(List<String> ids, ConflictPolicy policy, String prefix, String suffix) {
        Request request = new Request(ids, destination.getId(), policy, prefix, suffix, null);
        return new Request(ids, destination.getId(), policy, prefix, suffix,
                service.preview(workspace, source.getId(), request, "user").fingerprint());
    }

    private Preview execute(List<String> ids, ConflictPolicy policy, String prefix, String suffix) throws IOException {
        return service.move(workspace, source.getId(), reviewed(ids, policy, prefix, suffix), "user");
    }
}

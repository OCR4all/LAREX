package de.uniwue.zpd.dachs.larex.backend.service.action;

import de.uniwue.zpd.dachs.larex.backend.config.UploadDirectoryProperties;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto;
import de.uniwue.zpd.dachs.larex.backend.entity.*;
import de.uniwue.zpd.dachs.larex.backend.repository.action.*;
import de.uniwue.zpd.dachs.larex.backend.repository.dataset.*;
import de.uniwue.zpd.dachs.larex.backend.repository.page.*;
import de.uniwue.zpd.dachs.larex.backend.service.storage.*;
import de.uniwue.zpd.dachs.larex.backend.service.upload.UploadPathService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ActionIntegrationSnapshotServiceTest {
    @TempDir Path root;
    ActionInputSnapshotRepository snapshots = mock(ActionInputSnapshotRepository.class);
    PageRepository pages = mock(PageRepository.class);
    PageXmlRepository xmls = mock(PageXmlRepository.class);
    PageImageRepository images = mock(PageImageRepository.class);
    DatasetItemRepository items = mock(DatasetItemRepository.class);
    WorkspaceQuotaGuardService quota = mock(WorkspaceQuotaGuardService.class);
    WorkspaceQuotaRefreshService refresh = mock(WorkspaceQuotaRefreshService.class);
    List<ActionInputSnapshot> saved = new ArrayList<>();
    ActionTrainingInputRepository trainingInputs = mock(ActionTrainingInputRepository.class);
    ActionTrainingSnapshotService service;
    ActionRun run;

    @BeforeEach void setup() throws Exception {
        UploadDirectoryProperties properties = new UploadDirectoryProperties();
        properties.setRootDirectory(root);
        service = new ActionTrainingSnapshotService(mock(DatasetRepository.class), items, pages,
                images, xmls, trainingInputs, mock(WorkspaceAccessService.class),
                new UploadPathService(properties), quota, refresh, snapshots);
        run = new ActionRun(); run.setId("integration-run"); run.setWorkspaceId("workspace");
        run.setProjectId("project"); run.setKind(ActionRun.Kind.INTEGRATION);
        Files.writeString(root.resolve("source.xml"), "original xml");
        PageXml xml = new PageXml(); xml.setId("xml"); xml.setFileName("source.xml"); xml.setFilePath("source.xml");
        xml.setMimeType("application/xml");
        when(xmls.findById("xml")).thenReturn(Optional.of(xml));
        when(snapshots.saveAll(anyList())).thenAnswer(call -> {
            saved.addAll(call.getArgument(0)); return saved;
        });
    }

    List<ActionDto.MachinePageInput> xmlInput() {
        return List.of(new ActionDto.MachinePageInput("page", "Page", "page", null, List.of(),
                List.of(new ActionDto.MachinePageFile("xml", "source.xml", null, "application/xml", 12L, "unused"))));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"images", "xml", "both"})
    void freezesDatasetCopyInputsWithOptionalFilesAndNoSplit(String types) throws Exception {
        run.setProjectId(null); run.setDatasetId("dataset");
        Dataset dataset = new Dataset(); dataset.setId("dataset");
        DatasetItem item = new DatasetItem(); item.setId("item"); item.setSourcePageId("page");
        item.setSourcePageName("Page"); item.setMode(DatasetItem.Mode.COPY); item.setStatus(DatasetItem.Status.READY);
        item.setAssignedSplit(null); item.setSelectedSourceXmlId("xml"); item.setSelectedSourceImageIds(List.of("image"));
        List<DatasetItemCopyFile> files = new ArrayList<>();
        if (!types.equals("images")) files.add(copyFile(DatasetItemCopyFile.Kind.XML, "xml", "source.xml"));
        if (!types.equals("xml")) {
            Files.writeString(root.resolve("image.png"), "image");
            files.add(copyFile(DatasetItemCopyFile.Kind.IMAGE, "image", "image.png"));
        }
        item.setCopyFiles(files);
        when(items.findByDatasetIdOrderByCreatedAsc("dataset")).thenReturn(List.of(item));
        ActionDto.InputRequirements requirements = new ActionDto.InputRequirements(
                new ActionDto.InputRequirement(ActionDto.InputLevel.OPTIONAL, List.of()),
                new ActionDto.InputRequirement(types.equals("images") ? ActionDto.InputLevel.NONE : ActionDto.InputLevel.OPTIONAL, List.of()));
        service.createDatasetIntegrationSnapshot(run, dataset, new ActionDto.StartTrainingRunRequest("definition",
                new ActionDto.TrainingSelection("ALL", List.of()), new ActionDto.TrainingImageSelection(types.equals("xml") ? "original" : null, Map.of()), Map.of(), true), requirements);
        assertThat(saved).hasSize(types.equals("both") ? 2 : 1);
        assertThat(saved).allMatch(file -> "item".equals(file.getInputId()) && file.getSplit() == null);
        assertThat(run.getInputCount()).isEqualTo(1);
    }

    @Test void freezesMultipleDatasetVariantsForEveryDatasetActionKind() throws Exception {
        run.setProjectId(null); run.setDatasetId("dataset");
        Dataset dataset = new Dataset(); dataset.setId("dataset");
        DatasetItem item = new DatasetItem(); item.setId("item"); item.setSourcePageId("page");
        item.setSourcePageName("Page"); item.setMode(DatasetItem.Mode.COPY); item.setStatus(DatasetItem.Status.READY);
        item.setAssignedSplit(DatasetItem.Split.TRAIN); item.setSelectedSourceXmlId("xml");
        item.setSelectedSourceImageIds(List.of("original", "scan", "other"));
        List<DatasetItemCopyFile> files = new ArrayList<>();
        files.add(copyFile(DatasetItemCopyFile.Kind.XML, "xml", "source.xml"));
        for (String variant : item.getSelectedSourceImageIds()) {
            Files.writeString(root.resolve(variant + ".png"), variant);
            var image = copyFile(DatasetItemCopyFile.Kind.IMAGE, variant, variant + ".png");
            image.setVariant(variant); files.add(image);
        }
        item.setCopyFiles(files);
        when(items.findByDatasetIdOrderByCreatedAsc("dataset")).thenReturn(List.of(item));
        var splits = new ActionDto.TrainingSplitRequirements(ActionDto.InputLevel.REQUIRED,
                ActionDto.InputLevel.NONE, ActionDto.InputLevel.NONE);
        for (boolean perItem : List.of(false, true)) {
            var selection = new ActionDto.TrainingImageSelection(null, null,
                    List.of("original", "scan"), perItem ? Map.of("item", List.of("original", "scan")) : Map.of());
            var request = new ActionDto.StartTrainingRunRequest("definition",
                    new ActionDto.TrainingSelection("ALL", List.of()), selection, Map.of(), true);
            var single = new ActionDto.InputRequirement(ActionDto.InputLevel.REQUIRED, List.of());
            assertThatThrownBy(() -> service.createSnapshot("workspace", dataset, run, request, splits, single))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("does not accept multiple image variants");
            assertThatThrownBy(() -> service.createEvaluationSnapshot("workspace", dataset, run,
                    new ActionDto.StartEvaluationRunRequest("definition", request.selection(), selection, Map.of(), true), splits, single))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("does not accept multiple image variants");
            assertThatThrownBy(() -> service.createDatasetIntegrationSnapshot(run, dataset, request,
                    new ActionDto.InputRequirements(single, single)))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("does not accept multiple image variants");
            run.setKind(ActionRun.Kind.INTEGRATION); saved.clear();
            service.createDatasetIntegrationSnapshot(run, dataset, request, new ActionDto.InputRequirements(
                    new ActionDto.InputRequirement(ActionDto.InputLevel.REQUIRED, List.of(), true),
                    new ActionDto.InputRequirement(ActionDto.InputLevel.REQUIRED, List.of())));
            assertThat(saved).filteredOn(file -> "images".equals(file.getFileType()))
                    .extracting(ActionInputSnapshot::getVariant).containsExactly("original", "scan");
            for (ActionRun.Kind kind : List.of(ActionRun.Kind.TRAINING, ActionRun.Kind.EVALUATION)) {
                run.setKind(kind);
                ActionTrainingSnapshotService.SnapshotResult result = kind == ActionRun.Kind.TRAINING
                        ? service.createSnapshot("workspace", dataset, run, request, splits, new ActionDto.InputRequirement(ActionDto.InputLevel.REQUIRED, List.of(), true))
                        : service.createEvaluationSnapshot("workspace", dataset, run,
                        new ActionDto.StartEvaluationRunRequest("definition", request.selection(), selection, Map.of(), true), splits, new ActionDto.InputRequirement(ActionDto.InputLevel.REQUIRED, List.of(), true));
                assertThat(result.inputCount()).isEqualTo(1);
                assertThat(result.splitCounts()).containsOnly(entry("TRAIN", 1L));
                @SuppressWarnings({"unchecked", "rawtypes"})
                org.mockito.ArgumentCaptor<List<ActionTrainingInput>> captured = org.mockito.ArgumentCaptor.forClass((Class) List.class);
                verify(trainingInputs, atLeastOnce()).saveAll(captured.capture());
                List<ActionTrainingInput> frozen = captured.getValue();
                assertThat(frozen).extracting(ActionTrainingInput::getImageVariant).containsExactly("original", "scan");
                assertThat(frozen).allSatisfy(input ->
                        assertThat(Files.readString(root.resolve(input.getImageSnapshotPath()))).isEqualTo(input.getImageVariant()));
            }
        }
    }

    @Test void freezesLinkedDatasetFilesBeforeSourceEdits() throws Exception {
        run.setProjectId(null); run.setDatasetId("dataset");
        Dataset dataset = new Dataset(); dataset.setId("dataset");
        DatasetItem item = new DatasetItem(); item.setId("item"); item.setSourcePageId("page");
        item.setSourcePageName("Page"); item.setMode(DatasetItem.Mode.LINK); item.setStatus(DatasetItem.Status.READY);
        item.setSelectedSourceXmlId("xml"); item.setSelectedSourceImageIds(List.of());
        Library library = new Library(); library.setWorkspaceId("workspace");
        Project project = new Project(); project.setLibrary(library);
        Page page = new Page(); page.setId("page"); page.setProject(project);
        when(pages.findById("page")).thenReturn(Optional.of(page));
        xmls.findById("xml").orElseThrow().setPage(page);
        when(items.findByDatasetIdOrderByCreatedAsc("dataset")).thenReturn(List.of(item));
        service.createDatasetIntegrationSnapshot(run, dataset, new ActionDto.StartTrainingRunRequest("definition",
                new ActionDto.TrainingSelection("SELECTED", List.of("item")), null, Map.of(), true),
                new ActionDto.InputRequirements(new ActionDto.InputRequirement(ActionDto.InputLevel.NONE, List.of()),
                        new ActionDto.InputRequirement(ActionDto.InputLevel.REQUIRED, List.of())));
        Files.writeString(root.resolve("source.xml"), "changed linked source");
        assertThat(saved).hasSize(1);
        assertThat(Files.readString(root.resolve(saved.getFirst().getSnapshotPath()))).isEqualTo("original xml");
    }

    DatasetItemCopyFile copyFile(DatasetItemCopyFile.Kind kind, String id, String path) throws Exception {
        DatasetItemCopyFile file = new DatasetItemCopyFile(); file.setKind(kind); file.setSourceFileId(id);
        file.setFileName(path); file.setFilePath(path); file.setMimeType("application/octet-stream");
        file.setFileSize(Files.size(root.resolve(path)));
        return file;
    }

    @Test void freezesXmlOnlyQueuedInputAndCleansBytesButKeepsMetadata() throws Exception {
        run.setStatus(ActionRun.Status.QUEUED);
        service.createProjectIntegrationSnapshot(run, xmlInput());
        Files.writeString(root.resolve("source.xml"), "edited xml");
        assertThat(saved).hasSize(1);
        assertThat(Files.readString(root.resolve(saved.getFirst().getSnapshotPath()))).isEqualTo("original xml");
        assertThat(saved.getFirst().getChecksumSha256()).hasSize(64);
        assertThat(run.getInputCount()).isEqualTo(1);
        service.cleanupSnapshot(run);
        assertThat(Files.exists(root.resolve(saved.getFirst().getSnapshotPath()))).isFalse();
        assertThat(Files.readString(root.resolve("source.xml"))).isEqualTo("edited xml");
        assertThat(run.getSnapshotCleanedAt()).isNotNull();
        verify(snapshots, never()).deleteAll(anyList());
        verify(refresh).scheduleUsageRefresh("workspace");
    }

    @Test void snapshotsMultipleImagesWithoutXml() throws Exception {
        List<ActionDto.MachinePageFile> files = new ArrayList<>();
        for (String id : List.of("one", "two")) {
            Files.writeString(root.resolve(id + ".png"), id);
            PageImage image = new PageImage(); image.setId(id); image.setFileName(id + ".png");
            image.setFilePath(id + ".png"); image.setMimeType("image/png");
            when(images.findById(id)).thenReturn(Optional.of(image));
            files.add(new ActionDto.MachinePageFile(id, id + ".png", null, "image/png", 3L, "unused"));
        }
        service.createProjectIntegrationSnapshot(run, List.of(new ActionDto.MachinePageInput("page", "Page", "page", null, files, List.of())));
        assertThat(saved).hasSize(2).allMatch(file -> "images".equals(file.getFileType()));
        assertThat(run.getInputCount()).isEqualTo(1);
    }

    @Test void removesPartialSnapshotAndReleasesQuotaOnPersistenceFailure() {
        when(quota.reserveBytesOrThrow(anyString(), anyLong(), anyString())).thenReturn(12L);
        when(snapshots.saveAll(anyList())).thenThrow(new IllegalStateException("database failure"));
        assertThatThrownBy(() -> service.createProjectIntegrationSnapshot(run, xmlInput())).hasMessage("database failure");
        assertThat(Files.exists(root.resolve("actions/integration/integration-run"))).isFalse();
        verify(quota).syncUsageAndReleaseReservation("workspace", 12L);
    }

    @Test void rollsBackSnapshotAfterLaterTransactionFailure() throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.createProjectIntegrationSnapshot(run, xmlInput());
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
            assertThat(Files.exists(root.resolve(saved.getFirst().getSnapshotPath()))).isFalse();
            verify(refresh).scheduleUsageRefresh("workspace");
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }

    @Test void quotaFailureCreatesNoSnapshot() {
        when(quota.reserveBytesOrThrow(anyString(), anyLong(), anyString())).thenThrow(new IllegalStateException("quota exceeded"));
        assertThatThrownBy(() -> service.createProjectIntegrationSnapshot(run, xmlInput())).hasMessage("quota exceeded");
        assertThat(Files.exists(root.resolve("actions/integration/integration-run"))).isFalse();
        verify(snapshots, never()).saveAll(anyList());
    }

    @Test void rejectsCrossRunAndWrongTypeDownloads() {
        ActionInputSnapshot file = new ActionInputSnapshot(); file.setRun(run); file.setFileType("xml");
        when(snapshots.findById("file")).thenReturn(Optional.of(file));
        assertThat(service.integrationFile(run.getId(), "xml", "file")).isSameAs(file);
        assertThatThrownBy(() -> service.integrationFile("other-run", "xml", "file")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.integrationFile(run.getId(), "images", "file")).isInstanceOf(IllegalArgumentException.class);
    }
}

package de.uniwue.zpd.dachs.larex.backend.service.action;

import de.uniwue.zpd.dachs.larex.backend.entity.ActionOutput;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.dto.StorageCleanupDto;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionOutputFileRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionOutputRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.storage.StoredFileRepository;
import de.uniwue.zpd.dachs.larex.backend.service.storage.HierarchicalFileStorageService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaGuardService;
import de.uniwue.zpd.dachs.larex.backend.service.upload.UploadPathService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ActionOutputServiceAdminTest {

    @Mock
    private ActionOutputRepository outputRepository;
    @Mock
    private ActionOutputFileRepository outputFileRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private StoredFileRepository storedFileRepository;
    @Mock
    private HierarchicalFileStorageService fileStorageService;
    @Mock
    private UploadPathService uploadPathService;
    @Mock
    private WorkspaceAccessService workspaceAccessService;
    @Mock
    private WorkspaceQuotaGuardService workspaceQuotaGuardService;

    @InjectMocks
    private ActionOutputService outputService;

    private ActionOutput datasetOutput() {
        ActionOutput output = output("workspace-1", 10, null);
        output.setProject(null);
        var dataset = new de.uniwue.zpd.dachs.larex.backend.entity.Dataset();
        ReflectionTestUtils.setField(dataset, "id", "dataset-1");
        dataset.setName("Dataset");
        output.setDataset(dataset);
        return output;
    }

    @Test
    void datasetDownloadsEnforceWorkspaceDatasetFileAndExpiry() throws Exception {
        var output = datasetOutput();
        when(outputRepository.findById("output-1")).thenReturn(Optional.of(output));
        assertThrows(de.uniwue.zpd.dachs.larex.backend.exception.ResourceNotFoundException.class,
                () -> outputService.prepareDatasetFileDownload("other", "dataset-1", "output-1", "file", "user"));
        assertThrows(de.uniwue.zpd.dachs.larex.backend.exception.ResourceNotFoundException.class,
                () -> outputService.prepareDatasetFileDownload("workspace-1", "other", "output-1", "file", "user"));
        assertThrows(de.uniwue.zpd.dachs.larex.backend.exception.ResourceNotFoundException.class,
                () -> outputService.prepareDatasetFileDownload("workspace-1", "dataset-1", "output-1", "missing", "user"));
        output.setExpiresAt(LocalDateTime.now().minusDays(1));
        assertThrows(de.uniwue.zpd.dachs.larex.backend.exception.ResourceNotFoundException.class,
                () -> outputService.prepareDatasetFileDownload("workspace-1", "dataset-1", "output-1", "file", "user"));
        verifyNoInteractions(fileStorageService);
    }

    @Test
    void datasetDownloadReturnsActualContentAndOriginalMetadata(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var output = datasetOutput();
        var path = directory.resolve("result.bin");
        java.nio.file.Files.write(path, new byte[]{0, 1, 2});
        var storedFile = new de.uniwue.zpd.dachs.larex.backend.entity.StoredFile();
        storedFile.setStoragePath("ws/workspace-1/ds/dataset-1/out/result.bin");
        var file = new de.uniwue.zpd.dachs.larex.backend.entity.ActionOutputFile();
        ReflectionTestUtils.setField(file, "id", "file-1");
        file.setStoredFile(storedFile); file.setFileName("résultat.bin"); file.setMimeType("application/octet-stream"); file.setChecksumSha256("checksum");
        output.getFiles().add(file);
        when(outputRepository.findById("output-1")).thenReturn(Optional.of(output));
        when(uploadPathService.resolve(storedFile.getStoragePath())).thenReturn(path);
        var download = outputService.prepareDatasetFileDownload("workspace-1", "dataset-1", "output-1", "file-1", "user");
        assertEquals("résultat.bin", download.fileName());
        assertEquals(3, download.contentLength());
        org.assertj.core.api.Assertions.assertThat(java.nio.file.Files.readAllBytes(download.absolutePath())).containsExactly(0, 1, 2);
        verify(workspaceAccessService).requireWorkspaceAccess("workspace-1", "user");
    }

    @Test
    void datasetDeletionUsesManagedFileCleanup() {
        var output = datasetOutput();
        when(outputRepository.findByDatasetId("dataset-1")).thenReturn(List.of(output));
        outputService.deleteDatasetOutputs("dataset-1");
        verify(outputRepository).delete(output);
        verify(fileStorageService).deleteStoredFiles(List.of());
    }

    @Test
    void datasetOutputsAppearInAdminListWithoutProject() {
        var output = datasetOutput();
        when(outputRepository.findByStatus(eq(ActionOutput.Status.READY), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(output)));
        var response = outputService.listAdminOutputs(1, 25, "created", "desc").outputs().getFirst();
        assertEquals("dataset-1", response.datasetId());
        assertEquals("Dataset", response.datasetName());
        org.assertj.core.api.Assertions.assertThat(response.projectId()).isNull();
    }

    @Test
    void datasetRetentionIsSnapshottedAndExpiresFromCompletion() {
        var dataset = new de.uniwue.zpd.dachs.larex.backend.entity.Dataset();
        ReflectionTestUtils.setField(dataset, "id", "dataset-1");
        dataset.setOutputRetentionDays(7);
        var run = new de.uniwue.zpd.dachs.larex.backend.entity.ActionRun();
        ReflectionTestUtils.setField(run, "id", "run-1");
        run.setKind(de.uniwue.zpd.dachs.larex.backend.entity.ActionRun.Kind.EVALUATION);
        run.setEvaluationOutputMode(de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto.EvaluationOutputMode.FILES);
        run.setWorkspaceId("workspace-1"); run.setCreatedByUserId("user");
        var definition = new de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition();
        definition.setId("definition"); definition.setName("Evaluation"); definition.setProcessorKey("evaluation");
        run.setProcessorDefinition(definition);
        outputService.createEvaluationDraft(run, dataset);
        var captor = ArgumentCaptor.forClass(ActionOutput.class);
        verify(outputRepository).saveAndFlush(captor.capture());
        var draft = captor.getValue();
        dataset.setOutputRetentionDays(30);
        draft.setFileCount(1);
        when(outputRepository.findBySourceRunId("run-1")).thenReturn(Optional.of(draft));
        var completedAt = LocalDateTime.of(2026, 10, 13, 12, 0);
        outputService.finalizeDraft("run-1", completedAt);
        assertEquals(7, draft.getRetentionDays());
        assertEquals(completedAt.plusDays(7), draft.getExpiresAt());
        assertEquals(ActionOutput.Status.READY, draft.getStatus());
    }

    @Test
    void listAdminOutputsUsesAllowListedSortAndPagination() {
        ActionOutput output = output("workspace-1", 2048, null);
        when(outputRepository.findByStatus(eq(ActionOutput.Status.READY), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(output), Pageable.ofSize(25), 1));

        var response = outputService.listAdminOutputs(2, 25, "totalSizeBytes", "asc");

        assertEquals(2, response.page());
        assertEquals(1, response.totalElements());
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(outputRepository).findByStatus(eq(ActionOutput.Status.READY), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertEquals(1, pageable.getPageNumber());
        assertEquals(Sort.Direction.ASC, pageable.getSort().getOrderFor("totalSizeBytes").getDirection());
    }

    @Test
    void listAdminOutputsRejectsInvalidSortWithoutQuerying() {
        assertThrows(IllegalArgumentException.class,
                () -> outputService.listAdminOutputs(1, 25, "updated", "desc"));

        verifyNoInteractions(outputRepository);
    }

    @Test
    void manualDeletionUsesExistingDeletionPathAndSyncsQuota() {
        ActionOutput output = output("workspace-1", 2048, null);
        when(outputRepository.findByIdAndStatus("output-1", ActionOutput.Status.READY))
                .thenReturn(Optional.of(output));

        StorageCleanupDto.CleanupResponse response = outputService.deleteAdminOutput("output-1");

        assertEquals(1, response.deletedCount());
        assertEquals(ActionOutput.Status.DELETING, output.getStatus());
        assertNotNull(output.getShareRevokedAt());
        verify(outputRepository).saveAndFlush(output);
        verify(outputRepository).delete(output);
        verify(workspaceQuotaGuardService).syncUsage("workspace-1");
    }

    @Test
    void selectedCleanupOnlyDeletesSelectedOutputs() {
        ActionOutput selected = output("workspace-1", 2048, null);
        ActionOutput deselected = output("workspace-1", 1024, null);
        ReflectionTestUtils.setField(selected, "id", "output-1");
        ReflectionTestUtils.setField(deselected, "id", "output-2");
        when(outputRepository.findByStatusAndCreatedBefore(eq(ActionOutput.Status.READY), any(LocalDateTime.class)))
                .thenReturn(List.of(selected, deselected));

        StorageCleanupDto.CleanupResponse response = outputService.deleteAdminOutputsOlderThan(30, List.of("output-1"));

        assertEquals(1, response.deletedCount());
        assertEquals(0, response.failedCount());
        assertEquals(ActionOutput.Status.DELETING, selected.getStatus());
        assertEquals(ActionOutput.Status.READY, deselected.getStatus());
        verify(outputRepository).delete(selected);
        verify(outputRepository, never()).delete(deselected);
        verify(workspaceQuotaGuardService).syncUsage("workspace-1");
    }

    @Test
    void cleanupUsesCreatedCutoffAndIncludesIndefinitelyRetainedOutputs() {
        LocalDateTime before = LocalDateTime.now();
        ActionOutput output = output("workspace-1", 2048, null);
        ActionOutput secondOutput = output("workspace-1", 1024, null);
        when(outputRepository.findByStatusAndCreatedBefore(eq(ActionOutput.Status.READY), any(LocalDateTime.class)))
                .thenReturn(List.of(output, secondOutput));

        StorageCleanupDto.CleanupResponse response = outputService.deleteAdminOutputsOlderThan(30);

        assertEquals(2, response.deletedCount());
        assertEquals(3072, response.freedBytes());
        assertEquals(ActionOutput.Status.DELETING, output.getStatus());
        assertNotNull(output.getShareRevokedAt());
        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(outputRepository).findByStatusAndCreatedBefore(eq(ActionOutput.Status.READY), cutoffCaptor.capture());
        LocalDateTime cutoff = cutoffCaptor.getValue();
        assertFalse(cutoff.isBefore(before.minusDays(30)));
        assertFalse(cutoff.isAfter(LocalDateTime.now().minusDays(30)));
        verify(workspaceQuotaGuardService).syncUsage("workspace-1");
    }

    @Test
    void cleanupRejectsNonPositiveAge() {
        assertThrows(IllegalArgumentException.class, () -> outputService.deleteAdminOutputsOlderThan(0));
        verifyNoInteractions(outputRepository);
    }

    private ActionOutput output(String workspaceId, long size, Integer retentionDays) {
        ActionOutput output = new ActionOutput();
        output.setWorkspaceId(workspaceId);
        output.setTotalSizeBytes(size);
        output.setRetentionDays(retentionDays);
        output.setStatus(ActionOutput.Status.READY);
        Project project = new Project();
        project.setId("project-1");
        project.setName("Project");
        output.setProject(project);
        return output;
    }
}

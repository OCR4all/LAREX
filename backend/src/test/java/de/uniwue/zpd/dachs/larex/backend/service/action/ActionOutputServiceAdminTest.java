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

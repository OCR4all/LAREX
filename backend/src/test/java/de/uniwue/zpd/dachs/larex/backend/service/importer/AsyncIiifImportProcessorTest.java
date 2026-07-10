package de.uniwue.zpd.dachs.larex.backend.service.importer;

import de.uniwue.zpd.dachs.larex.backend.config.IiifProperties;
import de.uniwue.zpd.dachs.larex.backend.entity.IiifImportJob;
import de.uniwue.zpd.dachs.larex.backend.entity.IiifImportJobItem;
import de.uniwue.zpd.dachs.larex.backend.repository.importing.IiifImportJobItemRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.importing.IiifImportJobRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageImageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.service.storage.HierarchicalFileStorageService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.ThumbnailService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaGuardService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AsyncIiifImportProcessorTest {

    @Test
    void downloadsOutsideDatabaseTransaction() throws Exception {
        AtomicBoolean transactionActive = new AtomicBoolean();
        PlatformTransactionManager transactionManager = trackingTransactionManager(transactionActive);
        IiifImportJobRepository jobRepository = mock(IiifImportJobRepository.class);
        IiifImportJobItemRepository itemRepository = mock(IiifImportJobItemRepository.class);
        IiifImageDownloader imageDownloader = mock(IiifImageDownloader.class);
        ObjectMapper objectMapper = new ObjectMapper();
        IiifImportJob job = importJob(objectMapper);

        when(jobRepository.claimPendingJob(anyString(), anyString(), any(), any())).thenAnswer(invocation -> {
            job.setStatus(IiifImportJob.Status.IMPORTING);
            job.setLeaseOwner(invocation.getArgument(1));
            return 1;
        });
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(jobRepository.save(any(IiifImportJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(imageDownloader.download(anyString(), anyString(), anyLong())).thenAnswer(invocation -> {
            assertThat(transactionActive)
                    .as("image downloads must not hold a database transaction")
                    .isFalse();
            throw new IOException("remote server unavailable");
        });

        AsyncIiifImportProcessor processor = new AsyncIiifImportProcessor(
                jobRepository,
                itemRepository,
                mock(ProjectRepository.class),
                mock(PageRepository.class),
                mock(PageImageRepository.class),
                mock(HierarchicalFileStorageService.class),
                mock(ThumbnailService.class),
                mock(WorkspaceQuotaGuardService.class),
                objectMapper,
                imageDownloader,
                new IiifProperties(),
                transactionManager,
                Clock.fixed(Instant.parse("2026-07-09T10:00:00Z"), ZoneOffset.UTC)
        );

        processor.processImportJob(job.getId(), "worker-1");

        verify(imageDownloader).download(
                "https://iiif.example/image/full/full/0/default.jpg",
                "Page 1",
                536_870_912L
        );
        ArgumentCaptor<IiifImportJobItem> itemCaptor = ArgumentCaptor.forClass(IiifImportJobItem.class);
        verify(itemRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(job.getResultsJson()).isEqualTo("[]");
        assertThat(job.getStatus()).isEqualTo(IiifImportJob.Status.COMPLETED);
        assertThat(job.getFailedCanvases()).isEqualTo(1);
        assertThat(job.getLeaseOwner()).isNull();
    }

    @Test
    void workerThatLosesItsLeaseCannotCommitDownloadedCanvas() throws Exception {
        AtomicBoolean transactionActive = new AtomicBoolean();
        PlatformTransactionManager transactionManager = trackingTransactionManager(transactionActive);
        IiifImportJobRepository jobRepository = mock(IiifImportJobRepository.class);
        IiifImportJobItemRepository itemRepository = mock(IiifImportJobItemRepository.class);
        IiifImageDownloader imageDownloader = mock(IiifImageDownloader.class);
        ObjectMapper objectMapper = new ObjectMapper();
        IiifImportJob job = importJob(objectMapper);
        Path downloadedFile = Files.createTempFile("iiif-lease-test-", ".jpg");

        when(jobRepository.claimPendingJob(anyString(), anyString(), any(), any())).thenAnswer(invocation -> {
            job.setStatus(IiifImportJob.Status.IMPORTING);
            job.setLeaseOwner(invocation.getArgument(1));
            return 1;
        });
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(jobRepository.save(any(IiifImportJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(imageDownloader.download(anyString(), anyString(), anyLong())).thenAnswer(invocation -> {
            job.setLeaseOwner("replacement-worker");
            return new IiifImageDownloader.DownloadedImage(
                    downloadedFile,
                    "image/jpeg",
                    "jpg",
                    Files.size(downloadedFile)
            );
        });
        HierarchicalFileStorageService storageService = mock(HierarchicalFileStorageService.class);

        AsyncIiifImportProcessor processor = new AsyncIiifImportProcessor(
                jobRepository,
                itemRepository,
                mock(ProjectRepository.class),
                mock(PageRepository.class),
                mock(PageImageRepository.class),
                storageService,
                mock(ThumbnailService.class),
                mock(WorkspaceQuotaGuardService.class),
                objectMapper,
                imageDownloader,
                new IiifProperties(),
                transactionManager,
                Clock.fixed(Instant.parse("2026-07-09T10:00:00Z"), ZoneOffset.UTC)
        );

        processor.processImportJob(job.getId(), "worker-1");

        assertThat(job.getStatus()).isEqualTo(IiifImportJob.Status.IMPORTING);
        assertThat(job.getLeaseOwner()).isEqualTo("replacement-worker");
        assertThat(job.getProcessedCanvases()).isZero();
        assertThat(downloadedFile).doesNotExist();
        verify(storageService, org.mockito.Mockito.never()).storeFromPath(
                any(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(),
                anyString(),
                org.mockito.ArgumentMatchers.anyBoolean()
        );
    }

    @Test
    void extendsJobReservationBeforePersistingActualBytesBeyondEstimate() throws Exception {
        AtomicBoolean transactionActive = new AtomicBoolean();
        PlatformTransactionManager transactionManager = trackingTransactionManager(transactionActive);
        IiifImportJobRepository jobRepository = mock(IiifImportJobRepository.class);
        IiifImportJobItemRepository itemRepository = mock(IiifImportJobItemRepository.class);
        IiifImageDownloader imageDownloader = mock(IiifImageDownloader.class);
        WorkspaceQuotaGuardService quotaGuard = mock(WorkspaceQuotaGuardService.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        IiifImportJob job = importJob(objectMapper);
        job.setReservedBytes(1_000L);
        Path downloadedFile = Files.createTempFile("iiif-quota-test-", ".jpg");
        Files.write(downloadedFile, new byte[1_200]);

        when(jobRepository.claimPendingJob(anyString(), anyString(), any(), any())).thenAnswer(invocation -> {
            job.setStatus(IiifImportJob.Status.IMPORTING);
            job.setLeaseOwner(invocation.getArgument(1));
            return 1;
        });
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(jobRepository.save(any(IiifImportJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(quotaGuard.isQuotaEnforcementEnabled()).thenReturn(true);
        when(quotaGuard.getAvailableBytes(job.getWorkspaceId())).thenReturn(1_000L);
        when(quotaGuard.reserveBytesOrThrow(job.getWorkspaceId(), 200L, "iiif-import-download"))
                .thenReturn(200L);
        when(imageDownloader.download(anyString(), anyString(), anyLong()))
                .thenReturn(new IiifImageDownloader.DownloadedImage(
                        downloadedFile,
                        "image/jpeg",
                        "jpg",
                        1_200L
                ));
        IiifProperties properties = new IiifProperties();
        properties.setMaxImageDownloadBytes(5_000L);

        AsyncIiifImportProcessor processor = new AsyncIiifImportProcessor(
                jobRepository,
                itemRepository,
                projectRepository,
                mock(PageRepository.class),
                mock(PageImageRepository.class),
                mock(HierarchicalFileStorageService.class),
                mock(ThumbnailService.class),
                quotaGuard,
                objectMapper,
                imageDownloader,
                properties,
                transactionManager,
                Clock.fixed(Instant.parse("2026-07-09T10:00:00Z"), ZoneOffset.UTC)
        );

        processor.processImportJob(job.getId(), "worker-1");

        verify(imageDownloader).download(
                "https://iiif.example/image/full/full/0/default.jpg",
                "Page 1",
                2_000L
        );
        verify(quotaGuard).reserveBytesOrThrow(
                job.getWorkspaceId(),
                200L,
                "iiif-import-download"
        );
        assertThat(downloadedFile).doesNotExist();
    }

    private PlatformTransactionManager trackingTransactionManager(AtomicBoolean transactionActive) {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus transactionStatus = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenAnswer(invocation -> {
            assertThat(transactionActive.compareAndSet(false, true))
                    .as("test processor transactions must not overlap")
                    .isTrue();
            return transactionStatus;
        });
        doAnswer(invocation -> {
            transactionActive.set(false);
            return null;
        }).when(transactionManager).commit(transactionStatus);
        doAnswer(invocation -> {
            transactionActive.set(false);
            return null;
        }).when(transactionManager).rollback(transactionStatus);
        return transactionManager;
    }

    private IiifImportJob importJob(ObjectMapper objectMapper) throws Exception {
        IiifJobCanvasPayload payload = new IiifJobCanvasPayload(
                "canvas-1",
                "Canvas 1",
                0,
                0,
                "Page 1",
                "Page 1",
                null,
                "CREATE",
                "https://iiif.example/image/full/full/0/default.jpg",
                1_000L,
                null,
                "https://iiif.example/manifest",
                "{}",
                null,
                null,
                null,
                null
        );
        IiifImportJob job = new IiifImportJob();
        job.setId("job-1");
        job.setProjectId("project-1");
        job.setWorkspaceId("workspace-1");
        job.setCreatedByUserId("user-1");
        job.setStatus(IiifImportJob.Status.PENDING);
        job.setTotalCanvases(1);
        job.setCanvasPayloadJson(objectMapper.writeValueAsString(List.of(payload)));
        job.setResultsJson("[]");
        return job;
    }
}

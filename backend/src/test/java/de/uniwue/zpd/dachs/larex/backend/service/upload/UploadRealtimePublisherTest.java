package de.uniwue.zpd.dachs.larex.backend.service.upload;

import de.uniwue.zpd.dachs.larex.backend.config.UploadProperties;
import de.uniwue.zpd.dachs.larex.backend.entity.UploadSession;
import de.uniwue.zpd.dachs.larex.backend.entity.UploadSessionFile;
import de.uniwue.zpd.dachs.larex.backend.repository.upload.UploadSessionRepository;
import de.uniwue.zpd.dachs.larex.backend.service.notification.NotificationBridgeClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UploadRealtimePublisherTest {

    @Mock
    private UploadSessionRepository sessionRepository;

    @Mock
    private NotificationBridgeClient notificationBridgeClient;

    @Mock
    private TaskScheduler taskScheduler;

    private final AtomicReference<Runnable> scheduledTask = new AtomicReference<>();
    private UploadRealtimePublisher publisher;

    @BeforeEach
    void setUp() {
        when(taskScheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(invocation -> {
            scheduledTask.set(invocation.getArgument(0));
            return mock(ScheduledFuture.class);
        });
        publisher = new UploadRealtimePublisher(
                sessionRepository,
                notificationBridgeClient,
                taskScheduler,
                new UploadProperties()
        );
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void schedulesOnlyAfterTheSurroundingTransactionCommits() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        publisher.broadcastSessionState("session-1", "uploading");

        verifyNoInteractions(taskScheduler);
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        verify(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void coalescesLatestFileAndPageChangesIntoOneUserScopedEvent() {
        UploadSession session = new UploadSession("project-1", "workspace-1", "user-1", 2, 200);
        session.setId("session-1");
        session.setStatus(UploadSession.UploadSessionStatus.PROCESSING);
        session.setProcessedFiles(1);
        session.setProcessingCompletedItems(3);
        session.setProcessingTotalItems(6);
        when(sessionRepository.findById("session-1")).thenReturn(Optional.of(session));

        UploadSessionFile file = new UploadSessionFile("page.png", 100, "image/png", "page", "png", 2);
        file.setId("file-1");
        file.setStatus(UploadSessionFile.UploadFileStatus.UPLOADING);
        file.setChunksReceived(1);
        publisher.broadcastFileState("session-1", file);

        file.setStatus(UploadSessionFile.UploadFileStatus.COMPLETED);
        file.setChunksReceived(2);
        publisher.broadcastFileState("session-1", file);
        publisher.broadcastPageCreatedOrUpdated("session-1", "project-1", "page-1", "Page 1", "image");
        publisher.broadcastPageCreatedOrUpdated("session-1", "project-1", "page-1", "Page 1", "xml");
        publisher.broadcastSessionState("session-1", "processing");

        verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));
        scheduledTask.get().run();

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(notificationBridgeClient).pushUploadEvent(eq("user-1"), payloadCaptor.capture(), eq("upload-service"));
        UploadRealtimePublisher.UploadRealtimePayload payload
                = (UploadRealtimePublisher.UploadRealtimePayload) payloadCaptor.getValue();

        assertThat(payload.sessionId()).isEqualTo("session-1");
        assertThat(payload.workspaceId()).isEqualTo("workspace-1");
        assertThat(payload.projectId()).isEqualTo("project-1");
        assertThat(payload.sequence()).isPositive();
        assertThat(payload.streamId()).isNotBlank();
        assertThat(payload.session().message()).isEqualTo("processing");
        assertThat(payload.session().processingProgressPercent()).isEqualTo(50);
        assertThat(payload.files()).singleElement().satisfies(filePayload -> {
            assertThat(filePayload.fileId()).isEqualTo("file-1");
            assertThat(filePayload.status()).isEqualTo("COMPLETED");
            assertThat(filePayload.chunksReceived()).isEqualTo(2);
        });
        assertThat(payload.pages()).singleElement().satisfies(pagePayload -> {
            assertThat(pagePayload.pageId()).isEqualTo("page-1");
            assertThat(pagePayload.reason()).isEqualTo("xml");
        });
    }
}

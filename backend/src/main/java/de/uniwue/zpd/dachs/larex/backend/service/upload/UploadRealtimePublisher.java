package de.uniwue.zpd.dachs.larex.backend.service.upload;

import de.uniwue.zpd.dachs.larex.backend.config.UploadProperties;
import de.uniwue.zpd.dachs.larex.backend.entity.UploadSession;
import de.uniwue.zpd.dachs.larex.backend.entity.UploadSession.UploadSessionStatus;
import de.uniwue.zpd.dachs.larex.backend.entity.UploadSessionFile;
import de.uniwue.zpd.dachs.larex.backend.repository.upload.UploadSessionRepository;
import de.uniwue.zpd.dachs.larex.backend.service.notification.NotificationBridgeClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Coalesces high-frequency upload changes into user-scoped messages on LAREX's
 * existing authenticated WebSocket connection.
 */
@Service
public class UploadRealtimePublisher {

    private static final Logger log = LoggerFactory.getLogger(UploadRealtimePublisher.class);

    private final UploadSessionRepository sessionRepository;
    private final NotificationBridgeClient notificationBridgeClient;
    private final TaskScheduler taskScheduler;
    private final long coalesceMs;
    private final String streamId = UUID.randomUUID().toString();
    private final AtomicLong sequence = new AtomicLong();
    private final Map<String, PendingUploadEvent> pendingBySessionId = new ConcurrentHashMap<>();

    public UploadRealtimePublisher(UploadSessionRepository sessionRepository,
                                   NotificationBridgeClient notificationBridgeClient,
                                   @Qualifier("uploadRealtimeTaskScheduler") TaskScheduler taskScheduler,
                                   UploadProperties uploadProperties) {
        this.sessionRepository = sessionRepository;
        this.notificationBridgeClient = notificationBridgeClient;
        this.taskScheduler = taskScheduler;
        this.coalesceMs = uploadProperties.getRealtime().getCoalesceMs();
    }

    public void broadcastSessionState(String sessionId, String message) {
        queue(sessionId, pending -> pending.message = message);
    }

    public void broadcastFileState(String sessionId, UploadSessionFile file) {
        if (file == null || file.getId() == null) {
            return;
        }
        FileStatePayload payload = new FileStatePayload(
                sessionId,
                file.getId(),
                file.getOriginalFileName(),
                file.getStatus() != null ? file.getStatus().name() : null,
                file.getChunksReceived(),
                file.getChunkCount(),
                file.getCreatedPageId(),
                file.getCreatedPageImageId(),
                file.getErrorMessage(),
                file.getConflictType()
        );
        queue(sessionId, pending -> pending.files.put(file.getId(), payload));
    }

    public void broadcastPageCreatedOrUpdated(String sessionId,
                                              String projectId,
                                              String pageId,
                                              String pageName,
                                              String reason) {
        if (pageId == null || pageId.isBlank()) {
            return;
        }
        PageCreatedOrUpdatedPayload payload = new PageCreatedOrUpdatedPayload(projectId, pageId, pageName, reason);
        queue(sessionId, pending -> pending.pages.put(pageId, payload));
    }

    private void queue(String sessionId, PendingMutation mutation) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    queueAfterCommit(sessionId, mutation);
                }
            });
            return;
        }

        queueAfterCommit(sessionId, mutation);
    }

    private void queueAfterCommit(String sessionId, PendingMutation mutation) {
        PendingUploadEvent pending = pendingBySessionId.computeIfAbsent(sessionId, ignored -> new PendingUploadEvent());
        synchronized (pending) {
            mutation.apply(pending);
            if (pending.scheduled == null && !pending.flushing) {
                scheduleFlush(sessionId, pending);
            }
        }
    }

    private void scheduleFlush(String sessionId, PendingUploadEvent pending) {
        try {
            pending.scheduled = taskScheduler.schedule(
                    () -> flush(sessionId, pending),
                    Instant.now().plusMillis(coalesceMs)
            );
        } catch (RuntimeException error) {
            pending.scheduled = null;
            pendingBySessionId.remove(sessionId, pending);
            log.warn("Failed to schedule realtime upload update for session {}: {}", sessionId, error.getMessage());
        }
    }

    private void flush(String sessionId, PendingUploadEvent pending) {
        PendingSnapshot snapshot;
        synchronized (pending) {
            pending.scheduled = null;
            pending.flushing = true;
            snapshot = pending.snapshotAndClear();
        }

        try {
            sessionRepository.findById(sessionId).ifPresent(session -> {
                UploadRealtimePayload payload = new UploadRealtimePayload(
                        streamId,
                        sequence.incrementAndGet(),
                        session.getId(),
                        session.getWorkspaceId(),
                        session.getProjectId(),
                        buildSessionStatePayload(session, snapshot.message()),
                        snapshot.files(),
                        snapshot.pages()
                );
                notificationBridgeClient.pushUploadEvent(session.getUserId(), payload, "upload-service");
            });
        } catch (RuntimeException error) {
            // REST rehydration remains authoritative if a realtime delivery fails.
            log.warn("Failed to publish realtime upload update for session {}: {}", sessionId, error.getMessage());
        } finally {
            synchronized (pending) {
                pending.flushing = false;
                if (pending.hasChanges()) {
                    scheduleFlush(sessionId, pending);
                } else {
                    pendingBySessionId.remove(sessionId, pending);
                }
            }
        }
    }

    private SessionStatePayload buildSessionStatePayload(UploadSession session, String message) {
        UploadSessionStatus status = session.getStatus();
        boolean uploadingActive = status == UploadSessionStatus.PENDING || status == UploadSessionStatus.UPLOADING;
        boolean processingActive = status == UploadSessionStatus.PROCESSING;
        boolean finalized = status == UploadSessionStatus.PROCESSING
                || status == UploadSessionStatus.COMPLETED
                || status == UploadSessionStatus.FAILED
                || status == UploadSessionStatus.CANCELLED;

        return new SessionStatePayload(
                session.getId(),
                status != null ? status.name() : null,
                session.getProcessedFiles(),
                session.getFailedFiles(),
                session.getTotalFiles(),
                session.getProcessedBytes(),
                session.getTotalBytes(),
                session.getProcessingCompletedItems(),
                session.getProcessingTotalItems(),
                session.getProcessingProgressPercent(),
                session.getProcessingCurrentFileName(),
                message,
                uploadingActive,
                processingActive,
                finalized
        );
    }

    @FunctionalInterface
    private interface PendingMutation {
        void apply(PendingUploadEvent pending);
    }

    private static final class PendingUploadEvent {
        private String message;
        private final Map<String, FileStatePayload> files = new LinkedHashMap<>();
        private final Map<String, PageCreatedOrUpdatedPayload> pages = new LinkedHashMap<>();
        private ScheduledFuture<?> scheduled;
        private boolean flushing;

        private PendingSnapshot snapshotAndClear() {
            PendingSnapshot snapshot = new PendingSnapshot(
                    message,
                    new ArrayList<>(files.values()),
                    new ArrayList<>(pages.values())
            );
            message = null;
            files.clear();
            pages.clear();
            return snapshot;
        }

        private boolean hasChanges() {
            return message != null || !files.isEmpty() || !pages.isEmpty();
        }
    }

    private record PendingSnapshot(
            String message,
            List<FileStatePayload> files,
            List<PageCreatedOrUpdatedPayload> pages
    ) {}

    public record UploadRealtimePayload(
            String streamId,
            long sequence,
            String sessionId,
            String workspaceId,
            String projectId,
            SessionStatePayload session,
            List<FileStatePayload> files,
            List<PageCreatedOrUpdatedPayload> pages
    ) {}

    public record SessionStatePayload(
            String sessionId,
            String status,
            int processedFiles,
            int failedFiles,
            int totalFiles,
            long processedBytes,
            long totalBytes,
            int processingCompletedItems,
            int processingTotalItems,
            int processingProgressPercent,
            String processingCurrentFileName,
            String message,
            boolean uploadingActive,
            boolean processingActive,
            boolean finalized
    ) {}

    public record FileStatePayload(
            String sessionId,
            String fileId,
            String fileName,
            String status,
            int chunksReceived,
            int chunkCount,
            String createdPageId,
            String createdPageImageId,
            String errorMessage,
            String conflictType
    ) {}

    public record PageCreatedOrUpdatedPayload(
            String projectId,
            String pageId,
            String pageName,
            String reason
    ) {}
}

package de.uniwue.zpd.dachs.larex.backend.service;

import de.uniwue.zpd.dachs.larex.backend.entity.UploadSession;
import de.uniwue.zpd.dachs.larex.backend.entity.UploadSession.UploadSessionStatus;
import de.uniwue.zpd.dachs.larex.backend.entity.UploadSessionFile;
import de.uniwue.zpd.dachs.larex.backend.entity.UploadSessionFile.UploadFileStatus;
import de.uniwue.zpd.dachs.larex.backend.repository.UploadSessionFileRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.UploadSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class UploadSessionEventBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(UploadSessionEventBroadcaster.class);
    private static final long SSE_TIMEOUT_MS = 0L; // no timeout; heartbeat keeps connection active

    private final UploadSessionRepository sessionRepository;
    private final UploadSessionFileRepository fileRepository;

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emittersBySessionId = new ConcurrentHashMap<>();

    public UploadSessionEventBroadcaster(UploadSessionRepository sessionRepository,
                                         UploadSessionFileRepository fileRepository) {
        this.sessionRepository = sessionRepository;
        this.fileRepository = fileRepository;
    }

    public SseEmitter subscribe(String sessionId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emittersBySessionId.computeIfAbsent(sessionId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(sessionId, emitter));
        emitter.onTimeout(() -> {
            removeEmitter(sessionId, emitter);
            emitter.complete();
        });
        emitter.onError(err -> removeEmitter(sessionId, emitter));

        sendToEmitter(sessionId, emitter, "heartbeat", new HeartbeatPayload(Instant.now().toString()));
        sendSessionStateToEmitter(sessionId, emitter, "subscribed");
        return emitter;
    }

    public void broadcastSessionState(String sessionId, String message) {
        Optional<UploadSession> sessionOpt = sessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty()) {
            return;
        }
        SessionStatePayload payload = buildSessionStatePayload(sessionOpt.get(), message);
        broadcast(sessionId, "upload-session-state", payload);
    }

    public void broadcastFileState(String sessionId, UploadSessionFile file) {
        if (sessionId == null || sessionId.isBlank() || file == null) {
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
        broadcast(sessionId, "upload-file-state", payload);
    }

    public void broadcastPageCreatedOrUpdated(String sessionId, String projectId, String pageId, String pageName, String reason) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        broadcast(sessionId, "page-created-or-updated", new PageCreatedOrUpdatedPayload(projectId, pageId, pageName, reason));
    }

    public void broadcastPageIndexState(String sessionId, String projectId, String pageId, String status) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        broadcast(sessionId, "page-index-state", new PageIndexStatePayload(projectId, pageId, status));
    }

    @Scheduled(fixedDelayString = "${larex.upload.sse-heartbeat-ms:20000}")
    public void sendHeartbeats() {
        HeartbeatPayload payload = new HeartbeatPayload(Instant.now().toString());
        for (Map.Entry<String, CopyOnWriteArrayList<SseEmitter>> entry : emittersBySessionId.entrySet()) {
            String sessionId = entry.getKey();
            for (SseEmitter emitter : entry.getValue()) {
                sendToEmitter(sessionId, emitter, "heartbeat", payload);
            }
        }
    }

    private void sendSessionStateToEmitter(String sessionId, SseEmitter emitter, String message) {
        Optional<UploadSession> sessionOpt = sessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty()) {
            return;
        }
        sendToEmitter(sessionId, emitter, "upload-session-state", buildSessionStatePayload(sessionOpt.get(), message));
    }

    private SessionStatePayload buildSessionStatePayload(UploadSession session, String message) {
        List<UploadSessionFile> stillUploading = fileRepository.findBySessionIdAndStatusIn(
                session.getId(),
                List.of(UploadFileStatus.PENDING, UploadFileStatus.UPLOADING)
        );
        boolean uploadingActive = !stillUploading.isEmpty()
                || session.getStatus() == UploadSessionStatus.PENDING
                || session.getStatus() == UploadSessionStatus.UPLOADING;
        boolean finalized = session.getStatus() == UploadSessionStatus.PROCESSING
                || session.getStatus() == UploadSessionStatus.COMPLETED
                || session.getStatus() == UploadSessionStatus.FAILED;
        boolean processingActive = session.getStatus() == UploadSessionStatus.PROCESSING;

        return new SessionStatePayload(
                session.getId(),
                session.getStatus() != null ? session.getStatus().name() : null,
                session.getProcessedFiles(),
                session.getFailedFiles(),
                session.getTotalFiles(),
                session.getProcessedBytes(),
                session.getTotalBytes(),
                message,
                uploadingActive,
                processingActive,
                finalized
        );
    }

    private void broadcast(String sessionId, String eventName, Object payload) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersBySessionId.get(sessionId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : new ArrayList<>(emitters)) {
            sendToEmitter(sessionId, emitter, eventName, payload);
        }
    }

    private void sendToEmitter(String sessionId, SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
        } catch (IOException | IllegalStateException e) {
            log.debug("Removing broken upload SSE emitter for session {}: {}", sessionId, e.getMessage());
            removeEmitter(sessionId, emitter);
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // no-op
            }
        }
    }

    private void removeEmitter(String sessionId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersBySessionId.get(sessionId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersBySessionId.remove(sessionId, emitters);
        }
    }

    public record SessionStatePayload(
            String sessionId,
            String status,
            int processedFiles,
            int failedFiles,
            int totalFiles,
            long processedBytes,
            long totalBytes,
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

    public record PageIndexStatePayload(
            String projectId,
            String pageId,
            String status
    ) {}

    public record HeartbeatPayload(String timestamp) {}
}

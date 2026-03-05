package de.uniwue.zpd.dachs.larex.backend.service.upload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UploadSessionProcessingCoordinator {

    private static final Logger log = LoggerFactory.getLogger(UploadSessionProcessingCoordinator.class);

    private final AsyncUploadProcessor asyncUploadProcessor;
    private final TaskExecutor uploadTaskExecutor;
    private final Set<String> runningSessionIds = ConcurrentHashMap.newKeySet();
    private final Set<String> rerunRequestedSessionIds = ConcurrentHashMap.newKeySet();

    public UploadSessionProcessingCoordinator(AsyncUploadProcessor asyncUploadProcessor,
                                             @Qualifier("uploadTaskExecutor") TaskExecutor uploadTaskExecutor) {
        this.asyncUploadProcessor = asyncUploadProcessor;
        this.uploadTaskExecutor = uploadTaskExecutor;
    }

    public void requestProcessing(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        if (!runningSessionIds.add(sessionId)) {
            rerunRequestedSessionIds.add(sessionId);
            return;
        }

        try {
            uploadTaskExecutor.execute(() -> runLoop(sessionId));
        } catch (Exception e) {
            runningSessionIds.remove(sessionId);
            log.warn("Failed to schedule upload processing for session {}: {}", sessionId, e.getMessage(), e);
        }
    }

    public boolean isSessionRunning(String sessionId) {
        return sessionId != null && runningSessionIds.contains(sessionId);
    }

    private void runLoop(String sessionId) {
        try {
            while (true) {
                rerunRequestedSessionIds.remove(sessionId);
                asyncUploadProcessor.processUploadSessionWork(sessionId);

                if (!rerunRequestedSessionIds.remove(sessionId)) {
                    break;
                }
            }
        } catch (Exception e) {
            log.error("Unexpected error in upload session processing coordinator loop for {}", sessionId, e);
        } finally {
            runningSessionIds.remove(sessionId);

            // Close race where a rerun is requested after the last loop check but before running flag is cleared.
            if (rerunRequestedSessionIds.remove(sessionId)) {
                requestProcessing(sessionId);
            }
        }
    }
}

package de.uniwue.zpd.dachs.larex.backend.service.upload.indexing;

import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageIndexStatusTracker;
import de.uniwue.zpd.dachs.larex.backend.service.upload.UploadSessionEventBroadcaster;
import de.uniwue.zpd.dachs.larex.backend.service.upload.events.UploadPageIndexingRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;


@Component
public class UploadPageIndexDispatcher {

    private static final Logger log = LoggerFactory.getLogger(UploadPageIndexDispatcher.class);

    private final PageIndexStatusTracker pageIndexStatusTracker;
    private final UploadPageIndexWorker uploadPageIndexWorker;
    private final UploadSessionEventBroadcaster uploadSessionEventBroadcaster;
    private final long staleThresholdMs;

    public UploadPageIndexDispatcher(PageIndexStatusTracker pageIndexStatusTracker,
                                     UploadPageIndexWorker uploadPageIndexWorker,
                                     UploadSessionEventBroadcaster uploadSessionEventBroadcaster,
                                     @Value("${larex.upload.indexing.stale-threshold-ms:60000}") long staleThresholdMs) {
        this.pageIndexStatusTracker = pageIndexStatusTracker;
        this.uploadPageIndexWorker = uploadPageIndexWorker;
        this.uploadSessionEventBroadcaster = uploadSessionEventBroadcaster;
        this.staleThresholdMs = staleThresholdMs;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUploadPageIndexingRequested(UploadPageIndexingRequestedEvent event) {
        if (event == null || event.pageIds() == null || event.pageIds().isEmpty()) {
            return;
        }

        for (String pageId : event.pageIds()) {
            PageIndexStatusTracker.AcquireResult acquireResult = pageIndexStatusTracker.acquireIndexingSlot(pageId, staleThresholdMs);
            if (acquireResult == PageIndexStatusTracker.AcquireResult.INVALID_PAGE_ID) {
                continue;
            }
            if (acquireResult == PageIndexStatusTracker.AcquireResult.ALREADY_ACTIVE) {
                log.debug("Skipping duplicate indexing dispatch for page {} in project {}", pageId, event.projectId());
                continue;
            }
            if (acquireResult == PageIndexStatusTracker.AcquireResult.ACQUIRED_STALE_RECOVERY) {
                log.warn("Recovered stale indexing lock for page {} in project {} (threshold {} ms)",
                        pageId, event.projectId(), staleThresholdMs);
            }

            uploadSessionEventBroadcaster.broadcastPageIndexState(event.sessionId(), event.projectId(), pageId, "queued");
            try {
                uploadPageIndexWorker.indexPageAsync(event.sessionId(), event.projectId(), pageId);
            } catch (Exception e) {
                pageIndexStatusTracker.clearIndexing(pageId);
                uploadSessionEventBroadcaster.broadcastPageIndexState(event.sessionId(), event.projectId(), pageId, "failed");
                log.warn("Failed to schedule background indexing for page {} in project {}: {}",
                        pageId, event.projectId(), e.getMessage(), e);
            }
        }
    }
}

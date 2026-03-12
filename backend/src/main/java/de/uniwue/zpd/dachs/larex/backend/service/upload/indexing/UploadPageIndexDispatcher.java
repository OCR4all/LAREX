package de.uniwue.zpd.dachs.larex.backend.service.upload.indexing;

import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageIndexStatusTracker;
import de.uniwue.zpd.dachs.larex.backend.service.upload.UploadSessionEventBroadcaster;
import de.uniwue.zpd.dachs.larex.backend.service.upload.events.UploadPageIndexingRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;


@Component
public class UploadPageIndexDispatcher {

    private static final Logger log = LoggerFactory.getLogger(UploadPageIndexDispatcher.class);

    private final PageIndexStatusTracker pageIndexStatusTracker;
    private final UploadPageIndexWorker uploadPageIndexWorker;
    private final UploadSessionEventBroadcaster uploadSessionEventBroadcaster;

    public UploadPageIndexDispatcher(PageIndexStatusTracker pageIndexStatusTracker,
                                     UploadPageIndexWorker uploadPageIndexWorker,
                                     UploadSessionEventBroadcaster uploadSessionEventBroadcaster) {
        this.pageIndexStatusTracker = pageIndexStatusTracker;
        this.uploadPageIndexWorker = uploadPageIndexWorker;
        this.uploadSessionEventBroadcaster = uploadSessionEventBroadcaster;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUploadPageIndexingRequested(UploadPageIndexingRequestedEvent event) {
        if (event == null || event.pageIds() == null || event.pageIds().isEmpty()) {
            return;
        }

        for (String pageId : event.pageIds()) {
            boolean marked = pageIndexStatusTracker.markIndexingIfAbsent(pageId);
            if (marked) {
                uploadSessionEventBroadcaster.broadcastPageIndexState(event.sessionId(), event.projectId(), pageId, "queued");
            } else {
                // Recovery path: do not drop work just because a stale in-memory flag exists.
                log.debug("Page {} was already marked indexing; dispatching worker anyway to avoid stale lock", pageId);
            }
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

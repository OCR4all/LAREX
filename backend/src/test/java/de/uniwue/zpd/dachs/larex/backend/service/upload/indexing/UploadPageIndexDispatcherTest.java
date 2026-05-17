package de.uniwue.zpd.dachs.larex.backend.service.upload.indexing;

import de.uniwue.zpd.dachs.larex.backend.config.UploadProperties;
import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageIndexStatusTracker;
import de.uniwue.zpd.dachs.larex.backend.service.upload.UploadSessionEventBroadcaster;
import de.uniwue.zpd.dachs.larex.backend.service.upload.events.UploadPageIndexingRequestedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UploadPageIndexDispatcherTest {

    @Mock
    private UploadPageIndexWorker uploadPageIndexWorker;

    @Mock
    private UploadSessionEventBroadcaster uploadSessionEventBroadcaster;

    private PageIndexStatusTracker pageIndexStatusTracker;
    private UploadPageIndexDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        pageIndexStatusTracker = new PageIndexStatusTracker();
    }

    @Test
    void duplicateNonStaleRequestsAreDeduped() {
        dispatcher = new UploadPageIndexDispatcher(
                pageIndexStatusTracker,
                uploadPageIndexWorker,
                uploadSessionEventBroadcaster,
                uploadProperties(60_000L)
        );

        pageIndexStatusTracker.markIndexingIfAbsent("page-1");
        UploadPageIndexingRequestedEvent event = new UploadPageIndexingRequestedEvent("session-1", "project-1", Set.of("page-1"));

        dispatcher.onUploadPageIndexingRequested(event);

        verifyNoInteractions(uploadPageIndexWorker);
        verify(uploadSessionEventBroadcaster, never())
                .broadcastPageIndexState(anyString(), anyString(), anyString(), eq("queued"));
    }

    @Test
    void staleRequestsRecoverAndRedispatch() {
        dispatcher = new UploadPageIndexDispatcher(
                pageIndexStatusTracker,
                uploadPageIndexWorker,
                uploadSessionEventBroadcaster,
                uploadProperties(0L)
        );

        pageIndexStatusTracker.markIndexingIfAbsent("page-1");
        UploadPageIndexingRequestedEvent event = new UploadPageIndexingRequestedEvent("session-1", "project-1", Set.of("page-1"));

        dispatcher.onUploadPageIndexingRequested(event);

        verify(uploadPageIndexWorker).indexPageAsync("session-1", "project-1", "page-1");
        verify(uploadSessionEventBroadcaster).broadcastPageIndexState("session-1", "project-1", "page-1", "queued");
    }

    @Test
    void workerSchedulingFailureClearsTrackerState() {
        dispatcher = new UploadPageIndexDispatcher(
                pageIndexStatusTracker,
                uploadPageIndexWorker,
                uploadSessionEventBroadcaster,
                uploadProperties(60_000L)
        );
        doThrow(new RuntimeException("boom"))
                .when(uploadPageIndexWorker)
                .indexPageAsync("session-1", "project-1", "page-1");

        UploadPageIndexingRequestedEvent event = new UploadPageIndexingRequestedEvent("session-1", "project-1", Set.of("page-1"));

        dispatcher.onUploadPageIndexingRequested(event);

        assertFalse(pageIndexStatusTracker.isIndexing("page-1"));
        verify(uploadSessionEventBroadcaster).broadcastPageIndexState("session-1", "project-1", "page-1", "failed");
    }

    private UploadProperties uploadProperties(long staleThresholdMs) {
        UploadProperties properties = new UploadProperties();
        UploadProperties.IndexingProperties indexing = new UploadProperties.IndexingProperties();
        indexing.setStaleThresholdMs(staleThresholdMs);
        properties.setIndexing(indexing);
        return properties;
    }
}

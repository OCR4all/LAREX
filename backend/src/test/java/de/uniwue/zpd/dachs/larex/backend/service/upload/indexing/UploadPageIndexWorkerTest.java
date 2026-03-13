package de.uniwue.zpd.dachs.larex.backend.service.upload.indexing;

import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageFilterIndexService;
import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageIndexStatusTracker;
import de.uniwue.zpd.dachs.larex.backend.service.upload.UploadSessionEventBroadcaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UploadPageIndexWorkerTest {

    @Mock
    private PageRepository pageRepository;

    @Mock
    private PageFilterIndexService pageFilterIndexService;

    @Mock
    private UploadSessionEventBroadcaster uploadSessionEventBroadcaster;

    private PageIndexStatusTracker pageIndexStatusTracker;
    private UploadPageIndexWorker worker;

    @BeforeEach
    void setUp() {
        pageIndexStatusTracker = new PageIndexStatusTracker();
        worker = new UploadPageIndexWorker(
                pageRepository,
                pageFilterIndexService,
                pageIndexStatusTracker,
                uploadSessionEventBroadcaster
        );
    }

    @Test
    void clearsTrackerStateWhenIndexingFails() {
        Page page = new Page();
        page.setId("page-1");
        pageIndexStatusTracker.markIndexingIfAbsent("page-1");

        when(pageRepository.findById("page-1")).thenReturn(Optional.of(page));
        doThrow(new RuntimeException("index failed")).when(pageFilterIndexService).indexPageFromXml(page);

        worker.indexPageAsync("session-1", "project-1", "page-1");

        assertFalse(pageIndexStatusTracker.isIndexing("page-1"));
        verify(uploadSessionEventBroadcaster).broadcastPageIndexState("session-1", "project-1", "page-1", "failed");
    }
}

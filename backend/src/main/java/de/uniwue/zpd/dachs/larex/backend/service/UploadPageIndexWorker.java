package de.uniwue.zpd.dachs.larex.backend.service;

import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.repository.PageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class UploadPageIndexWorker {

    private static final Logger log = LoggerFactory.getLogger(UploadPageIndexWorker.class);

    private final PageRepository pageRepository;
    private final PageFilterIndexService pageFilterIndexService;
    private final PageIndexStatusTracker pageIndexStatusTracker;
    private final UploadSessionEventBroadcaster uploadSessionEventBroadcaster;

    public UploadPageIndexWorker(PageRepository pageRepository,
                                 PageFilterIndexService pageFilterIndexService,
                                 PageIndexStatusTracker pageIndexStatusTracker,
                                 UploadSessionEventBroadcaster uploadSessionEventBroadcaster) {
        this.pageRepository = pageRepository;
        this.pageFilterIndexService = pageFilterIndexService;
        this.pageIndexStatusTracker = pageIndexStatusTracker;
        this.uploadSessionEventBroadcaster = uploadSessionEventBroadcaster;
    }

    @Async("uploadIndexTaskExecutor")
    public void indexPageAsync(String sessionId, String projectId, String pageId) {
        try {
            uploadSessionEventBroadcaster.broadcastPageIndexState(sessionId, projectId, pageId, "indexing");
            Page page = pageRepository.findById(pageId).orElse(null);
            if (page == null) {
                log.warn("Skipping background indexing for missing page {} in project {}", pageId, projectId);
                uploadSessionEventBroadcaster.broadcastPageIndexState(sessionId, projectId, pageId, "failed");
                return;
            }

            pageFilterIndexService.indexPageFromXml(page);
            uploadSessionEventBroadcaster.broadcastPageIndexState(sessionId, projectId, pageId, "indexed");
        } catch (Exception e) {
            log.warn("Background indexing failed for page {} in project {}: {}", pageId, projectId, e.getMessage(), e);
            uploadSessionEventBroadcaster.broadcastPageIndexState(sessionId, projectId, pageId, "failed");
        } finally {
            pageIndexStatusTracker.clearIndexing(pageId);
        }
    }
}

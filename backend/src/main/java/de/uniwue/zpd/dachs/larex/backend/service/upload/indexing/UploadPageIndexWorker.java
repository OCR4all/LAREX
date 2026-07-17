package de.uniwue.zpd.dachs.larex.backend.service.upload.indexing;

import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageFilterIndexService;
import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageIndexStatusTracker;
import de.uniwue.zpd.dachs.larex.backend.service.notification.JobRealtimePublisher;
import de.uniwue.zpd.dachs.larex.backend.service.upload.UploadSessionEventBroadcaster;
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
    private final JobRealtimePublisher jobRealtimePublisher;

    public UploadPageIndexWorker(PageRepository pageRepository,
                                 PageFilterIndexService pageFilterIndexService,
                                 PageIndexStatusTracker pageIndexStatusTracker,
                                 UploadSessionEventBroadcaster uploadSessionEventBroadcaster,
                                 JobRealtimePublisher jobRealtimePublisher) {
        this.pageRepository = pageRepository;
        this.pageFilterIndexService = pageFilterIndexService;
        this.pageIndexStatusTracker = pageIndexStatusTracker;
        this.uploadSessionEventBroadcaster = uploadSessionEventBroadcaster;
        this.jobRealtimePublisher = jobRealtimePublisher;
    }

    @Async("uploadIndexTaskExecutor")
    public void indexPageAsync(String sessionId, String projectId, String pageId) {
        String finalStatus = "UNINDEXED";
        try {
            uploadSessionEventBroadcaster.broadcastPageIndexState(sessionId, projectId, pageId, "indexing");
            publishUpdate(projectId, pageId, "INDEXING");
            Page page = pageRepository.findById(pageId).orElse(null);
            if (page == null) {
                log.warn("Skipping background indexing for missing page {} in project {}", pageId, projectId);
                uploadSessionEventBroadcaster.broadcastPageIndexState(sessionId, projectId, pageId, "failed");
                return;
            }

            pageFilterIndexService.indexPageFromXml(page);
            uploadSessionEventBroadcaster.broadcastPageIndexState(sessionId, projectId, pageId, "indexed");
            finalStatus = "INDEXED";
        } catch (Exception e) {
            log.warn("Background indexing failed for page {} in project {}: {}", pageId, projectId, e.getMessage(), e);
            uploadSessionEventBroadcaster.broadcastPageIndexState(sessionId, projectId, pageId, "failed");
        } finally {
            pageIndexStatusTracker.clearIndexing(pageId);
            publishUpdate(projectId, pageId, finalStatus);
        }
    }

    private void publishUpdate(String projectId, String pageId, String status) {
        jobRealtimePublisher.publish("PAGE_INDEX", pageId, null, projectId, status, null);
    }
}

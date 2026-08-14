package de.uniwue.zpd.dachs.larex.backend.service.upload.indexing;

import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageFilterIndexService;
import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageIndexStatusTracker;
import de.uniwue.zpd.dachs.larex.backend.service.notification.JobRealtimePublisher;
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
    private final JobRealtimePublisher jobRealtimePublisher;

    public UploadPageIndexWorker(PageRepository pageRepository,
                                 PageFilterIndexService pageFilterIndexService,
                                 PageIndexStatusTracker pageIndexStatusTracker,
                                 JobRealtimePublisher jobRealtimePublisher) {
        this.pageRepository = pageRepository;
        this.pageFilterIndexService = pageFilterIndexService;
        this.pageIndexStatusTracker = pageIndexStatusTracker;
        this.jobRealtimePublisher = jobRealtimePublisher;
    }

    @Async("uploadIndexTaskExecutor")
    public void indexPageAsync(String sessionId, String projectId, String pageId) {
        String finalStatus = "UNINDEXED";
        try {
            publishUpdate(projectId, pageId, "INDEXING");
            Page page = pageRepository.findById(pageId).orElse(null);
            if (page == null) {
                log.warn("Skipping background indexing for missing page {} in project {}", pageId, projectId);
                return;
            }

            pageFilterIndexService.indexPageFromXml(page);
            finalStatus = "INDEXED";
        } catch (Exception e) {
            log.warn("Background indexing failed for page {} in project {}: {}", pageId, projectId, e.getMessage(), e);
        } finally {
            pageIndexStatusTracker.clearIndexing(pageId);
            publishUpdate(projectId, pageId, finalStatus);
        }
    }

    private void publishUpdate(String projectId, String pageId, String status) {
        jobRealtimePublisher.publish("PAGE_INDEX", pageId, null, projectId, status, null);
    }
}

package de.uniwue.zpd.dachs.larex.backend.service.version;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class PageXmlVersionPruneWorker {

    private static final Logger log = LoggerFactory.getLogger(PageXmlVersionPruneWorker.class);

    private final PageXmlVersionService pageXmlVersionService;

    public PageXmlVersionPruneWorker(PageXmlVersionService pageXmlVersionService) {
        this.pageXmlVersionService = pageXmlVersionService;
    }

    @Async("taskExecutor")
    public void pruneAsync(String pageXmlId) {
        try {
            pageXmlVersionService.pruneOldVersions(pageXmlId);
        } catch (Exception e) {
            log.warn("Failed to prune XML versions for {}: {}", pageXmlId, e.getMessage(), e);
        }
    }
}

package de.uniwue.zpd.dachs.larex.backend.scheduler;

import de.uniwue.zpd.dachs.larex.backend.service.admin.ErrorEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ErrorEventRetentionScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ErrorEventRetentionScheduler.class);

    private final ErrorEventService errorEventService;

    public ErrorEventRetentionScheduler(ErrorEventService errorEventService) {
        this.errorEventService = errorEventService;
    }

    @Scheduled(cron = "0 15 4 * * ?")
    public void pruneExpiredEvents() {
        long deleted = errorEventService.pruneExpiredEvents();
        if (deleted > 0) {
            logger.info("Pruned {} expired error event(s)", deleted);
        }
    }
}

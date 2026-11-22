package de.uniwue.zpd.dachs.larex.backend.service.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Component
public class SearchLexiconRebuildScheduler {

    private static final Logger log = LoggerFactory.getLogger(SearchLexiconRebuildScheduler.class);
    private static final Duration DEBOUNCE = Duration.ofMillis(500);

    private final SearchLexiconService searchLexiconService;
    private final TaskScheduler scheduler;
    private final Map<String, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();

    public SearchLexiconRebuildScheduler(SearchLexiconService searchLexiconService,
                                         @Qualifier("actionResultTaskScheduler") TaskScheduler scheduler) {
        this.searchLexiconService = searchLexiconService;
        this.scheduler = scheduler;
    }

    public void scheduleRebuild(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    debounce(projectId);
                }
            });
        } else {
            debounce(projectId);
        }
    }

    private void debounce(String projectId) {
        pending.compute(projectId, (id, previous) -> {
            if (previous != null) {
                previous.cancel(false);
            }
            return scheduler.schedule(() -> rebuild(id), Instant.now().plus(DEBOUNCE));
        });
    }

    private void rebuild(String projectId) {
        pending.remove(projectId);
        try {
            searchLexiconService.rebuildProjectLexicon(projectId);
        } catch (RuntimeException e) {
            log.warn("Failed to rebuild search lexicon for project {}: {}", projectId, e.getMessage(), e);
        }
    }
}

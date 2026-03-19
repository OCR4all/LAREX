package de.uniwue.zpd.dachs.larex.backend.service.annotation.indexing;

import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageFilterIndexService;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.events.AnnotationSavedEvent;
import de.uniwue.zpd.dachs.larex.backend.service.search.SearchLexiconService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.core.task.TaskRejectedException;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class AnnotationPostSaveIndexDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AnnotationPostSaveIndexDispatcher.class);

    private final ThreadPoolTaskExecutor executor;
    private final PageRepository pageRepository;
    private final PageFilterIndexService pageFilterIndexService;
    private final SearchLexiconService searchLexiconService;
    private final ConcurrentMap<String, PendingIndexState> pendingByPageId = new ConcurrentHashMap<>();

    public AnnotationPostSaveIndexDispatcher(
            @Qualifier("annotationPostSaveTaskExecutor") ThreadPoolTaskExecutor executor,
            PageRepository pageRepository,
            PageFilterIndexService pageFilterIndexService,
            SearchLexiconService searchLexiconService) {
        this.executor = executor;
        this.pageRepository = pageRepository;
        this.pageFilterIndexService = pageFilterIndexService;
        this.searchLexiconService = searchLexiconService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAnnotationSaved(AnnotationSavedEvent event) {
        if (event == null || event.pageId() == null || event.pageId().isBlank() || event.pageDto() == null) {
            return;
        }

        final boolean[] shouldSchedule = {false};
        pendingByPageId.compute(event.pageId(), (pageId, existing) -> {
            PendingIndexState state = existing != null ? existing : new PendingIndexState();
            synchronized (state) {
                state.latestEvent = event;
                if (!state.running) {
                    state.running = true;
                    shouldSchedule[0] = true;
                }
            }
            return state;
        });

        if (shouldSchedule[0]) {
            try {
                executor.execute(() -> processPage(event.pageId()));
            } catch (TaskRejectedException e) {
                PendingIndexState state = pendingByPageId.remove(event.pageId());
                if (state != null) {
                    synchronized (state) {
                        state.running = false;
                    }
                }
                log.warn("Post-save indexing queue rejected page {}: {}", event.pageId(), e.getMessage());
            }
        }
    }

    private void processPage(String pageId) {
        while (true) {
            PendingIndexState state = pendingByPageId.get(pageId);
            if (state == null) {
                return;
            }

            AnnotationSavedEvent event;
            synchronized (state) {
                event = state.latestEvent;
                state.latestEvent = null;
            }

            if (event == null) {
                synchronized (state) {
                    if (state.latestEvent == null) {
                        state.running = false;
                        pendingByPageId.remove(pageId, state);
                        return;
                    }
                }
                continue;
            }

            long startedAt = System.nanoTime();
            try {
                Page page = pageRepository.findById(event.pageId()).orElse(null);
                if (page == null) {
                    log.warn("Skipping post-save indexing for missing page {}", event.pageId());
                    continue;
                }
                pageFilterIndexService.indexPage(page, event.pageDto());
                if (page.getProject() != null) {
                    searchLexiconService.rebuildProjectLexicon(page.getProject().getId());
                }
                log.debug("Indexed saved annotations for page {} in {} ms",
                        event.pageId(), (System.nanoTime() - startedAt) / 1_000_000);
            } catch (Exception e) {
                log.warn("Post-save indexing failed for page {}: {}", event.pageId(), e.getMessage(), e);
            }
        }
    }

    private static final class PendingIndexState {
        private boolean running;
        private AnnotationSavedEvent latestEvent;
    }
}

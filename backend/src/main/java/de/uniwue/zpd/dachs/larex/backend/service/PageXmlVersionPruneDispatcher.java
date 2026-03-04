package de.uniwue.zpd.dachs.larex.backend.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PageXmlVersionPruneDispatcher {

    private final PageXmlVersionPruneWorker pruneWorker;

    public PageXmlVersionPruneDispatcher(PageXmlVersionPruneWorker pruneWorker) {
        this.pruneWorker = pruneWorker;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPageXmlVersionCreated(PageXmlVersionCreatedEvent event) {
        if (event == null || event.pageXmlId() == null || event.pageXmlId().isBlank()) {
            return;
        }
        pruneWorker.pruneAsync(event.pageXmlId());
    }
}

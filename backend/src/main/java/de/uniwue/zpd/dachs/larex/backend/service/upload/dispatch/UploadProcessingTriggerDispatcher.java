package de.uniwue.zpd.dachs.larex.backend.service.upload.dispatch;

import de.uniwue.zpd.dachs.larex.backend.service.upload.UploadSessionProcessingCoordinator;
import de.uniwue.zpd.dachs.larex.backend.service.upload.events.UploadFileReassembledEvent;
import de.uniwue.zpd.dachs.larex.backend.service.upload.events.UploadSessionFinalizedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;


@Component
public class UploadProcessingTriggerDispatcher {

    private final UploadSessionProcessingCoordinator coordinator;

    public UploadProcessingTriggerDispatcher(UploadSessionProcessingCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUploadFileReassembled(UploadFileReassembledEvent event) {
        if (event == null || event.sessionId() == null || event.sessionId().isBlank()) {
            return;
        }
        coordinator.requestProcessing(event.sessionId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUploadSessionFinalized(UploadSessionFinalizedEvent event) {
        if (event == null || event.sessionId() == null || event.sessionId().isBlank()) {
            return;
        }
        coordinator.requestProcessing(event.sessionId());
    }
}

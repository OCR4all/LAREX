package de.uniwue.zpd.dachs.larex.backend.service.action;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionAuditEvent;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionAuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class ActionAuditService {

    private static final Logger log = LoggerFactory.getLogger(ActionAuditService.class);

    private final ActionAuditEventRepository auditRepository;
    private final ObjectMapper objectMapper;

    public ActionAuditService(ActionAuditEventRepository auditRepository, ObjectMapper objectMapper) {
        this.auditRepository = auditRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action,
                       String outcome,
                       String actorUserId,
                       String processorDefinitionId,
                       String runId,
                       String workspaceId,
                       String projectId,
                       Map<String, ?> details) {
        try {
            ActionAuditEvent event = new ActionAuditEvent();
            event.setAction(action);
            event.setOutcome(outcome);
            event.setActorUserId(actorUserId);
            event.setProcessorDefinitionId(processorDefinitionId);
            event.setRunId(runId);
            event.setWorkspaceId(workspaceId);
            event.setProjectId(projectId);
            event.setDetailsJson(details == null || details.isEmpty() ? null : objectMapper.writeValueAsString(details));
            auditRepository.save(event);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize Action audit event details for {}", action, e);
        } catch (RuntimeException e) {
            log.warn("Failed to persist Action audit event {}", action, e);
        }
    }
}

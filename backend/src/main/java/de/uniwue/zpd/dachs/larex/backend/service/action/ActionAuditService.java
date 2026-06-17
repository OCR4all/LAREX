package de.uniwue.zpd.dachs.larex.backend.service.action;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionAuditEvent;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionAuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
        } catch (JacksonException e) {
            log.warn("Failed to serialize Action audit event details for {}", action, e);
        } catch (RuntimeException e) {
            log.warn("Failed to persist Action audit event {}", action, e);
        }
    }

    @Transactional(readOnly = true)
    public List<ActionDto.AuditEventResponse> listForDefinition(String definitionId) {
        return auditRepository.findTop100ByProcessorDefinitionIdOrderByCreatedDesc(definitionId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private ActionDto.AuditEventResponse toResponse(ActionAuditEvent event) {
        return new ActionDto.AuditEventResponse(
                event.getId(),
                event.getAction(),
                event.getOutcome(),
                event.getActorUserId(),
                event.getProcessorDefinitionId(),
                event.getRunId(),
                event.getWorkspaceId(),
                event.getProjectId(),
                parseDetails(event.getDetailsJson()),
                event.getCreated()
        );
    }

    private Object parseDetails(String detailsJson) {
        if (detailsJson == null || detailsJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(detailsJson, Object.class);
        } catch (JacksonException e) {
            return detailsJson;
        }
    }
}

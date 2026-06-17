package de.uniwue.zpd.dachs.larex.backend.service.admin;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserAuditAction;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserAuditEventDto;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserAuditOutcome;
import de.uniwue.zpd.dachs.larex.backend.entity.AdminUserAuditLog;
import de.uniwue.zpd.dachs.larex.backend.repository.admin.AdminUserAuditLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class AdminUserAuditService {

    private final AdminUserAuditLogRepository adminUserAuditLogRepository;
    private final ObjectMapper objectMapper;

    public AdminUserAuditService(AdminUserAuditLogRepository adminUserAuditLogRepository, ObjectMapper objectMapper) {
        this.adminUserAuditLogRepository = adminUserAuditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void logEvent(
            String actorUserId,
            String actorUsername,
            String targetUserId,
            String targetUsername,
            AdminUserAuditAction action,
            AdminUserAuditOutcome outcome,
            Map<String, Object> details) {
        AdminUserAuditLog log = new AdminUserAuditLog();
        log.setActorUserId(actorUserId);
        log.setActorUsername(actorUsername);
        log.setTargetUserId(targetUserId);
        log.setTargetUsername(targetUsername);
        log.setAction(action);
        log.setOutcome(outcome);
        log.setDetails(serializeDetails(details));
        adminUserAuditLogRepository.save(log);
    }

    public List<AdminUserAuditEventDto> getAuditEvents(String targetUserId, int limit) {
        return adminUserAuditLogRepository
                .findByTargetUserIdOrderByCreatedDesc(targetUserId, PageRequest.of(0, limit))
                .stream()
                .map(log -> new AdminUserAuditEventDto(
                        log.getId(),
                        log.getAction(),
                        log.getOutcome(),
                        log.getActorUserId(),
                        log.getActorUsername(),
                        log.getCreated() != null ? log.getCreated().toString() : null,
                        log.getDetails()
                ))
                .toList();
    }

    private String serializeDetails(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(details);
        } catch (JacksonException e) {
            return "{\"error\":\"Failed to serialize audit details\"}";
        }
    }
}

package de.uniwue.zpd.dachs.larex.backend.service.admin;

import de.uniwue.zpd.dachs.larex.backend.config.ErrorLogProperties;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminErrorEventDetailDto;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminErrorEventPageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminErrorEventSummaryDto;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminErrorSummaryDto;
import de.uniwue.zpd.dachs.larex.backend.dto.ErrorEventCaptureRequest;
import de.uniwue.zpd.dachs.larex.backend.entity.ErrorEvent;
import de.uniwue.zpd.dachs.larex.backend.entity.ErrorEvent.Severity;
import de.uniwue.zpd.dachs.larex.backend.repository.admin.ErrorEventRepository;
import de.uniwue.zpd.dachs.larex.backend.exception.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ErrorEventService {

    private final ErrorEventRepository errorEventRepository;
    private final ErrorLogProperties errorLogProperties;

    public ErrorEventService(ErrorEventRepository errorEventRepository, ErrorLogProperties errorLogProperties) {
        this.errorEventRepository = errorEventRepository;
        this.errorLogProperties = errorLogProperties;
    }

    @Transactional
    public String capture(ErrorEventCaptureRequest request) {
        ErrorEvent event = new ErrorEvent();
        event.setStatus(request.status());
        event.setSeverity(resolveSeverity(request.status()));
        event.setCode(trimToNull(request.code()));
        event.setError(fallback(request.error(), "Unknown Error"));
        event.setMessage(fallback(request.message(), "No message available"));
        event.setPath(fallback(request.path(), "unknown"));
        event.setMethod(fallback(request.method(), "UNKNOWN"));
        event.setExceptionClass(trimToNull(request.exceptionClass()));
        event.setUserId(trimToNull(request.userId()));
        event.setUsername(trimToNull(request.username()));
        event.setWorkspaceId(trimToNull(request.workspaceId()));
        event.setDetailsJson(trimToNull(request.detailsJson()));
        event.setStackTrace(truncate(trimToNull(request.stackTrace()), errorLogProperties.getStackTraceMaxLength()));

        return errorEventRepository.save(event).getId();
    }

    public AdminErrorSummaryDto getSummary(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(Math.max(1, days));
        return new AdminErrorSummaryDto(
                Math.max(1, days),
                errorEventRepository.countSince(since),
                errorEventRepository.countServerErrorsSince(since),
                errorEventRepository.countActionableClientErrorsSince(since),
                errorEventRepository.countDistinctUsersSince(since),
                errorEventRepository.countDistinctWorkspacesSince(since)
        );
    }

    public AdminErrorEventPageDto getEvents(
            int page,
            int size,
            int days,
            Integer status,
            String userId,
            String workspaceId,
            String query
    ) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        LocalDateTime since = LocalDateTime.now().minusDays(Math.max(1, days));
        Specification<ErrorEvent> specification = createdSince(since);
        specification = append(specification, matchesStatus(status));
        specification = append(specification, matchesUserId(userId));
        specification = append(specification, matchesWorkspaceId(workspaceId));
        specification = append(specification, matchesQuery(query));

        Page<ErrorEvent> result = errorEventRepository.findAll(specification, pageable);
        List<AdminErrorEventSummaryDto> items = result.getContent().stream()
                .map(this::toSummaryDto)
                .toList();

        return new AdminErrorEventPageDto(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    public AdminErrorEventDetailDto getEvent(String errorId) {
        ErrorEvent event = errorEventRepository.findById(errorId)
                .orElseThrow(() -> new ResourceNotFoundException("Error event not found"));
        return toDetailDto(event);
    }

    @Transactional
    public long pruneExpiredEvents() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(Math.max(1, errorLogProperties.getRetentionDays()));
        return errorEventRepository.deleteByCreatedBefore(cutoff);
    }

    private Severity resolveSeverity(int status) {
        return status >= 500 ? Severity.ERROR : Severity.WARN;
    }

    private Specification<ErrorEvent> createdSince(LocalDateTime since) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("created"), since);
    }

    private Specification<ErrorEvent> matchesStatus(Integer status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    private Specification<ErrorEvent> matchesUserId(String userId) {
        String normalized = trimToNull(userId);
        if (normalized == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("userId"), normalized);
    }

    private Specification<ErrorEvent> matchesWorkspaceId(String workspaceId) {
        String normalized = trimToNull(workspaceId);
        if (normalized == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("workspaceId"), normalized);
    }

    private Specification<ErrorEvent> matchesQuery(String query) {
        String normalized = trimToNull(query);
        if (normalized == null) {
            return null;
        }

        String like = "%" + normalized.toLowerCase() + "%";
        return (root, queryObj, cb) -> cb.or(
                cb.like(cb.lower(root.get("message")), like),
                cb.like(cb.lower(root.get("error")), like),
                cb.like(cb.lower(root.get("path")), like),
                cb.like(cb.lower(root.get("username")), like),
                cb.like(cb.lower(root.get("userId")), like),
                cb.like(cb.lower(root.get("workspaceId")), like),
                cb.like(cb.lower(root.get("code")), like)
        );
    }

    private AdminErrorEventSummaryDto toSummaryDto(ErrorEvent event) {
        return new AdminErrorEventSummaryDto(
                event.getId(),
                event.getCreated() != null ? event.getCreated().toString() : null,
                event.getStatus(),
                event.getSeverity().name(),
                event.getCode(),
                event.getError(),
                event.getMessage(),
                event.getPath(),
                event.getMethod(),
                event.getUserId(),
                event.getUsername(),
                event.getWorkspaceId()
        );
    }

    private AdminErrorEventDetailDto toDetailDto(ErrorEvent event) {
        return new AdminErrorEventDetailDto(
                event.getId(),
                event.getCreated() != null ? event.getCreated().toString() : null,
                event.getStatus(),
                event.getSeverity().name(),
                event.getCode(),
                event.getError(),
                event.getMessage(),
                event.getPath(),
                event.getMethod(),
                event.getExceptionClass(),
                event.getUserId(),
                event.getUsername(),
                event.getWorkspaceId(),
                event.getDetailsJson(),
                event.getStackTrace()
        );
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String fallback(String value, String defaultValue) {
        String normalized = trimToNull(value);
        return normalized != null ? normalized : defaultValue;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || maxLength <= 0 || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private Specification<ErrorEvent> append(Specification<ErrorEvent> base, Specification<ErrorEvent> next) {
        if (next == null) {
            return base;
        }
        return base.and(next);
    }
}

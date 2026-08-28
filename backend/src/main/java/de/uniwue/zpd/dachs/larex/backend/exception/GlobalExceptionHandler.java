package de.uniwue.zpd.dachs.larex.backend.exception;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import de.uniwue.zpd.dachs.larex.backend.dto.ErrorResponseDto;
import de.uniwue.zpd.dachs.larex.backend.dto.ErrorEventCaptureRequest;
import de.uniwue.zpd.dachs.larex.backend.dto.StorageQuotaErrorResponseDto;
import de.uniwue.zpd.dachs.larex.backend.dto.AnnotationCollaborationDto;
import de.uniwue.zpd.dachs.larex.backend.service.admin.ErrorEventContextResolver;
import de.uniwue.zpd.dachs.larex.backend.service.admin.ErrorEventService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Global exception handler for all REST controllers
 * Provides consistent error responses to the frontend
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ErrorEventService errorEventService;
    private final ErrorEventContextResolver errorEventContextResolver;
    private final ObjectMapper objectMapper;

    public GlobalExceptionHandler(
            ErrorEventService errorEventService,
            ErrorEventContextResolver errorEventContextResolver,
            ObjectMapper objectMapper) {
        this.errorEventService = errorEventService;
        this.errorEventContextResolver = errorEventContextResolver;
        this.objectMapper = objectMapper;
    }

    /**
     * Handle locked annotation lease conflicts.
     */
    @ExceptionHandler(AnnotationLeaseLockedException.class)
    public ResponseEntity<AnnotationCollaborationDto.LockErrorResponse> handleAnnotationLeaseLockedException(
            AnnotationLeaseLockedException ex, HttpServletRequest request) {

        AnnotationCollaborationDto.LockErrorResponse errorResponse = new AnnotationCollaborationDto.LockErrorResponse(
                HttpStatus.LOCKED.value(),
                "Annotation Locked",
                ex.getMessage(),
                request.getRequestURI(),
                ex.getOwner(),
                ex.getReason()
        );

        return ResponseEntity.status(HttpStatus.LOCKED).body(errorResponse);
    }

    /**
     * Handle validation errors from @Valid annotations
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidationErrors(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .collect(Collectors.toList());

        ErrorResponseDto errorResponse = new ErrorResponseDto(
                HttpStatus.BAD_REQUEST.value(),
                "Validation Failed",
                "Invalid input data provided",
                request.getRequestURI(),
                errors
        );

        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Handle constraint validation errors
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponseDto> handleConstraintViolationException(
            ConstraintViolationException ex, HttpServletRequest request) {

        List<String> errors = ex.getConstraintViolations()
                .stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toList());

        ErrorResponseDto errorResponse = new ErrorResponseDto(
                HttpStatus.BAD_REQUEST.value(),
                "Validation Failed",
                "Constraint validation failed",
                request.getRequestURI(),
                errors
        );

        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Handle typed admin user-management errors with stable machine-readable codes.
     */
    @ExceptionHandler(AdminUserManagementException.class)
    public ResponseEntity<ErrorResponseDto> handleAdminUserManagementException(
            AdminUserManagementException ex, HttpServletRequest request) {

        HttpStatus status = resolveAdminUserStatus(ex.getCode());
        String errorId = captureEvent(
                request,
                status.value(),
                status.getReasonPhrase(),
                ex.getMessage(),
                ex.getCode().name(),
                ex,
                null,
                shouldPersist(status)
        );
        ErrorResponseDto errorResponse = new ErrorResponseDto(
                status.value(),
                status.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                ex.getCode().name(),
                errorId
        );

        return ResponseEntity.status(status).body(errorResponse);
    }

    /**
     * Handle IllegalArgumentException (business logic validation)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDto> handleIllegalArgumentException(
            IllegalArgumentException ex, HttpServletRequest request) {

        ErrorResponseDto errorResponse = new ErrorResponseDto(
                HttpStatus.BAD_REQUEST.value(),
                "Invalid Request",
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(ActionConcurrencyLimitException.class)
    public ResponseEntity<ErrorResponseDto> handleActionConcurrencyLimitException(
            ActionConcurrencyLimitException ex, HttpServletRequest request) {

        ErrorResponseDto errorResponse = new ErrorResponseDto(
                HttpStatus.CONFLICT.value(),
                "Concurrency limit reached",
                ex.getMessage(),
                request.getRequestURI(),
                "ACTION_CONCURRENCY_LIMIT_REACHED"
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    /**
     * Handle malformed JSON / unreadable request bodies.
     * This includes cases like unknown properties when DTOs are configured strictly.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDto> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        String message = ex.getMostSpecificCause() != null && ex.getMostSpecificCause().getMessage() != null
                ? ex.getMostSpecificCause().getMessage()
                : "Request body is not readable";

        ErrorResponseDto errorResponse = new ErrorResponseDto(
                HttpStatus.BAD_REQUEST.value(),
                "Invalid JSON",
                message,
                request.getRequestURI()
        );

        return ResponseEntity.badRequest().body(errorResponse);
    }

        @ExceptionHandler(ProjectNameConflictException.class)
    public ResponseEntity<ErrorResponseDto> handleProjectNameConflictException(
            ProjectNameConflictException ex, HttpServletRequest request) {

        ErrorResponseDto errorResponse = new ErrorResponseDto(
                HttpStatus.CONFLICT.value(),
                "Project Name Conflict",
                ex.getMessage(),
                request.getRequestURI(),
                "PROJECT_NAME_CONFLICT"
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    /**
     * Handle database constraint violations (e.g., unique constraint violations)
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDto> handleDataIntegrityViolationException(
            DataIntegrityViolationException ex, HttpServletRequest request) {

        String message = "Data integrity violation";
        String code = null;

        // Try to provide more specific error messages for common constraint violations
        if (ex.getMessage() != null) {
            String exMessage = ex.getMessage().toLowerCase();
            if (exMessage.contains("unique constraint") || exMessage.contains("duplicate key")) {
                if (exMessage.contains("workspace") && exMessage.contains("name")) {
                    message = "A workspace with this name already exists";
                } else if (exMessage.contains("uk_project_name_library")
                        || (exMessage.contains("project") && exMessage.contains("name"))) {
                    message = "A project with this name already exists in this workspace";
                    code = "PROJECT_NAME_CONFLICT";
                } else {
                    message = "This value already exists and must be unique";
                }
            } else if (exMessage.contains("foreign key constraint")) {
                message = "Cannot perform this operation due to related data constraints";
            } else if (exMessage.contains("not null constraint")) {
                message = "Required field cannot be empty";
            }
        }

        String errorId = captureEvent(
                request,
                HttpStatus.CONFLICT.value(),
                "Data Conflict",
                message,
                code,
                ex,
                null,
                true
        );
        ErrorResponseDto errorResponse = new ErrorResponseDto(
                HttpStatus.CONFLICT.value(),
                "Data Conflict",
                message,
                request.getRequestURI(),
                code,
                errorId
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    /**
     * Handle SecurityException (access denied)
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponseDto> handleSecurityException(
            SecurityException ex, HttpServletRequest request) {
        String errorId = captureEvent(
                request,
                HttpStatus.FORBIDDEN.value(),
                "Access Denied",
                ex.getMessage(),
                null,
                ex,
                null,
                true
        );
        ErrorResponseDto errorResponse = new ErrorResponseDto(
                HttpStatus.FORBIDDEN.value(),
                "Access Denied",
                ex.getMessage(),
                request.getRequestURI(),
                (String) null,
                errorId
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }

    /**
     * Handle Spring Security authorization failures.
     */
    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ErrorResponseDto> handleAccessDeniedException(
            RuntimeException ex, HttpServletRequest request) {

        String message = "You do not have permission to perform this action.";
        String errorId = captureEvent(
                request,
                HttpStatus.FORBIDDEN.value(),
                "Access Denied",
                message,
                null,
                ex,
                null,
                true
        );
        ErrorResponseDto errorResponse = new ErrorResponseDto(
                HttpStatus.FORBIDDEN.value(),
                "Access Denied",
                message,
                request.getRequestURI(),
                (String) null,
                errorId
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }

    /**
     * Handle ResourceNotFoundException (custom exception)
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleResourceNotFoundException(
            ResourceNotFoundException ex, HttpServletRequest request) {

        ErrorResponseDto errorResponse = new ErrorResponseDto(
                HttpStatus.NOT_FOUND.value(),
                "Resource Not Found",
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * A streaming client can close its connection before the archive is complete. There is no
     * usable response channel left for an error DTO in that case, so handling it as a normal API
     * error only produces a misleading converter warning.
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public ResponseEntity<Void> handleAsyncRequestNotUsableException(
            AsyncRequestNotUsableException ex, HttpServletRequest request) {
        if (isClientDisconnect(ex)) {
            logger.debug("Client disconnected while streaming {}", request.getRequestURI());
            return ResponseEntity.noContent().build();
        }

        logger.warn("Async response became unusable at {}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    @ExceptionHandler(StorageQuotaExceededException.class)
    public ResponseEntity<StorageQuotaErrorResponseDto> handleStorageQuotaExceededException(
            StorageQuotaExceededException ex, HttpServletRequest request) {
        String errorId = captureEvent(
                request,
                HttpStatus.INSUFFICIENT_STORAGE.value(),
                HttpStatus.INSUFFICIENT_STORAGE.getReasonPhrase(),
                ex.getMessage(),
                StorageQuotaExceededException.ERROR_CODE,
                ex,
                serializeDetails(new LinkedHashMap<>() {{
                    put("blockedOperation", ex.getBlockedOperation());
                    put("workspaceId", ex.getWorkspaceId());
                    put("requiredBytes", ex.getRequiredBytes());
                    put("quotaLimitBytes", ex.getQuotaLimitBytes());
                    put("currentUsageBytes", ex.getCurrentUsageBytes());
                    put("reservedBytes", ex.getReservedBytes());
                    put("availableBytes", ex.getAvailableBytes());
                    put("usagePercentage", ex.getUsagePercentage());
                }}),
                true
        );

        StorageQuotaErrorResponseDto errorResponse = new StorageQuotaErrorResponseDto(
                HttpStatus.INSUFFICIENT_STORAGE.value(),
                HttpStatus.INSUFFICIENT_STORAGE.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                StorageQuotaExceededException.ERROR_CODE,
                errorId,
                ex.getBlockedOperation(),
                ex.getWorkspaceId(),
                ex.getRequiredBytes(),
                ex.getQuotaLimitBytes(),
                ex.getCurrentUsageBytes(),
                ex.getReservedBytes(),
                ex.getAvailableBytes(),
                ex.getUsagePercentage()
        );

        return ResponseEntity.status(HttpStatus.INSUFFICIENT_STORAGE).body(errorResponse);
    }

    /**
     * Handle all other unexpected exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleGenericException(
            Exception ex, HttpServletRequest request) {

        // Log the full exception for debugging
        logger.error("Unexpected error at {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        String errorId = captureEvent(
                request,
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "An unexpected error occurred. Please try again later.",
                null,
                ex,
                null,
                true
        );
        ErrorResponseDto errorResponse = new ErrorResponseDto(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "An unexpected error occurred. Please try again later.",
                request.getRequestURI(),
                (String) null,
                errorId
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Format field error for better readability
     */
    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }

    private HttpStatus resolveAdminUserStatus(AdminUserErrorCode code) {
        return switch (code) {
            case ADMIN_USER_DUPLICATE_USERNAME,
                    ADMIN_USER_DUPLICATE_EMAIL,
                    ADMIN_USER_IDENTITY_PROVIDER_CONFLICT -> HttpStatus.CONFLICT;
            case ADMIN_USER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ADMIN_USER_SERVICE_ACCOUNT_FORBIDDEN,
                    ADMIN_USER_SELF_DISABLE_FORBIDDEN,
                    ADMIN_USER_PROVISIONING_DISABLED,
                    ADMIN_USER_EXTERNALLY_MANAGED -> HttpStatus.FORBIDDEN;
            case ADMIN_USER_INVALID_USERNAME,
                    ADMIN_USER_ALREADY_ENABLED,
                    ADMIN_USER_ALREADY_DISABLED,
                    ADMIN_USER_RESEND_NOT_ALLOWED,
                    ADMIN_USER_IDENTITY_PROVIDER_VALIDATION_FAILED -> HttpStatus.BAD_REQUEST;
            case ADMIN_USER_SETUP_EMAIL_FAILED, ADMIN_USER_KEYCLOAK_OPERATION_FAILED -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private boolean shouldPersist(HttpStatus status) {
        return status.is5xxServerError()
                || status == HttpStatus.FORBIDDEN
                || status == HttpStatus.CONFLICT
                || status == HttpStatus.INSUFFICIENT_STORAGE;
    }

    private boolean isClientDisconnect(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(java.util.Locale.ROOT);
                if (normalized.contains("disconnected client")
                        || normalized.contains("connection reset")
                        || normalized.contains("broken pipe")
                        || normalized.contains("closed channel")
                        || normalized.contains("clientabort")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private String captureEvent(
            HttpServletRequest request,
            int status,
            String error,
            String message,
            String code,
            Exception ex,
            String detailsJson,
            boolean persist
    ) {
        if (!persist) {
            return null;
        }

        return errorEventService.capture(new ErrorEventCaptureRequest(
                status,
                code,
                error,
                message,
                request.getRequestURI(),
                request.getMethod(),
                ex.getClass().getName(),
                errorEventContextResolver.resolveUserId(),
                errorEventContextResolver.resolveUsername(),
                errorEventContextResolver.resolveWorkspaceId(request),
                detailsJson,
                status >= 500 ? stackTraceOf(ex) : null
        ));
    }

    private String serializeDetails(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            return null;
        }
    }

    private String stackTraceOf(Exception ex) {
        StringWriter stringWriter = new StringWriter();
        ex.printStackTrace(new PrintWriter(stringWriter));
        return stringWriter.toString();
    }
}

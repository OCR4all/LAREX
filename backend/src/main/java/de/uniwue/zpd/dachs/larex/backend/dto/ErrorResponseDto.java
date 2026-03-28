package de.uniwue.zpd.dachs.larex.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Standardized error response DTO for API errors
 */
public record ErrorResponseDto(
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        List<String> details,
        String code,
        String errorId
) {
    
    public ErrorResponseDto(int status, String error, String message, String path) {
        this(LocalDateTime.now(), status, error, message, path, null, null, null);
    }
    
    public ErrorResponseDto(int status, String error, String message, String path, List<String> details) {
        this(LocalDateTime.now(), status, error, message, path, details, null, null);
    }

    public ErrorResponseDto(int status, String error, String message, String path, String code) {
        this(LocalDateTime.now(), status, error, message, path, null, code, null);
    }

    public ErrorResponseDto(int status, String error, String message, String path, List<String> details, String code) {
        this(LocalDateTime.now(), status, error, message, path, details, code, null);
    }

    public ErrorResponseDto(int status, String error, String message, String path, String code, String errorId) {
        this(LocalDateTime.now(), status, error, message, path, null, code, errorId);
    }

    public ErrorResponseDto(int status, String error, String message, String path, List<String> details, String code, String errorId) {
        this(LocalDateTime.now(), status, error, message, path, details, code, errorId);
    }
}

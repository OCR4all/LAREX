package de.uniwue.zpd.dachs.larex.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public class PrivateAccessTokenDto {

    public record CreateRequest(
            @NotBlank(message = "Workspace ID is required")
            String workspaceId,
            @NotBlank(message = "Token name is required")
            @Size(max = 128, message = "Token name must be at most 128 characters")
            String name,
            LocalDateTime expiresAt,
            @NotEmpty(message = "At least one scope is required")
            List<@NotBlank(message = "Scope must not be blank") String> scopes
    ) {
    }

    public record SummaryResponse(
            String id,
            String workspaceId,
            String name,
            String secretPrefix,
            List<String> scopes,
            LocalDateTime createdAt,
            LocalDateTime expiresAt,
            LocalDateTime revokedAt,
            LocalDateTime lastUsedAt,
            boolean active
    ) {
    }

    public record CreatedResponse(
            String id,
            String workspaceId,
            String name,
            List<String> scopes,
            LocalDateTime createdAt,
            LocalDateTime expiresAt,
            String secret
    ) {
    }
}

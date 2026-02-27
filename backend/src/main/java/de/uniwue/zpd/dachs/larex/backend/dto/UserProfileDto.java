package de.uniwue.zpd.dachs.larex.backend.dto;

import jakarta.validation.constraints.Size;

/**
 * DTO for user profile updates (non-security related fields only)
 */
public record UserProfileDto(
        String id,
        String username,
        String email,
        @Size(max = 50, message = "First name must not exceed 50 characters")
        String firstName,
        @Size(max = 50, message = "Last name must not exceed 50 characters")
        String lastName,
        String avatar
) {
    /**
     * Request DTO for updating user profile
     */
    public record UpdateRequest(
            @Size(max = 50, message = "First name must not exceed 50 characters")
            String firstName,
            @Size(max = 50, message = "Last name must not exceed 50 characters")
            String lastName,
            @Size(max = 500, message = "Avatar URL must not exceed 500 characters")
            String avatar
    ) {}
}
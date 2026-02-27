package de.uniwue.zpd.dachs.larex.backend.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminCreateUserRequest(
        @NotBlank(message = "username is required")
        @Size(max = 255, message = "username must be at most 255 characters")
        String username,

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        @Size(max = 255, message = "email must be at most 255 characters")
        String email,

        @Size(max = 255, message = "firstName must be at most 255 characters")
        String firstName,

        @Size(max = 255, message = "lastName must be at most 255 characters")
        String lastName
) {
    @JsonAnySetter
    public void rejectUnknownField(String key, Object value) {
        throw new IllegalArgumentException("Unknown field: " + key);
    }
}

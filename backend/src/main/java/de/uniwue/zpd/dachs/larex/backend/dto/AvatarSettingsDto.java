package de.uniwue.zpd.dachs.larex.backend.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public final class AvatarSettingsDto {

    private AvatarSettingsDto() {
    }

    public record PublicResponse(AvatarStyle defaultStyle) {
    }

    public record AdminResponse(
            AvatarStyle defaultStyle,
            LocalDateTime updatedAt,
            String updatedByUserId
    ) {
    }

    public record UpdateRequest(@NotNull AvatarStyle defaultStyle) {
    }
}

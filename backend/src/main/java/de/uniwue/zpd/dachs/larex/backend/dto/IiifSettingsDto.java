package de.uniwue.zpd.dachs.larex.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.LocalDateTime;

public final class IiifSettingsDto {

    private IiifSettingsDto() {
    }

    public record UpdateRequest(
            @Min(0) @Max(60_000) Integer downloadMinIntervalMs
    ) {
    }

    public record Response(
            int deploymentDefaultDownloadMinIntervalMs,
            Integer overrideDownloadMinIntervalMs,
            int effectiveDownloadMinIntervalMs,
            LocalDateTime updatedAt,
            String updatedByUserId
    ) {
    }
}

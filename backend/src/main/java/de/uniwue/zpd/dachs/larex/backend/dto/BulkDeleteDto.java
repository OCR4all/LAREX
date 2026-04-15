package de.uniwue.zpd.dachs.larex.backend.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public class BulkDeleteDto {

    public record BulkDeleteRequest(
            @NotEmpty(message = "IDs are required")
            @Size(max = 200, message = "Cannot bulk delete more than 200 items")
            List<String> ids
    ) {}

    public record BulkDeleteResponse(
            int successCount,
            int failedCount,
            List<String> deletedIds,
            List<String> failedIds,
            List<String> errors
    ) {}
}

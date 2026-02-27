package de.uniwue.zpd.dachs.larex.backend.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public class StorageCleanupDto {

    public record OrphanedFile(
            String path,
            String type,  // "image", "xml", "thumbnail", "temp"
            long sizeBytes,
            String lastModified
    ) {}

    public record OrphanedFilesResponse(
            List<OrphanedFile> files,
            int totalCount,
            long totalSizeBytes,
            String totalSizeFormatted
    ) {}

    public record CleanupRequest(
            @NotEmpty(message = "Paths are required")
            @Size(max = 500, message = "Cannot clean up more than 500 paths at once")
            List<String> paths
    ) {}

    public record CleanupResponse(
            int deletedCount,
            int failedCount,
            long freedBytes,
            String freedFormatted,
            List<String> errors
    ) {}

    public record StorageOverview(
            long totalUsedBytes,
            String totalUsedFormatted,
            int totalImages,
            int totalXmlFiles,
            int totalThumbnails,
            int orphanedImages,
            int orphanedXmlFiles,
            int orphanedThumbnails,
            int orphanedTempFiles,
            long orphanedTotalBytes,
            String orphanedTotalFormatted
    ) {}
}

package de.uniwue.zpd.dachs.larex.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public class UploadConflictDto {

    public record ConflictResponse(
            String conflictId,
            String conflictType,
            String existingFileName,
            String newFileName,
            String existingFilePath,
            String newFilePath,
            LocalDateTime conflictTimestamp,
            String pageId,
            String pageName,
            ConflictDetails details
    ) {}

    public record ConflictDetails(
            String existingFileSize,
            String newFileSize,
            LocalDateTime existingFileModified,
            LocalDateTime newFileModified,
            String existingFileChecksum,
            String newFileChecksum
    ) {}

    public record BatchConflictResolutionRequest(
            @NotNull List<ConflictResolution> resolutions
    ) {}

    public record ConflictResolution(
            @NotBlank String conflictId,
            @NotBlank String action // "keep_existing", "replace_with_new", "skip"
    ) {}
}

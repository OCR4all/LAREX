package de.uniwue.zpd.dachs.larex.backend.dto;

import de.uniwue.zpd.dachs.larex.backend.entity.PageXmlVersion;

import java.time.LocalDateTime;

public record PageXmlVersionDto(
    String id,
    Integer versionNumber,
    Long fileSize,
    String userId,
    String comment,
    LocalDateTime created
) {
    public static PageXmlVersionDto fromEntity(PageXmlVersion entity) {
        return new PageXmlVersionDto(
            entity.getId(),
            entity.getVersionNumber(),
            entity.getFileSize(),
            entity.getUserId(),
            entity.getComment(),
            entity.getCreated()
        );
    }
}

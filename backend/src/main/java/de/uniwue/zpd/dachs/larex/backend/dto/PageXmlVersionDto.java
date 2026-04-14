package de.uniwue.zpd.dachs.larex.backend.dto;

import de.uniwue.zpd.dachs.larex.backend.entity.PageXmlVersion;

import java.time.LocalDateTime;

public record PageXmlVersionDto(
    String id,
    Integer versionNumber,
    Long fileSize,
    String userId,
    String username,
    String userDisplayName,
    String comment,
    LocalDateTime created
) {
    public static PageXmlVersionDto fromEntity(PageXmlVersion entity, UserDto user) {
        return new PageXmlVersionDto(
            entity.getId(),
            entity.getVersionNumber(),
            entity.getFileSize(),
            entity.getUserId(),
            user == null ? null : user.username(),
            buildDisplayName(user, entity.getUserId()),
            entity.getComment(),
            entity.getCreated()
        );
    }

    private static String buildDisplayName(UserDto user, String fallbackUserId) {
        if (user == null) {
            return fallbackUserId;
        }
        if (user.firstName() != null && user.lastName() != null) {
            return user.firstName() + " " + user.lastName();
        }
        if (user.firstName() != null) {
            return user.firstName();
        }
        if (user.lastName() != null) {
            return user.lastName();
        }
        if (user.username() != null && !user.username().isBlank()) {
            return user.username();
        }
        return fallbackUserId;
    }
}

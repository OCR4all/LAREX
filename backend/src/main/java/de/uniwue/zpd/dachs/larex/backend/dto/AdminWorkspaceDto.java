package de.uniwue.zpd.dachs.larex.backend.dto;

public record AdminWorkspaceDto(
        String id,
        String name,
        String description,
        boolean isPersonal,
        String ownerUserId,
        String ownerUsername,
        long memberCount,
        long projectCount,
        String created
) {
}

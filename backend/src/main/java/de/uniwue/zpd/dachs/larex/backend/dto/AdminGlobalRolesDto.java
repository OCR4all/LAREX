package de.uniwue.zpd.dachs.larex.backend.dto;

public record AdminGlobalRolesDto(
        boolean globalAdmin,
        boolean globalCurator
) {
}

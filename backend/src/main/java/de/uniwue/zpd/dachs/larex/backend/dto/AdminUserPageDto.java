package de.uniwue.zpd.dachs.larex.backend.dto;

import java.util.List;

public record AdminUserPageDto(
        List<AdminUserDto> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean creationAllowed,
        boolean setupEmailAllowed
) {
}

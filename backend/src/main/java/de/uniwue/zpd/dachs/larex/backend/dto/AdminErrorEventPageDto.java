package de.uniwue.zpd.dachs.larex.backend.dto;

import java.util.List;

public record AdminErrorEventPageDto(
        List<AdminErrorEventSummaryDto> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}

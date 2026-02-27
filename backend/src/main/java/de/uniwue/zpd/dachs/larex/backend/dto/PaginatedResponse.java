package de.uniwue.zpd.dachs.larex.backend.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record PaginatedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T> PaginatedResponse<T> from(Page<T> page) {
        return new PaginatedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}

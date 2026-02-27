package de.uniwue.zpd.dachs.larex.backend.dto;

public record UserDto(
        String id,
        String username,
        String email,
        String firstName,
        String lastName,
        String avatar
) {
}
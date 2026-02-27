package de.uniwue.zpd.dachs.larex.backend.dto;

public record AdminUserDto(
    String id,
    String username,
    String email,
    String firstName,
    String lastName,
    String avatar,
    boolean enabled,
    boolean emailVerified,
    String createdTimestamp
) {}

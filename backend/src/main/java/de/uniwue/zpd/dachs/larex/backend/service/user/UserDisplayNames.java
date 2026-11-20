package de.uniwue.zpd.dachs.larex.backend.service.user;

import de.uniwue.zpd.dachs.larex.backend.dto.UserDto;

public final class UserDisplayNames {

    private UserDisplayNames() {
    }

    public static String of(UserDto user, String fallback) {
        if (user == null) {
            return fallback;
        }
        String fullName = (trim(user.firstName()) + " " + trim(user.lastName())).trim();
        if (!fullName.isEmpty()) {
            return fullName;
        }
        String username = trim(user.username());
        return username.isEmpty() ? fallback : username;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}

package de.uniwue.zpd.dachs.larex.backend.service;

import de.uniwue.zpd.dachs.larex.backend.dto.AdminCreateUserRequest;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserDto;
import de.uniwue.zpd.dachs.larex.backend.dto.UserDto;
import de.uniwue.zpd.dachs.larex.backend.dto.UserProfileDto;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final Keycloak keycloakAdmin;
    private final String realm;
    private final String actionEmailClientId;
    private final String actionEmailRedirectUri;
    private final Integer actionEmailLifespanSeconds;

    public UserService(
            Keycloak keycloakAdmin,
            @Value("${keycloak.admin.realm:larex-dev}") String realm,
            @Value("${keycloak.admin.action-email.client-id:larex-frontend}") String actionEmailClientId,
            @Value("${keycloak.admin.action-email.redirect-uri:http://larex.localhost/auth/keycloak}") String actionEmailRedirectUri,
            @Value("${keycloak.admin.action-email.lifespan-seconds:43200}") Integer actionEmailLifespanSeconds) {
        this.keycloakAdmin = keycloakAdmin;
        this.realm = realm;
        this.actionEmailClientId = actionEmailClientId;
        this.actionEmailRedirectUri = actionEmailRedirectUri;
        this.actionEmailLifespanSeconds = actionEmailLifespanSeconds;
    }

    public List<UserDto> getAllUsers() {
        RealmResource realmResource = keycloakAdmin.realm(realm);
        List<UserRepresentation> users = realmResource.users().list();

        return users.stream()
                .map(this::mapToUserDto)
                .toList();
    }

    public List<AdminUserDto> getAllUsersForAdmin(boolean includeServiceAccounts) {
        RealmResource realmResource = keycloakAdmin.realm(realm);
        List<UserRepresentation> users = realmResource.users().list();

        return users.stream()
                .filter(user -> includeServiceAccounts || !isServiceAccount(user))
                .map(this::mapToAdminUserDto)
                .toList();
    }

    public AdminUserDto createUserForAdmin(AdminCreateUserRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String firstName = normalizeOptional(request.firstName());
        String lastName = normalizeOptional(request.lastName());

        RealmResource realmResource = keycloakAdmin.realm(realm);
        UsersResource usersResource = realmResource.users();

        ensureUserDoesNotExist(usersResource, username, email);

        UserRepresentation newUser = new UserRepresentation();
        newUser.setUsername(username);
        newUser.setEmail(email);
        newUser.setFirstName(firstName);
        newUser.setLastName(lastName);
        newUser.setEnabled(true);
        newUser.setEmailVerified(false);
        newUser.setRequiredActions(List.of("VERIFY_EMAIL", "UPDATE_PASSWORD"));

        String createdUserId;
        try (Response response = usersResource.create(newUser)) {
            int status = response.getStatus();
            if (status != Response.Status.CREATED.getStatusCode()) {
                if (status == Response.Status.CONFLICT.getStatusCode()) {
                    throw new IllegalArgumentException("A user with this username or email already exists.");
                }
                throw new IllegalArgumentException("Failed to create user in Keycloak (status " + status + ").");
            }
            createdUserId = CreatedResponseUtil.getCreatedId(response);
        }

        UserResource createdUserResource = usersResource.get(createdUserId);
        try {
            sendSetupActionsEmail(createdUserResource, List.of("VERIFY_EMAIL", "UPDATE_PASSWORD"));
        } catch (Exception e) {
            rollbackCreatedUser(createdUserResource, createdUserId);
            throw new IllegalArgumentException("Failed to send account setup email. User creation was rolled back.");
        }

        UserRepresentation createdUser = createdUserResource.toRepresentation();
        return mapToAdminUserDto(createdUser);
    }

    private AdminUserDto mapToAdminUserDto(UserRepresentation user) {
        String avatar = extractAvatarUrl(user);

        return new AdminUserDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                avatar,
                Boolean.TRUE.equals(user.isEnabled()),
                Boolean.TRUE.equals(user.isEmailVerified()),
                user.getCreatedTimestamp() != null
                    ? Instant.ofEpochMilli(user.getCreatedTimestamp()).toString()
                    : null
        );
    }

    /**
     * Get a single user by ID
     */
    public Optional<UserDto> getUserById(String userId) {
        try {
            RealmResource realmResource = keycloakAdmin.realm(realm);
            UserRepresentation user = realmResource.users().get(userId).toRepresentation();
            return Optional.of(mapToUserDto(user));
        } catch (Exception e) {
            // User not found or other error
            return Optional.empty();
        }
    }

    /**
     * Get multiple users by their IDs - optimized for batch lookups
     */
    public Map<String, UserDto> getUsersByIds(List<String> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }

        RealmResource realmResource = keycloakAdmin.realm(realm);
        return userIds.stream()
                .map(userId -> {
                    try {
                        UserRepresentation user = realmResource.users().get(userId).toRepresentation();
                        return mapToUserDto(user);
                    } catch (Exception e) {
                        // User not found, return null (will be filtered out)
                        return null;
                    }
                })
                .filter(user -> user != null)
                .collect(Collectors.toMap(UserDto::id, user -> user));
    }

    private UserDto mapToUserDto(UserRepresentation user) {
        String avatar = extractAvatarUrl(user);

        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                avatar
        );
    }

    private String extractAvatarUrl(UserRepresentation user) {
        // Check for avatar in user attributes (common attribute names)
        if (user.getAttributes() != null) {
            // Try different common attribute names for avatar
            String[] avatarAttributes = {"avatar", "picture", "profile_picture", "photo"};

            for (String attr : avatarAttributes) {
                List<String> values = user.getAttributes().get(attr);
                if (values != null && !values.isEmpty() && values.get(0) != null && !values.get(0).trim().isEmpty()) {
                    return values.get(0).trim();
                }
            }
        }

        return null;
    }

    /**
     * Get user profile (including avatar from attributes)
     */
    public Optional<UserProfileDto> getUserProfile(String userId) {
        try {
            RealmResource realmResource = keycloakAdmin.realm(realm);
            UserRepresentation user = realmResource.users().get(userId).toRepresentation();
            UserDto userDto = mapToUserDto(user);

            return Optional.of(new UserProfileDto(
                    userDto.id(),
                    userDto.username(),
                    userDto.email(),
                    userDto.firstName(),
                    userDto.lastName(),
                    userDto.avatar()
            ));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Update user profile (firstName, lastName, avatar only - no security-related fields)
     */
    public boolean updateUserProfile(String userId, UserProfileDto.UpdateRequest updateRequest) {
        try {
            RealmResource realmResource = keycloakAdmin.realm(realm);
            UserResource userResource = realmResource.users().get(userId);
            UserRepresentation user = userResource.toRepresentation();

            // Update basic profile fields
            if (updateRequest.firstName() != null) {
                user.setFirstName(updateRequest.firstName().trim());
            }
            if (updateRequest.lastName() != null) {
                user.setLastName(updateRequest.lastName().trim());
            }

            // Update avatar in user attributes
            if (updateRequest.avatar() != null) {
                Map<String, List<String>> attributes = user.getAttributes();
                if (attributes == null) {
                    attributes = new HashMap<>();
                }

                if (updateRequest.avatar().trim().isEmpty()) {
                    // Remove avatar if empty string provided
                    attributes.remove("picture");
                } else {
                    // Set avatar URL
                    attributes.put("picture", Arrays.asList(updateRequest.avatar().trim()));
                }
                user.setAttributes(attributes);
            }

            // Save the updated user
            userResource.update(user);
            return true;

        } catch (Exception e) {
            logger.error("Failed to update user profile for userId: {}, error: {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * Get user IDs by usernames (for @mention resolution)
     */
    public List<String> getUserIdsByUsernames(List<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return List.of();
        }

        RealmResource realmResource = keycloakAdmin.realm(realm);
        return usernames.stream()
                .map(username -> {
                    try {
                        List<UserRepresentation> users = realmResource.users().searchByUsername(username, true);
                        return users.stream()
                                .filter(u -> u.getUsername().equalsIgnoreCase(username))
                                .findFirst()
                                .map(UserRepresentation::getId)
                                .orElse(null);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(id -> id != null)
                .toList();
    }

    /**
     * Search users by username or email
     */
    public List<UserDto> searchUsers(String query, int limit) {
        if (query == null || query.trim().length() < 2) {
            return List.of();
        }

        RealmResource realmResource = keycloakAdmin.realm(realm);
        String searchTerm = query.trim();

        // Search by username
        List<UserRepresentation> usernameResults = realmResource.users().searchByUsername(searchTerm, true);
        
        // Search by email
        List<UserRepresentation> emailResults = realmResource.users().searchByEmail(searchTerm, true);

        // Also search with broader matching
        List<UserRepresentation> broadResults = realmResource.users().search(searchTerm, 0, limit);

        // Combine and deduplicate
        Map<String, UserDto> uniqueUsers = new HashMap<>();
        
        for (UserRepresentation user : usernameResults) {
            uniqueUsers.putIfAbsent(user.getId(), mapToUserDto(user));
        }
        for (UserRepresentation user : emailResults) {
            uniqueUsers.putIfAbsent(user.getId(), mapToUserDto(user));
        }
        for (UserRepresentation user : broadResults) {
            uniqueUsers.putIfAbsent(user.getId(), mapToUserDto(user));
        }

        return uniqueUsers.values().stream()
                .limit(limit)
                .toList();
    }

    private void ensureUserDoesNotExist(UsersResource usersResource, String username, String email) {
        boolean usernameTaken = usersResource.searchByUsername(username, true).stream()
                .map(UserRepresentation::getUsername)
                .filter(existingUsername -> existingUsername != null)
                .anyMatch(existingUsername -> existingUsername.equalsIgnoreCase(username));
        if (usernameTaken) {
            throw new IllegalArgumentException("A user with this username already exists.");
        }

        boolean emailTaken = usersResource.searchByEmail(email, true).stream()
                .map(UserRepresentation::getEmail)
                .filter(existingEmail -> existingEmail != null)
                .anyMatch(existingEmail -> existingEmail.equalsIgnoreCase(email));
        if (emailTaken) {
            throw new IllegalArgumentException("A user with this email already exists.");
        }
    }

    private void rollbackCreatedUser(UserResource createdUserResource, String userId) {
        try {
            createdUserResource.remove();
        } catch (Exception deleteException) {
            logger.error("Failed to roll back user {} after action-email failure: {}", userId, deleteException.getMessage(), deleteException);
        }
    }

    private void sendSetupActionsEmail(UserResource userResource, List<String> actions) {
        String clientId = normalizeOptional(actionEmailClientId);
        String redirectUri = normalizeOptional(actionEmailRedirectUri);

        if (clientId != null && redirectUri != null) {
            userResource.executeActionsEmail(clientId, redirectUri, actionEmailLifespanSeconds, actions);
            return;
        }

        if (clientId == null ^ redirectUri == null) {
            logger.warn("Incomplete action-email redirect configuration. Falling back to default execute-actions-email flow.");
        }

        if (actionEmailLifespanSeconds != null) {
            userResource.executeActionsEmail(actions, actionEmailLifespanSeconds);
        } else {
            userResource.executeActionsEmail(actions);
        }
    }

    private boolean isServiceAccount(UserRepresentation user) {
        if (user.getServiceAccountClientId() != null && !user.getServiceAccountClientId().isBlank()) {
            return true;
        }
        String username = user.getUsername();
        return username != null && username.startsWith("service-account-");
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

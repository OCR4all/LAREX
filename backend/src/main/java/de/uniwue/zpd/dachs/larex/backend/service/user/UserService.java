package de.uniwue.zpd.dachs.larex.backend.service.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.uniwue.zpd.dachs.larex.backend.config.auth.AuthProvisioningProperties;
import de.uniwue.zpd.dachs.larex.backend.config.auth.UserProvisioningMode;
import de.uniwue.zpd.dachs.larex.backend.config.security.KeycloakAdminProperties;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminCreateUserRequest;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminGlobalRolesDto;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserAuditAction;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserAuditEventDto;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserAuditOutcome;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserDto;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserIdentitySource;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserOnboardingState;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserPageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserStatusFilter;
import de.uniwue.zpd.dachs.larex.backend.dto.UserDto;
import de.uniwue.zpd.dachs.larex.backend.dto.UserProfileDto;
import de.uniwue.zpd.dachs.larex.backend.exception.AdminUserErrorCode;
import de.uniwue.zpd.dachs.larex.backend.exception.AdminUserManagementException;
import de.uniwue.zpd.dachs.larex.backend.service.admin.AdminUserAuditService;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private static final List<String> SETUP_ACTIONS = List.of("VERIFY_EMAIL", "UPDATE_PASSWORD");
    private static final String SERVICE_ACCOUNT_USERNAME_PREFIX = "service-account-";
    private static final String GLOBAL_ADMIN_ROLE = "GLOBAL_ADMIN";
    private static final String GLOBAL_CURATOR_ROLE = "GLOBAL_CURATOR";
    private static final int IDENTITY_PROVIDER_ERROR_DETAIL_MAX_LENGTH = 240;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Keycloak keycloakAdmin;
    private final String realm;
    private final String actionEmailClientId;
    private final String actionEmailRedirectUri;
    private final Integer actionEmailLifespanSeconds;
    private final AdminUserAuditService adminUserAuditService;
    private final AuthProvisioningProperties authProvisioningProperties;

    public UserService(
            Keycloak keycloakAdmin,
            KeycloakAdminProperties keycloakAdminProperties,
            AdminUserAuditService adminUserAuditService,
            AuthProvisioningProperties authProvisioningProperties) {
        KeycloakAdminProperties.ActionEmail actionEmail = keycloakAdminProperties.actionEmail();

        this.keycloakAdmin = keycloakAdmin;
        this.realm = keycloakAdminProperties.realm();
        this.actionEmailClientId = actionEmail.clientId();
        this.actionEmailRedirectUri = actionEmail.redirectUri();
        this.actionEmailLifespanSeconds = actionEmail.lifespanSeconds();
        this.adminUserAuditService = adminUserAuditService;
        this.authProvisioningProperties = authProvisioningProperties;
    }

    private enum AdminMutationAction {
        ENABLE,
        DISABLE,
        RESEND_SETUP
    }

    public List<UserDto> getAllUsers() {
        RealmResource realmResource = keycloakAdmin.realm(realm);
        List<UserRepresentation> users = realmResource.users().list();

        return users.stream()
                .map(this::mapToUserDto)
                .toList();
    }

    public AdminUserPageDto getUserPageForAdmin(int page, int size, String search, boolean includeServiceAccounts, AdminUserStatusFilter status) {
        List<UserRepresentation> users = keycloakAdmin.realm(realm).users().list();
        String normalizedSearch = normalizeOptional(search);

        List<AdminUserDto> filteredUsers = users.stream()
                .filter(user -> includeServiceAccounts || !isServiceAccount(user))
                .sorted(this::compareByCreatedTimestampDesc)
                .map(this::mapToAdminUserDto)
                .filter(user -> matchesSearch(user, normalizedSearch))
                .filter(user -> matchesStatus(user, status))
                .toList();

        long totalElements = filteredUsers.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        int fromIndex = Math.min(page * size, filteredUsers.size());
        int toIndex = Math.min(fromIndex + size, filteredUsers.size());

        return new AdminUserPageDto(
                filteredUsers.subList(fromIndex, toIndex),
                page,
                size,
                totalElements,
                totalPages,
                !isLdapManagedDeployment(),
                !isLdapManagedDeployment()
        );
    }

    public AdminUserDto getUserForAdmin(String userId) {
        return mapToAdminUserDto(getRequiredUserRepresentation(userId));
    }

    public List<AdminUserAuditEventDto> getUserAuditEventsForAdmin(String targetUserId, int limit) {
        getRequiredUserRepresentation(targetUserId);
        return adminUserAuditService.getAuditEvents(targetUserId, limit);
    }

    public AdminGlobalRolesDto getGlobalRolesForAdmin(String targetUserId) {
        UserRepresentation user = getRequiredUserRepresentation(targetUserId);
        return readGlobalRoles(user);
    }

    public AdminGlobalRolesDto grantGlobalCuratorForAdmin(String actorUserId, String actorUsername, String targetUserId, String reason) {
        return mutateGlobalCuratorRole(actorUserId, actorUsername, targetUserId, reason, true);
    }

    public AdminGlobalRolesDto revokeGlobalCuratorForAdmin(String actorUserId, String actorUsername, String targetUserId, String reason) {
        return mutateGlobalCuratorRole(actorUserId, actorUsername, targetUserId, reason, false);
    }

    public AdminUserDto createUserForAdmin(String actorUserId, String actorUsername, AdminCreateUserRequest request) {
        String rawUsername = normalizeOptional(request.username());

        try {
            assertProvisioningEnabledForCreate();
        } catch (AdminUserManagementException ex) {
            adminUserAuditService.logEvent(
                    actorUserId,
                    actorUsername,
                    null,
                    rawUsername,
                    AdminUserAuditAction.CREATE,
                    AdminUserAuditOutcome.FAILURE,
                    auditDetails(null, null, ex.getCode(), null)
            );
            throw ex;
        }

        String username;
        String email;

        try {
            username = normalizeRequiredUsername(request.username());
            email = normalizeRequiredEmail(request.email());
        } catch (AdminUserManagementException ex) {
            adminUserAuditService.logEvent(
                    actorUserId,
                    actorUsername,
                    null,
                    rawUsername,
                    AdminUserAuditAction.CREATE,
                    AdminUserAuditOutcome.FAILURE,
                    auditDetails(null, null, ex.getCode(), null)
            );
            throw ex;
        }

        String firstName = normalizeOptional(request.firstName());
        String lastName = normalizeOptional(request.lastName());

        UsersResource usersResource = keycloakAdmin.realm(realm).users();

        try {
            ensureUserDoesNotExist(usersResource, username, email);
        } catch (AdminUserManagementException ex) {
            adminUserAuditService.logEvent(
                    actorUserId,
                    actorUsername,
                    null,
                    username,
                    AdminUserAuditAction.CREATE,
                    AdminUserAuditOutcome.FAILURE,
                    auditDetails(null, null, ex.getCode(), identitySourceDetails(AdminUserIdentitySource.LOCAL))
            );
            throw ex;
        }

        UserRepresentation newUser = new UserRepresentation();
        newUser.setUsername(username);
        newUser.setEmail(email);
        newUser.setFirstName(firstName);
        newUser.setLastName(lastName);
        newUser.setEnabled(true);
        newUser.setEmailVerified(false);
        newUser.setRequiredActions(SETUP_ACTIONS);

        String createdUserId;
        try (Response response = usersResource.create(newUser)) {
            int status = response.getStatus();
            if (status != Response.Status.CREATED.getStatusCode()) {
                String providerErrorDetail = extractIdentityProviderErrorDetail(response);
                AdminUserErrorCode errorCode = switch (status) {
                    case 400 -> AdminUserErrorCode.ADMIN_USER_IDENTITY_PROVIDER_VALIDATION_FAILED;
                    case 409 -> AdminUserErrorCode.ADMIN_USER_IDENTITY_PROVIDER_CONFLICT;
                    default -> AdminUserErrorCode.ADMIN_USER_KEYCLOAK_OPERATION_FAILED;
                };
                AdminUserManagementException ex = new AdminUserManagementException(
                        errorCode,
                        status == Response.Status.CONFLICT.getStatusCode()
                                ? "Failed to create user because the identity provider reported a conflict."
                                : buildIdentityProviderCreateFailureMessage(status, providerErrorDetail)
                );
                adminUserAuditService.logEvent(
                        actorUserId,
                        actorUsername,
                        null,
                        username,
                        AdminUserAuditAction.CREATE,
                        AdminUserAuditOutcome.FAILURE,
                        auditDetails(null, null, ex.getCode(), identitySourceDetails(AdminUserIdentitySource.LOCAL))
                );
                throw ex;
            }
            createdUserId = resolveCreatedUserId(response, usersResource, username, email);
        } catch (AdminUserManagementException ex) {
            throw ex;
        } catch (Exception ex) {
            adminUserAuditService.logEvent(
                    actorUserId,
                    actorUsername,
                    null,
                    username,
                    AdminUserAuditAction.CREATE,
                    AdminUserAuditOutcome.FAILURE,
                    auditDetails(null, null, AdminUserErrorCode.ADMIN_USER_KEYCLOAK_OPERATION_FAILED, identitySourceDetails(AdminUserIdentitySource.LOCAL))
            );
            throw new AdminUserManagementException(
                    AdminUserErrorCode.ADMIN_USER_KEYCLOAK_OPERATION_FAILED,
                    "Failed to create user in the identity provider.",
                    ex
            );
        }

        UserResource createdUserResource = usersResource.get(createdUserId);
        try {
            sendSetupActionsEmail(createdUserResource, SETUP_ACTIONS);
            UserRepresentation createdUser = createdUserResource.toRepresentation();
            AdminUserDto createdDto = mapToAdminUserDto(createdUser);
            adminUserAuditService.logEvent(
                    actorUserId,
                    actorUsername,
                    createdUserId,
                    username,
                    AdminUserAuditAction.CREATE,
                    AdminUserAuditOutcome.SUCCESS,
                    auditDetails(null, createdDto.onboardingState(), null, identitySourceDetails(createdDto.identitySource()))
            );
            return createdDto;
        } catch (Exception ex) {
            rollbackCreatedUser(createdUserResource, createdUserId);
            adminUserAuditService.logEvent(
                    actorUserId,
                    actorUsername,
                    createdUserId,
                    username,
                    AdminUserAuditAction.CREATE,
                    AdminUserAuditOutcome.FAILURE,
                    auditDetails(null, null, AdminUserErrorCode.ADMIN_USER_SETUP_EMAIL_FAILED, identitySourceDetails(AdminUserIdentitySource.LOCAL))
            );
            throw new AdminUserManagementException(
                    AdminUserErrorCode.ADMIN_USER_SETUP_EMAIL_FAILED,
                    "Failed to send account setup email. User creation was rolled back.",
                    ex
            );
        }
    }

    public AdminUserDto disableUserForAdmin(String actorUserId, String actorUsername, String targetUserId) {
        return updateUserEnabledStateForAdmin(actorUserId, actorUsername, targetUserId, false);
    }

    public AdminUserDto enableUserForAdmin(String actorUserId, String actorUsername, String targetUserId) {
        return updateUserEnabledStateForAdmin(actorUserId, actorUsername, targetUserId, true);
    }

    public AdminUserDto resendSetupEmailForAdmin(String actorUserId, String actorUsername, String targetUserId) {
        UserRepresentation user;
        try {
            user = getRequiredUserRepresentation(targetUserId);
        } catch (AdminUserManagementException ex) {
            adminUserAuditService.logEvent(
                    actorUserId,
                    actorUsername,
                    targetUserId,
                    null,
                    AdminUserAuditAction.RESEND_SETUP_EMAIL,
                    AdminUserAuditOutcome.FAILURE,
                    auditDetails(null, null, ex.getCode(), null)
            );
            throw ex;
        }

        AdminUserOnboardingState previousState = deriveOnboardingState(user);
        AdminUserIdentitySource identitySource = deriveIdentitySource(user);

        try {
            assertNotServiceAccount(user);
            assertMutableInCurrentMode(user, AdminMutationAction.RESEND_SETUP);
            if (user.getEmail() == null || user.getEmail().isBlank() || previousState != AdminUserOnboardingState.PENDING_SETUP) {
                throw new AdminUserManagementException(
                        AdminUserErrorCode.ADMIN_USER_RESEND_NOT_ALLOWED,
                        "Setup email can only be resent for users who are still completing onboarding."
                );
            }

            UserResource userResource = keycloakAdmin.realm(realm).users().get(targetUserId);
            sendSetupActionsEmail(userResource, SETUP_ACTIONS);
            UserRepresentation refreshedUser = userResource.toRepresentation();
            AdminUserDto updatedDto = mapToAdminUserDto(refreshedUser);

            adminUserAuditService.logEvent(
                    actorUserId,
                    actorUsername,
                    targetUserId,
                    refreshedUser.getUsername(),
                    AdminUserAuditAction.RESEND_SETUP_EMAIL,
                    AdminUserAuditOutcome.SUCCESS,
                    auditDetails(previousState, updatedDto.onboardingState(), null, identitySourceDetails(updatedDto.identitySource()))
            );

            return updatedDto;
        } catch (AdminUserManagementException ex) {
            adminUserAuditService.logEvent(
                    actorUserId,
                    actorUsername,
                    targetUserId,
                    user.getUsername(),
                    AdminUserAuditAction.RESEND_SETUP_EMAIL,
                    AdminUserAuditOutcome.FAILURE,
                    auditDetails(previousState, previousState, ex.getCode(), identitySourceDetails(identitySource))
            );
            throw ex;
        } catch (Exception ex) {
            adminUserAuditService.logEvent(
                    actorUserId,
                    actorUsername,
                    targetUserId,
                    user.getUsername(),
                    AdminUserAuditAction.RESEND_SETUP_EMAIL,
                    AdminUserAuditOutcome.FAILURE,
                    auditDetails(previousState, previousState, AdminUserErrorCode.ADMIN_USER_SETUP_EMAIL_FAILED, identitySourceDetails(identitySource))
            );
            throw new AdminUserManagementException(
                    AdminUserErrorCode.ADMIN_USER_SETUP_EMAIL_FAILED,
                    "Failed to send account setup email.",
                    ex
            );
        }
    }

    public Optional<UserDto> getUserById(String userId) {
        try {
            RealmResource realmResource = keycloakAdmin.realm(realm);
            UserRepresentation user = realmResource.users().get(userId).toRepresentation();
            return Optional.of(mapToUserDto(user));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

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
                        return null;
                    }
                })
                .filter(user -> user != null)
                .collect(Collectors.toMap(UserDto::id, user -> user));
    }

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
        } catch (NotFoundException e) {
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Failed to load user profile for userId: {}, error: {}", userId, e.getMessage(), e);
            throw new IllegalStateException("Failed to load user profile", e);
        }
    }

    public boolean updateUserProfile(String userId, UserProfileDto.UpdateRequest updateRequest) {
        try {
            RealmResource realmResource = keycloakAdmin.realm(realm);
            UserResource userResource = realmResource.users().get(userId);
            UserRepresentation user = userResource.toRepresentation();

            if (updateRequest.firstName() != null) {
                user.setFirstName(updateRequest.firstName().trim());
            }
            if (updateRequest.lastName() != null) {
                user.setLastName(updateRequest.lastName().trim());
            }

            if (updateRequest.avatar() != null) {
                Map<String, List<String>> attributes = user.getAttributes();
                if (attributes == null) {
                    attributes = new HashMap<>();
                }

                if (updateRequest.avatar().trim().isEmpty()) {
                    attributes.remove("picture");
                } else {
                    attributes.put("picture", Arrays.asList(updateRequest.avatar().trim()));
                }
                user.setAttributes(attributes);
            }

            userResource.update(user);
            return true;

        } catch (Exception e) {
            logger.error("Failed to update user profile for userId: {}, error: {}", userId, e.getMessage());
            return false;
        }
    }

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

    public List<UserDto> searchUsers(String query, int limit) {
        if (query == null || query.trim().length() < 2) {
            return List.of();
        }

        RealmResource realmResource = keycloakAdmin.realm(realm);
        String searchTerm = query.trim();

        List<UserRepresentation> usernameResults = realmResource.users().searchByUsername(searchTerm, true);
        List<UserRepresentation> emailResults = realmResource.users().searchByEmail(searchTerm, true);
        List<UserRepresentation> broadResults = realmResource.users().search(searchTerm, 0, limit);

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

    private AdminUserDto updateUserEnabledStateForAdmin(String actorUserId, String actorUsername, String targetUserId, boolean enabled) {
        AdminUserAuditAction action = enabled ? AdminUserAuditAction.ENABLE : AdminUserAuditAction.DISABLE;
        AdminMutationAction mutationAction = enabled ? AdminMutationAction.ENABLE : AdminMutationAction.DISABLE;
        UserRepresentation user;

        try {
            user = getRequiredUserRepresentation(targetUserId);
        } catch (AdminUserManagementException ex) {
            adminUserAuditService.logEvent(
                    actorUserId,
                    actorUsername,
                    targetUserId,
                    null,
                    action,
                    AdminUserAuditOutcome.FAILURE,
                    auditDetails(null, null, ex.getCode(), null)
            );
            throw ex;
        }

        AdminUserOnboardingState previousState = deriveOnboardingState(user);
        AdminUserIdentitySource identitySource = deriveIdentitySource(user);

        try {
            assertNotServiceAccount(user);
            assertMutableInCurrentMode(user, mutationAction);
            if (!enabled && targetUserId.equals(actorUserId)) {
                throw new AdminUserManagementException(
                        AdminUserErrorCode.ADMIN_USER_SELF_DISABLE_FORBIDDEN,
                        "You cannot disable your own account."
                );
            }
            if (enabled && Boolean.TRUE.equals(user.isEnabled())) {
                throw new AdminUserManagementException(
                        AdminUserErrorCode.ADMIN_USER_ALREADY_ENABLED,
                        "User is already enabled."
                );
            }
            if (!enabled && !Boolean.TRUE.equals(user.isEnabled())) {
                throw new AdminUserManagementException(
                        AdminUserErrorCode.ADMIN_USER_ALREADY_DISABLED,
                        "User is already disabled."
                );
            }

            UserResource userResource = keycloakAdmin.realm(realm).users().get(targetUserId);
            user.setEnabled(enabled);
            userResource.update(user);

            UserRepresentation refreshedUser = userResource.toRepresentation();
            AdminUserDto updatedDto = mapToAdminUserDto(refreshedUser);
            adminUserAuditService.logEvent(
                    actorUserId,
                    actorUsername,
                    targetUserId,
                    refreshedUser.getUsername(),
                    action,
                    AdminUserAuditOutcome.SUCCESS,
                    auditDetails(previousState, updatedDto.onboardingState(), null, identitySourceDetails(updatedDto.identitySource()))
            );

            return updatedDto;
        } catch (AdminUserManagementException ex) {
            adminUserAuditService.logEvent(
                    actorUserId,
                    actorUsername,
                    targetUserId,
                    user.getUsername(),
                    action,
                    AdminUserAuditOutcome.FAILURE,
                    auditDetails(previousState, previousState, ex.getCode(), identitySourceDetails(identitySource))
            );
            throw ex;
        } catch (Exception ex) {
            adminUserAuditService.logEvent(
                    actorUserId,
                    actorUsername,
                    targetUserId,
                    user.getUsername(),
                    action,
                    AdminUserAuditOutcome.FAILURE,
                    auditDetails(previousState, previousState, AdminUserErrorCode.ADMIN_USER_KEYCLOAK_OPERATION_FAILED, identitySourceDetails(identitySource))
            );
            throw new AdminUserManagementException(
                    AdminUserErrorCode.ADMIN_USER_KEYCLOAK_OPERATION_FAILED,
                    "Failed to update the user in the identity provider.",
                    ex
            );
        }
    }

    private void ensureUserDoesNotExist(UsersResource usersResource, String username, String email) {
        boolean usernameTaken = usersResource.searchByUsername(username, true).stream()
                .map(UserRepresentation::getUsername)
                .filter(existingUsername -> existingUsername != null)
                .anyMatch(existingUsername -> existingUsername.equalsIgnoreCase(username));
        if (usernameTaken) {
            throw new AdminUserManagementException(
                    AdminUserErrorCode.ADMIN_USER_DUPLICATE_USERNAME,
                    "A user with this username already exists."
            );
        }

        boolean emailTaken = usersResource.searchByEmail(email, true).stream()
                .map(UserRepresentation::getEmail)
                .filter(existingEmail -> existingEmail != null)
                .anyMatch(existingEmail -> existingEmail.equalsIgnoreCase(email));
        if (emailTaken) {
            throw new AdminUserManagementException(
                    AdminUserErrorCode.ADMIN_USER_DUPLICATE_EMAIL,
                    "A user with this email already exists."
            );
        }
    }

    private AdminGlobalRolesDto mutateGlobalCuratorRole(
            String actorUserId,
            String actorUsername,
            String targetUserId,
            String reason,
            boolean grant) {
        String normalizedReason = normalizeOptional(reason);
        if (normalizedReason == null) {
            throw new AdminUserManagementException(
                    AdminUserErrorCode.ADMIN_USER_KEYCLOAK_OPERATION_FAILED,
                    "Reason is required."
            );
        }

        AdminUserAuditAction action = grant
                ? AdminUserAuditAction.GLOBAL_CURATOR_GRANT
                : AdminUserAuditAction.GLOBAL_CURATOR_REVOKE;

        UserRepresentation user;
        try {
            user = getRequiredUserRepresentation(targetUserId);
            assertNotServiceAccount(user);
        } catch (AdminUserManagementException ex) {
            adminUserAuditService.logEvent(
                    actorUserId,
                    actorUsername,
                    targetUserId,
                    null,
                    action,
                    AdminUserAuditOutcome.FAILURE,
                    Map.of(
                            "errorCode", ex.getCode().name(),
                            "reason", normalizedReason
                    )
            );
            throw ex;
        }

        AdminGlobalRolesDto previousRoles = readGlobalRoles(user);
        boolean needsMutation = grant ? !previousRoles.globalCurator() : previousRoles.globalCurator();

        try {
            UserResource userResource = keycloakAdmin.realm(realm).users().get(targetUserId);
            if (needsMutation) {
                RoleRepresentation curatorRole = resolveGlobalCuratorRealmRole();

                if (grant) {
                    userResource.roles().realmLevel().add(List.of(curatorRole));
                } else {
                    userResource.roles().realmLevel().remove(List.of(curatorRole));
                }
            }

            AdminGlobalRolesDto updatedRoles = readGlobalRoles(userResource.toRepresentation());

            adminUserAuditService.logEvent(
                    actorUserId,
                    actorUsername,
                    targetUserId,
                    user.getUsername(),
                    action,
                    AdminUserAuditOutcome.SUCCESS,
                    Map.of(
                            "reason", normalizedReason,
                            "idempotent", !needsMutation,
                            "previousGlobalAdmin", previousRoles.globalAdmin(),
                            "previousGlobalCurator", previousRoles.globalCurator(),
                            "newGlobalAdmin", updatedRoles.globalAdmin(),
                            "newGlobalCurator", updatedRoles.globalCurator()
                    )
            );

            return updatedRoles;
        } catch (AdminUserManagementException ex) {
            adminUserAuditService.logEvent(
                    actorUserId,
                    actorUsername,
                    targetUserId,
                    user.getUsername(),
                    action,
                    AdminUserAuditOutcome.FAILURE,
                    Map.of(
                            "reason", normalizedReason,
                            "errorCode", ex.getCode().name(),
                            "previousGlobalAdmin", previousRoles.globalAdmin(),
                            "previousGlobalCurator", previousRoles.globalCurator()
                    )
            );
            logger.warn("Global curator role update rejected (admin exception): actor={}, target={}, grant={}, reason={}",
                    actorUsername, user.getUsername(), grant, normalizedReason, ex);
            throw ex;
        } catch (Exception ex) {
            adminUserAuditService.logEvent(
                    actorUserId,
                    actorUsername,
                    targetUserId,
                    user.getUsername(),
                    action,
                    AdminUserAuditOutcome.FAILURE,
                    Map.of(
                            "reason", normalizedReason,
                            "errorCode", AdminUserErrorCode.ADMIN_USER_KEYCLOAK_OPERATION_FAILED.name(),
                            "previousGlobalAdmin", previousRoles.globalAdmin(),
                            "previousGlobalCurator", previousRoles.globalCurator()
                    )
            );
            logger.error("Global curator role update failed: actor={}, target={}, grant={}, reason={}",
                    actorUsername, user.getUsername(), grant, normalizedReason, ex);
            throw new AdminUserManagementException(
                    AdminUserErrorCode.ADMIN_USER_KEYCLOAK_OPERATION_FAILED,
                    "Failed to update global curator role mapping.",
                    ex
            );
        }
    }

    private RoleRepresentation resolveGlobalCuratorRealmRole() {
        try {
            return keycloakAdmin.realm(realm)
                    .roles()
                    .get(GLOBAL_CURATOR_ROLE)
                    .toRepresentation();
        } catch (NotFoundException ex) {
            throw new AdminUserManagementException(
                    AdminUserErrorCode.ADMIN_USER_KEYCLOAK_OPERATION_FAILED,
                    "Keycloak realm role 'GLOBAL_CURATOR' is missing. Create it before granting or revoking global curator access.",
                    ex
            );
        } catch (Exception ex) {
            throw new AdminUserManagementException(
                    AdminUserErrorCode.ADMIN_USER_KEYCLOAK_OPERATION_FAILED,
                    "Failed to resolve Keycloak realm role 'GLOBAL_CURATOR'. Check admin client permissions for role mappings.",
                    ex
            );
        }
    }

    private AdminGlobalRolesDto readGlobalRoles(UserRepresentation user) {
        List<RoleRepresentation> realmRoles = keycloakAdmin.realm(realm)
                .users()
                .get(user.getId())
                .roles()
                .realmLevel()
                .listEffective();

        java.util.Set<String> roleNames = realmRoles.stream()
                .map(RoleRepresentation::getName)
                .collect(java.util.stream.Collectors.toSet());

        return new AdminGlobalRolesDto(
                roleNames.contains(GLOBAL_ADMIN_ROLE),
                roleNames.contains(GLOBAL_CURATOR_ROLE)
        );
    }

    private UserRepresentation getRequiredUserRepresentation(String userId) {
        try {
            return keycloakAdmin.realm(realm).users().get(userId).toRepresentation();
        } catch (NotFoundException ex) {
            throw new AdminUserManagementException(
                    AdminUserErrorCode.ADMIN_USER_NOT_FOUND,
                    "User not found.",
                    ex
            );
        } catch (AdminUserManagementException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AdminUserManagementException(
                    AdminUserErrorCode.ADMIN_USER_NOT_FOUND,
                    "User not found.",
                    ex
            );
        }
    }

    private void assertProvisioningEnabledForCreate() {
        if (isLdapManagedDeployment()) {
            throw new AdminUserManagementException(
                    AdminUserErrorCode.ADMIN_USER_PROVISIONING_DISABLED,
                    "User creation is disabled because this deployment uses LDAP-managed identities."
            );
        }
    }

    private void assertMutableInCurrentMode(UserRepresentation user, AdminMutationAction action) {
        if (isLdapManagedDeployment()) {
            if (action == AdminMutationAction.RESEND_SETUP) {
                throw new AdminUserManagementException(
                        AdminUserErrorCode.ADMIN_USER_PROVISIONING_DISABLED,
                        "Setup email actions are disabled because this deployment uses LDAP-managed identities."
                );
            }
            throw new AdminUserManagementException(
                    AdminUserErrorCode.ADMIN_USER_EXTERNALLY_MANAGED,
                    "This user is managed externally through LDAP and cannot be changed here."
            );
        }

        if (isExternallyManaged(user)) {
            throw new AdminUserManagementException(
                    AdminUserErrorCode.ADMIN_USER_EXTERNALLY_MANAGED,
                    "This user is managed externally through LDAP and cannot be changed here."
            );
        }
    }

    private AdminUserDto mapToAdminUserDto(UserRepresentation user) {
        String avatar = extractAvatarUrl(user);
        boolean serviceAccount = isServiceAccount(user);
        AdminUserIdentitySource identitySource = deriveIdentitySource(user);
        boolean externallyManaged = identitySource != AdminUserIdentitySource.LOCAL;
        AdminUserOnboardingState onboardingState = deriveOnboardingState(user);

        return new AdminUserDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                avatar,
                Boolean.TRUE.equals(user.isEnabled()),
                Boolean.TRUE.equals(user.isEmailVerified()),
                serviceAccount,
                externallyManaged,
                identitySource,
                onboardingState,
                user.getCreatedTimestamp() != null
                        ? Instant.ofEpochMilli(user.getCreatedTimestamp()).toString()
                        : null
        );
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

    private AdminUserOnboardingState deriveOnboardingState(UserRepresentation user) {
        if (isServiceAccount(user)) {
            return AdminUserOnboardingState.SERVICE_ACCOUNT;
        }
        if (!Boolean.TRUE.equals(user.isEnabled())) {
            return AdminUserOnboardingState.DISABLED;
        }

        List<String> requiredActions = user.getRequiredActions() != null ? user.getRequiredActions() : List.of();
        boolean pendingSetup = !Boolean.TRUE.equals(user.isEmailVerified())
                || requiredActions.contains("VERIFY_EMAIL")
                || requiredActions.contains("UPDATE_PASSWORD");

        return pendingSetup ? AdminUserOnboardingState.PENDING_SETUP : AdminUserOnboardingState.ACTIVE;
    }

    private AdminUserIdentitySource deriveIdentitySource(UserRepresentation user) {
        if (isServiceAccount(user)) {
            return AdminUserIdentitySource.SERVICE_ACCOUNT;
        }
        String federationLink = normalizeOptional(user.getFederationLink());
        if (federationLink != null) {
            return AdminUserIdentitySource.LDAP;
        }
        return AdminUserIdentitySource.LOCAL;
    }

    private boolean isExternallyManaged(UserRepresentation user) {
        return deriveIdentitySource(user) != AdminUserIdentitySource.LOCAL;
    }

    private boolean isLdapManagedDeployment() {
        return authProvisioningProperties.getUserProvisioningMode() == UserProvisioningMode.LDAP_MANAGED;
    }

    private boolean matchesSearch(AdminUserDto user, String normalizedSearch) {
        if (normalizedSearch == null) {
            return true;
        }

        String query = normalizedSearch.toLowerCase(Locale.ROOT);
        return containsIgnoreCase(user.username(), query)
                || containsIgnoreCase(user.email(), query)
                || containsIgnoreCase(user.firstName(), query)
                || containsIgnoreCase(user.lastName(), query);
    }

    private boolean matchesStatus(AdminUserDto user, AdminUserStatusFilter status) {
        if (status == null || status == AdminUserStatusFilter.ALL) {
            return true;
        }
        return switch (status) {
            case ACTIVE -> user.onboardingState() == AdminUserOnboardingState.ACTIVE;
            case PENDING_SETUP -> user.onboardingState() == AdminUserOnboardingState.PENDING_SETUP;
            case DISABLED -> user.onboardingState() == AdminUserOnboardingState.DISABLED;
            case ALL -> true;
        };
    }

    private boolean containsIgnoreCase(String value, String normalizedQuery) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    private int compareByCreatedTimestampDesc(UserRepresentation left, UserRepresentation right) {
        long rightCreated = right.getCreatedTimestamp() != null ? right.getCreatedTimestamp() : Long.MIN_VALUE;
        long leftCreated = left.getCreatedTimestamp() != null ? left.getCreatedTimestamp() : Long.MIN_VALUE;
        return Long.compare(rightCreated, leftCreated);
    }

    private Map<String, Object> auditDetails(
            AdminUserOnboardingState previousState,
            AdminUserOnboardingState newState,
            AdminUserErrorCode errorCode,
            Map<String, Object> extraDetails) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (previousState != null) {
            details.put("previousState", previousState.name());
        }
        if (newState != null) {
            details.put("newState", newState.name());
        }
        if (errorCode != null) {
            details.put("errorCode", errorCode.name());
        }
        if (extraDetails != null) {
            extraDetails.forEach((key, value) -> {
                if (value != null) {
                    details.put(key, value);
                }
            });
        }
        return details;
    }

    private Map<String, Object> identitySourceDetails(AdminUserIdentitySource identitySource) {
        if (identitySource == null) {
            return null;
        }
        return Map.of("identitySource", identitySource.name());
    }

    private void assertNotServiceAccount(UserRepresentation user) {
        if (isServiceAccount(user)) {
            throw new AdminUserManagementException(
                    AdminUserErrorCode.ADMIN_USER_SERVICE_ACCOUNT_FORBIDDEN,
                    "Service accounts cannot be managed here."
            );
        }
    }

    private String normalizeRequiredUsername(String username) {
        String normalized = normalizeOptional(username);
        if (normalized == null || normalized.toLowerCase(Locale.ROOT).startsWith(SERVICE_ACCOUNT_USERNAME_PREFIX)) {
            throw new AdminUserManagementException(
                    AdminUserErrorCode.ADMIN_USER_INVALID_USERNAME,
                    "This username is not allowed."
            );
        }
        return normalized;
    }

    private String normalizeRequiredEmail(String email) {
        String normalized = normalizeOptional(email);
        if (normalized == null) {
            throw new AdminUserManagementException(
                    AdminUserErrorCode.ADMIN_USER_KEYCLOAK_OPERATION_FAILED,
                    "Email is required."
            );
        }
        return normalized.toLowerCase(Locale.ROOT);
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
        return username != null && username.startsWith(SERVICE_ACCOUNT_USERNAME_PREFIX);
    }

    private String resolveCreatedUserId(Response response, UsersResource usersResource, String username, String email) {
        String createdIdFromLocation = null;
        try {
            createdIdFromLocation = normalizeOptional(CreatedResponseUtil.getCreatedId(response));
        } catch (Exception ex) {
            logger.warn("Failed to parse created user id from response location for username '{}'.", username, ex);
        }

        if (createdIdFromLocation != null) {
            try {
                UserRepresentation createdUser = usersResource.get(createdIdFromLocation).toRepresentation();
                if (matchesCreatedUserIdentity(createdUser, username, email)) {
                    return createdIdFromLocation;
                }
                logger.error(
                        "Created user id '{}' from response location did not match requested identity (username='{}'). Falling back to lookup.",
                        createdIdFromLocation,
                        username
                );
            } catch (Exception ex) {
                logger.warn(
                        "Failed to read created user '{}' from response location for username '{}'. Falling back to lookup.",
                        createdIdFromLocation,
                        username,
                        ex
                );
            }
        }

        return findCreatedUserIdByIdentity(usersResource, username, email)
                .orElseThrow(() -> new IllegalStateException("Identity provider did not return a resolvable user id after successful creation."));
    }

    private Optional<String> findCreatedUserIdByIdentity(UsersResource usersResource, String username, String email) {
        String normalizedEmail = normalizeOptional(email);
        return usersResource.searchByUsername(username, true).stream()
                .filter(user -> matchesCreatedUserIdentity(user, username, normalizedEmail))
                .map(UserRepresentation::getId)
                .filter(id -> id != null && !id.isBlank())
                .findFirst();
    }

    private boolean matchesCreatedUserIdentity(UserRepresentation user, String username, String email) {
        if (user == null) {
            return false;
        }
        String normalizedUsername = normalizeOptional(username);
        String normalizedEmail = normalizeOptional(email);
        String userUsername = normalizeOptional(user.getUsername());
        String userEmail = normalizeOptional(user.getEmail());
        return userUsername != null
                && userUsername.equalsIgnoreCase(normalizedUsername)
                && (normalizedEmail == null || (userEmail != null && userEmail.equalsIgnoreCase(normalizedEmail)));
    }

    private String buildIdentityProviderCreateFailureMessage(int status, String providerErrorDetail) {
        String baseMessage = "Failed to create user in the identity provider (HTTP " + status + ").";
        if (providerErrorDetail == null) {
            if (status == Response.Status.BAD_REQUEST.getStatusCode()) {
                return baseMessage + " Check required user profile attributes in the identity provider.";
            }
            return baseMessage;
        }
        return baseMessage + " " + providerErrorDetail;
    }

    private String extractIdentityProviderErrorDetail(Response response) {
        if (response == null || !response.hasEntity()) {
            return null;
        }
        try {
            String rawBody = normalizeOptional(response.readEntity(String.class));
            if (rawBody == null) {
                return null;
            }
            return summarizeIdentityProviderErrorBody(rawBody);
        } catch (Exception ex) {
            logger.warn("Failed to read identity provider error body from create-user response.");
            return null;
        }
    }

    private String summarizeIdentityProviderErrorBody(String rawBody) {
        String normalizedBody = normalizeSingleLine(rawBody);
        if (normalizedBody == null) {
            return null;
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(normalizedBody);
            String preferredMessage = firstNonBlank(
                    readJsonText(root, "errorMessage"),
                    readJsonText(root, "error_description"),
                    readJsonText(root, "message"),
                    readJsonText(root, "error")
            );
            if (preferredMessage != null) {
                return truncateMessage(preferredMessage);
            }
        } catch (Exception ignored) {
            // Fall back to sanitized/truncated payload below.
        }

        return truncateMessage(normalizedBody);
    }

    private String readJsonText(JsonNode root, String fieldName) {
        JsonNode field = root.get(fieldName);
        if (field == null || !field.isValueNode()) {
            return null;
        }
        return normalizeOptional(field.asText());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = normalizeOptional(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String truncateMessage(String value) {
        if (value.length() <= IDENTITY_PROVIDER_ERROR_DETAIL_MAX_LENGTH) {
            return value;
        }
        return value.substring(0, IDENTITY_PROVIDER_ERROR_DETAIL_MAX_LENGTH) + "...";
    }

    private String normalizeSingleLine(String value) {
        if (value == null) {
            return null;
        }
        String withoutControlChars = value.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");
        String collapsed = withoutControlChars.replaceAll("\\s+", " ").trim();
        return collapsed.isEmpty() ? null : collapsed;
    }

    private String extractAvatarUrl(UserRepresentation user) {
        if (user.getAttributes() != null) {
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

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

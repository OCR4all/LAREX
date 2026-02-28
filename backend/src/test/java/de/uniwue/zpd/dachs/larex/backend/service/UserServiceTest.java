package de.uniwue.zpd.dachs.larex.backend.service;

import de.uniwue.zpd.dachs.larex.backend.config.auth.AuthProvisioningProperties;
import de.uniwue.zpd.dachs.larex.backend.config.auth.UserProvisioningMode;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminCreateUserRequest;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserAuditAction;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserAuditOutcome;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserIdentitySource;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserOnboardingState;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserPageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserStatusFilter;
import de.uniwue.zpd.dachs.larex.backend.exception.AdminUserErrorCode;
import de.uniwue.zpd.dachs.larex.backend.exception.AdminUserManagementException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private Keycloak keycloakAdmin;

    @Mock
    private RealmResource realmResource;

    @Mock
    private UsersResource usersResource;

    @Mock
    private UserResource userResource;

    @Mock
    private AdminUserAuditService adminUserAuditService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = createUserService(UserProvisioningMode.LOCAL);
        lenient().when(keycloakAdmin.realm("larex-prod")).thenReturn(realmResource);
        lenient().when(realmResource.users()).thenReturn(usersResource);
    }

    @Test
    void createUserForAdmin_createsUserAndSendsActionsEmail() {
        when(usersResource.searchByUsername("alice", true)).thenReturn(List.of());
        when(usersResource.searchByEmail("alice@example.org", true)).thenReturn(List.of());

        Response createdResponse = Response.status(Response.Status.CREATED)
                .location(URI.create("http://keycloak.example/admin/realms/larex-prod/users/user-1"))
                .build();
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(createdResponse);
        when(usersResource.get("user-1")).thenReturn(userResource);

        UserRepresentation createdUserRepresentation = localUser("user-1", "alice", "alice@example.org", true, false, List.of("VERIFY_EMAIL", "UPDATE_PASSWORD"));
        when(userResource.toRepresentation()).thenReturn(createdUserRepresentation);

        var created = userService.createUserForAdmin(
                "admin-1",
                "admin",
                new AdminCreateUserRequest("alice", "Alice@Example.ORG", "Alice", "Admin")
        );

        ArgumentCaptor<UserRepresentation> createCaptor = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(usersResource).create(createCaptor.capture());
        UserRepresentation submitted = createCaptor.getValue();
        assertEquals("alice", submitted.getUsername());
        assertEquals("alice@example.org", submitted.getEmail());
        assertEquals(List.of("VERIFY_EMAIL", "UPDATE_PASSWORD"), submitted.getRequiredActions());
        assertTrue(Boolean.TRUE.equals(submitted.isEnabled()));
        assertFalse(Boolean.TRUE.equals(submitted.isEmailVerified()));

        verify(userResource).executeActionsEmail(
                "larex-frontend",
                "http://larex.localhost/auth/keycloak",
                43200,
                List.of("VERIFY_EMAIL", "UPDATE_PASSWORD")
        );
        verify(userResource, never()).remove();
        verify(adminUserAuditService).logEvent(
                eq("admin-1"),
                eq("admin"),
                eq("user-1"),
                eq("alice"),
                eq(AdminUserAuditAction.CREATE),
                eq(AdminUserAuditOutcome.SUCCESS),
                anyMap()
        );

        assertNotNull(created);
        assertEquals("user-1", created.id());
        assertEquals("alice", created.username());
        assertEquals("alice@example.org", created.email());
        assertEquals(AdminUserOnboardingState.PENDING_SETUP, created.onboardingState());
        assertEquals(AdminUserIdentitySource.LOCAL, created.identitySource());
        assertFalse(created.externallyManaged());
    }

    @Test
    void createUserForAdmin_rejectsDuplicateUsername() {
        when(usersResource.searchByUsername("alice", true)).thenReturn(List.of(localUser("existing", "ALICE", "existing@example.org", true, true, List.of())));

        AdminUserManagementException ex = assertThrows(
                AdminUserManagementException.class,
                () -> userService.createUserForAdmin("admin-1", "admin", new AdminCreateUserRequest("alice", "alice@example.org", null, null))
        );

        assertEquals(AdminUserErrorCode.ADMIN_USER_DUPLICATE_USERNAME, ex.getCode());
        verify(usersResource, never()).create(any(UserRepresentation.class));
        verify(adminUserAuditService).logEvent(
                eq("admin-1"),
                eq("admin"),
                eq(null),
                eq("alice"),
                eq(AdminUserAuditAction.CREATE),
                eq(AdminUserAuditOutcome.FAILURE),
                anyMap()
        );
    }

    @Test
    void createUserForAdmin_rejectsDuplicateEmail() {
        when(usersResource.searchByUsername("alice", true)).thenReturn(List.of());
        when(usersResource.searchByEmail("alice@example.org", true)).thenReturn(List.of(localUser("existing", "other", "ALICE@EXAMPLE.ORG", true, true, List.of())));

        AdminUserManagementException ex = assertThrows(
                AdminUserManagementException.class,
                () -> userService.createUserForAdmin("admin-1", "admin", new AdminCreateUserRequest("alice", "alice@example.org", null, null))
        );

        assertEquals(AdminUserErrorCode.ADMIN_USER_DUPLICATE_EMAIL, ex.getCode());
        verify(usersResource, never()).create(any(UserRepresentation.class));
    }

    @Test
    void createUserForAdmin_rejectsReservedServiceAccountUsername() {
        AdminUserManagementException ex = assertThrows(
                AdminUserManagementException.class,
                () -> userService.createUserForAdmin(
                        "admin-1",
                        "admin",
                        new AdminCreateUserRequest("service-account-test", "alice@example.org", null, null)
                )
        );

        assertEquals(AdminUserErrorCode.ADMIN_USER_INVALID_USERNAME, ex.getCode());
        verify(usersResource, never()).create(any(UserRepresentation.class));
    }

    @Test
    void createUserForAdmin_rollsBackIfActionsEmailFails() {
        when(usersResource.searchByUsername("alice", true)).thenReturn(List.of());
        when(usersResource.searchByEmail("alice@example.org", true)).thenReturn(List.of());

        Response createdResponse = Response.status(Response.Status.CREATED)
                .location(URI.create("http://keycloak.example/admin/realms/larex-prod/users/user-2"))
                .build();
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(createdResponse);
        when(usersResource.get("user-2")).thenReturn(userResource);
        doThrow(new RuntimeException("SMTP unavailable")).when(userResource)
                .executeActionsEmail(anyString(), anyString(), anyInt(), anyList());

        AdminUserManagementException ex = assertThrows(
                AdminUserManagementException.class,
                () -> userService.createUserForAdmin("admin-1", "admin", new AdminCreateUserRequest("alice", "alice@example.org", null, null))
        );

        assertEquals(AdminUserErrorCode.ADMIN_USER_SETUP_EMAIL_FAILED, ex.getCode());
        verify(userResource).remove();
        verify(adminUserAuditService).logEvent(
                eq("admin-1"),
                eq("admin"),
                eq("user-2"),
                eq("alice"),
                eq(AdminUserAuditAction.CREATE),
                eq(AdminUserAuditOutcome.FAILURE),
                anyMap()
        );
    }

    @Test
    void createUserForAdmin_rejectsWhenProvisioningDisabled() {
        userService = createUserService(UserProvisioningMode.LDAP_MANAGED);

        AdminUserManagementException ex = assertThrows(
                AdminUserManagementException.class,
                () -> userService.createUserForAdmin("admin-1", "admin", new AdminCreateUserRequest("alice", "alice@example.org", null, null))
        );

        assertEquals(AdminUserErrorCode.ADMIN_USER_PROVISIONING_DISABLED, ex.getCode());
        verify(usersResource, never()).create(any(UserRepresentation.class));
        verify(adminUserAuditService).logEvent(
                eq("admin-1"),
                eq("admin"),
                eq(null),
                eq("alice"),
                eq(AdminUserAuditAction.CREATE),
                eq(AdminUserAuditOutcome.FAILURE),
                anyMap()
        );
    }

    @Test
    void getUserPageForAdmin_hidesServiceAccountsByDefaultAndExposesCapabilities() {
        UserRepresentation human = localUser("human-id", "human-user", "human@example.org", true, true, List.of());
        human.setCreatedTimestamp(100L);
        UserRepresentation ldapUser = ldapUser("ldap-id", "ldap-user", "ldap@example.org", true, true, List.of());
        ldapUser.setCreatedTimestamp(200L);
        UserRepresentation byClientId = serviceAccountUser("svc-1", "anything");
        byClientId.setCreatedTimestamp(300L);

        when(usersResource.list()).thenReturn(List.of(human, ldapUser, byClientId));

        AdminUserPageDto visibleByDefault = userService.getUserPageForAdmin(0, 25, null, false, AdminUserStatusFilter.ALL);
        AdminUserPageDto includeAll = userService.getUserPageForAdmin(0, 25, null, true, AdminUserStatusFilter.ALL);

        assertEquals(2, visibleByDefault.items().size());
        assertEquals("ldap-user", visibleByDefault.items().getFirst().username());
        assertTrue(visibleByDefault.creationAllowed());
        assertTrue(visibleByDefault.setupEmailAllowed());
        assertEquals(3, includeAll.items().size());
        assertEquals(AdminUserOnboardingState.SERVICE_ACCOUNT, includeAll.items().get(0).onboardingState());
        assertEquals(AdminUserIdentitySource.SERVICE_ACCOUNT, includeAll.items().get(0).identitySource());
    }

    @Test
    void getUserPageForAdmin_derivesOnboardingStatesAndIdentitySources() {
        UserRepresentation active = localUser("active", "active-user", "active@example.org", true, true, List.of());
        active.setCreatedTimestamp(400L);
        UserRepresentation pending = localUser("pending", "pending-user", "pending@example.org", true, false, List.of("UPDATE_PASSWORD"));
        pending.setCreatedTimestamp(300L);
        UserRepresentation disabled = localUser("disabled", "disabled-user", "disabled@example.org", false, true, List.of());
        disabled.setCreatedTimestamp(200L);
        UserRepresentation ldap = ldapUser("ldap", "ldap-user", "ldap@example.org", true, true, List.of());
        ldap.setCreatedTimestamp(150L);
        UserRepresentation service = serviceAccountUser("service", "service-account-larex");
        service.setCreatedTimestamp(100L);

        when(usersResource.list()).thenReturn(List.of(service, ldap, disabled, pending, active));

        AdminUserPageDto page = userService.getUserPageForAdmin(0, 25, null, true, AdminUserStatusFilter.ALL);

        assertEquals(List.of(
                AdminUserOnboardingState.ACTIVE,
                AdminUserOnboardingState.PENDING_SETUP,
                AdminUserOnboardingState.DISABLED,
                AdminUserOnboardingState.ACTIVE,
                AdminUserOnboardingState.SERVICE_ACCOUNT
        ), page.items().stream().map(user -> user.onboardingState()).toList());
        assertEquals(List.of(
                AdminUserIdentitySource.LOCAL,
                AdminUserIdentitySource.LOCAL,
                AdminUserIdentitySource.LOCAL,
                AdminUserIdentitySource.LDAP,
                AdminUserIdentitySource.SERVICE_ACCOUNT
        ), page.items().stream().map(user -> user.identitySource()).toList());
        assertTrue(page.items().get(3).externallyManaged());
    }

    @Test
    void getUserPageForAdmin_disablesCapabilitiesInLdapManagedMode() {
        userService = createUserService(UserProvisioningMode.LDAP_MANAGED);
        UserRepresentation localUser = localUser("user-1", "alice", "alice@example.org", true, true, List.of());
        when(usersResource.list()).thenReturn(List.of(localUser));

        AdminUserPageDto page = userService.getUserPageForAdmin(0, 25, null, false, AdminUserStatusFilter.ALL);

        assertFalse(page.creationAllowed());
        assertFalse(page.setupEmailAllowed());
    }

    @Test
    void disableUserForAdmin_disablesNormalUser() {
        UserRepresentation current = localUser("user-1", "alice", "alice@example.org", true, true, List.of());
        UserRepresentation updated = localUser("user-1", "alice", "alice@example.org", false, true, List.of());
        when(usersResource.get("user-1")).thenReturn(userResource);
        when(userResource.toRepresentation()).thenReturn(current, updated);

        var result = userService.disableUserForAdmin("admin-1", "admin", "user-1");

        ArgumentCaptor<UserRepresentation> updateCaptor = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(userResource).update(updateCaptor.capture());
        assertFalse(Boolean.TRUE.equals(updateCaptor.getValue().isEnabled()));
        assertFalse(result.enabled());
        assertEquals(AdminUserOnboardingState.DISABLED, result.onboardingState());
    }

    @Test
    void disableUserForAdmin_rejectsSelfDisable() {
        UserRepresentation current = localUser("user-1", "alice", "alice@example.org", true, true, List.of());
        when(usersResource.get("user-1")).thenReturn(userResource);
        when(userResource.toRepresentation()).thenReturn(current);

        AdminUserManagementException ex = assertThrows(
                AdminUserManagementException.class,
                () -> userService.disableUserForAdmin("user-1", "alice", "user-1")
        );

        assertEquals(AdminUserErrorCode.ADMIN_USER_SELF_DISABLE_FORBIDDEN, ex.getCode());
        verify(userResource, never()).update(any(UserRepresentation.class));
    }

    @Test
    void disableUserForAdmin_rejectsServiceAccount() {
        UserRepresentation serviceAccount = serviceAccountUser("svc-1", "service-account-foo");
        when(usersResource.get("svc-1")).thenReturn(userResource);
        when(userResource.toRepresentation()).thenReturn(serviceAccount);

        AdminUserManagementException ex = assertThrows(
                AdminUserManagementException.class,
                () -> userService.disableUserForAdmin("admin-1", "admin", "svc-1")
        );

        assertEquals(AdminUserErrorCode.ADMIN_USER_SERVICE_ACCOUNT_FORBIDDEN, ex.getCode());
        verify(userResource, never()).update(any(UserRepresentation.class));
    }

    @Test
    void disableUserForAdmin_rejectsExternallyManagedUserInLocalMode() {
        UserRepresentation ldapUser = ldapUser("ldap-1", "ldap-user", "ldap@example.org", true, true, List.of());
        when(usersResource.get("ldap-1")).thenReturn(userResource);
        when(userResource.toRepresentation()).thenReturn(ldapUser);

        AdminUserManagementException ex = assertThrows(
                AdminUserManagementException.class,
                () -> userService.disableUserForAdmin("admin-1", "admin", "ldap-1")
        );

        assertEquals(AdminUserErrorCode.ADMIN_USER_EXTERNALLY_MANAGED, ex.getCode());
        verify(userResource, never()).update(any(UserRepresentation.class));
    }

    @Test
    void disableUserForAdmin_rejectsAllMutationsInLdapManagedMode() {
        userService = createUserService(UserProvisioningMode.LDAP_MANAGED);
        UserRepresentation localUser = localUser("user-1", "alice", "alice@example.org", true, true, List.of());
        when(usersResource.get("user-1")).thenReturn(userResource);
        when(userResource.toRepresentation()).thenReturn(localUser);

        AdminUserManagementException ex = assertThrows(
                AdminUserManagementException.class,
                () -> userService.disableUserForAdmin("admin-1", "admin", "user-1")
        );

        assertEquals(AdminUserErrorCode.ADMIN_USER_EXTERNALLY_MANAGED, ex.getCode());
        verify(userResource, never()).update(any(UserRepresentation.class));
    }

    @Test
    void enableUserForAdmin_enablesDisabledUser() {
        UserRepresentation current = localUser("user-1", "alice", "alice@example.org", false, false, List.of("VERIFY_EMAIL", "UPDATE_PASSWORD"));
        UserRepresentation updated = localUser("user-1", "alice", "alice@example.org", true, false, List.of("VERIFY_EMAIL", "UPDATE_PASSWORD"));
        when(usersResource.get("user-1")).thenReturn(userResource);
        when(userResource.toRepresentation()).thenReturn(current, updated);

        var result = userService.enableUserForAdmin("admin-1", "admin", "user-1");

        assertTrue(result.enabled());
        assertEquals(AdminUserOnboardingState.PENDING_SETUP, result.onboardingState());
    }

    @Test
    void enableUserForAdmin_rejectsAlreadyEnabledUser() {
        UserRepresentation current = localUser("user-1", "alice", "alice@example.org", true, true, List.of());
        when(usersResource.get("user-1")).thenReturn(userResource);
        when(userResource.toRepresentation()).thenReturn(current);

        AdminUserManagementException ex = assertThrows(
                AdminUserManagementException.class,
                () -> userService.enableUserForAdmin("admin-1", "admin", "user-1")
        );

        assertEquals(AdminUserErrorCode.ADMIN_USER_ALREADY_ENABLED, ex.getCode());
        verify(userResource, never()).update(any(UserRepresentation.class));
    }

    @Test
    void enableUserForAdmin_rejectsExternallyManagedUserInLocalMode() {
        UserRepresentation ldapUser = ldapUser("ldap-1", "ldap-user", "ldap@example.org", false, true, List.of());
        when(usersResource.get("ldap-1")).thenReturn(userResource);
        when(userResource.toRepresentation()).thenReturn(ldapUser);

        AdminUserManagementException ex = assertThrows(
                AdminUserManagementException.class,
                () -> userService.enableUserForAdmin("admin-1", "admin", "ldap-1")
        );

        assertEquals(AdminUserErrorCode.ADMIN_USER_EXTERNALLY_MANAGED, ex.getCode());
        verify(userResource, never()).update(any(UserRepresentation.class));
    }

    @Test
    void resendSetupEmailForAdmin_succeedsForPendingUser() {
        UserRepresentation pending = localUser("user-1", "alice", "alice@example.org", true, false, List.of("VERIFY_EMAIL", "UPDATE_PASSWORD"));
        when(usersResource.get("user-1")).thenReturn(userResource);
        when(userResource.toRepresentation()).thenReturn(pending, pending);

        var result = userService.resendSetupEmailForAdmin("admin-1", "admin", "user-1");

        verify(userResource).executeActionsEmail(
                "larex-frontend",
                "http://larex.localhost/auth/keycloak",
                43200,
                List.of("VERIFY_EMAIL", "UPDATE_PASSWORD")
        );
        assertEquals(AdminUserOnboardingState.PENDING_SETUP, result.onboardingState());
    }

    @Test
    void resendSetupEmailForAdmin_rejectsActiveUser() {
        UserRepresentation active = localUser("user-1", "alice", "alice@example.org", true, true, List.of());
        when(usersResource.get("user-1")).thenReturn(userResource);
        when(userResource.toRepresentation()).thenReturn(active);

        AdminUserManagementException ex = assertThrows(
                AdminUserManagementException.class,
                () -> userService.resendSetupEmailForAdmin("admin-1", "admin", "user-1")
        );

        assertEquals(AdminUserErrorCode.ADMIN_USER_RESEND_NOT_ALLOWED, ex.getCode());
        verify(userResource, never()).executeActionsEmail(anyString(), anyString(), anyInt(), anyList());
    }

    @Test
    void resendSetupEmailForAdmin_rejectsDisabledUser() {
        UserRepresentation disabled = localUser("user-1", "alice", "alice@example.org", false, false, List.of("VERIFY_EMAIL"));
        when(usersResource.get("user-1")).thenReturn(userResource);
        when(userResource.toRepresentation()).thenReturn(disabled);

        AdminUserManagementException ex = assertThrows(
                AdminUserManagementException.class,
                () -> userService.resendSetupEmailForAdmin("admin-1", "admin", "user-1")
        );

        assertEquals(AdminUserErrorCode.ADMIN_USER_RESEND_NOT_ALLOWED, ex.getCode());
    }

    @Test
    void resendSetupEmailForAdmin_rejectsServiceAccount() {
        UserRepresentation serviceAccount = serviceAccountUser("svc-1", "service-account-foo");
        when(usersResource.get("svc-1")).thenReturn(userResource);
        when(userResource.toRepresentation()).thenReturn(serviceAccount);

        AdminUserManagementException ex = assertThrows(
                AdminUserManagementException.class,
                () -> userService.resendSetupEmailForAdmin("admin-1", "admin", "svc-1")
        );

        assertEquals(AdminUserErrorCode.ADMIN_USER_SERVICE_ACCOUNT_FORBIDDEN, ex.getCode());
    }

    @Test
    void resendSetupEmailForAdmin_rejectsExternallyManagedUserInLocalMode() {
        UserRepresentation ldapUser = ldapUser("ldap-1", "ldap-user", "ldap@example.org", true, false, List.of("VERIFY_EMAIL", "UPDATE_PASSWORD"));
        when(usersResource.get("ldap-1")).thenReturn(userResource);
        when(userResource.toRepresentation()).thenReturn(ldapUser);

        AdminUserManagementException ex = assertThrows(
                AdminUserManagementException.class,
                () -> userService.resendSetupEmailForAdmin("admin-1", "admin", "ldap-1")
        );

        assertEquals(AdminUserErrorCode.ADMIN_USER_EXTERNALLY_MANAGED, ex.getCode());
        verify(userResource, never()).executeActionsEmail(anyString(), anyString(), anyInt(), anyList());
    }

    @Test
    void resendSetupEmailForAdmin_rejectsWhenProvisioningDisabled() {
        userService = createUserService(UserProvisioningMode.LDAP_MANAGED);
        UserRepresentation localUser = localUser("user-1", "alice", "alice@example.org", true, false, List.of("VERIFY_EMAIL", "UPDATE_PASSWORD"));
        when(usersResource.get("user-1")).thenReturn(userResource);
        when(userResource.toRepresentation()).thenReturn(localUser);

        AdminUserManagementException ex = assertThrows(
                AdminUserManagementException.class,
                () -> userService.resendSetupEmailForAdmin("admin-1", "admin", "user-1")
        );

        assertEquals(AdminUserErrorCode.ADMIN_USER_PROVISIONING_DISABLED, ex.getCode());
        verify(userResource, never()).executeActionsEmail(anyString(), anyString(), anyInt(), anyList());
    }

    private UserService createUserService(UserProvisioningMode provisioningMode) {
        AuthProvisioningProperties properties = new AuthProvisioningProperties();
        properties.setUserProvisioningMode(provisioningMode);

        return new UserService(
                keycloakAdmin,
                "larex-prod",
                "larex-frontend",
                "http://larex.localhost/auth/keycloak",
                43200,
                adminUserAuditService,
                properties
        );
    }

    private UserRepresentation localUser(String id, String username, String email, boolean enabled, boolean emailVerified, List<String> requiredActions) {
        UserRepresentation user = baseUser(id, username, email, enabled, emailVerified, requiredActions);
        user.setFederationLink(null);
        return user;
    }

    private UserRepresentation ldapUser(String id, String username, String email, boolean enabled, boolean emailVerified, List<String> requiredActions) {
        UserRepresentation user = baseUser(id, username, email, enabled, emailVerified, requiredActions);
        user.setFederationLink("ldap-provider");
        return user;
    }

    private UserRepresentation serviceAccountUser(String id, String username) {
        UserRepresentation user = baseUser(id, username, null, true, false, List.of());
        user.setServiceAccountClientId("larex-backend-service");
        return user;
    }

    private UserRepresentation baseUser(String id, String username, String email, boolean enabled, boolean emailVerified, List<String> requiredActions) {
        UserRepresentation user = new UserRepresentation();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setEnabled(enabled);
        user.setEmailVerified(emailVerified);
        user.setRequiredActions(requiredActions);
        return user;
    }
}

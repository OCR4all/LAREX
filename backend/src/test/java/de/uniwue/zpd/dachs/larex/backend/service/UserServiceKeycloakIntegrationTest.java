package de.uniwue.zpd.dachs.larex.backend.service;

import de.uniwue.zpd.dachs.larex.backend.config.auth.AuthProvisioningProperties;
import de.uniwue.zpd.dachs.larex.backend.config.security.KeycloakAdminProperties;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserDto;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserIdentitySource;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserOnboardingState;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserSort;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserStatusFilter;
import de.uniwue.zpd.dachs.larex.backend.exception.AdminUserErrorCode;
import de.uniwue.zpd.dachs.larex.backend.exception.AdminUserManagementException;
import de.uniwue.zpd.dachs.larex.backend.repository.user.UserLoginRepository;
import de.uniwue.zpd.dachs.larex.backend.service.admin.AdminUserAuditService;
import de.uniwue.zpd.dachs.larex.backend.service.user.ProfileImageService;
import de.uniwue.zpd.dachs.larex.backend.service.user.UserService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.FederatedIdentityRepresentation;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Runs UserService against a real Keycloak with the same realm-management roles the bootstrap realm grants
 * the backend service account. The brokered user mirrors what Keycloak stores after a first OIDC broker login:
 * unverified email, no required actions, no credentials, and one federated identity link.
 */
@Testcontainers(disabledWithoutDocker = true)
class UserServiceKeycloakIntegrationTest {

    private static final String REALM = "larex-it";
    private static final String SERVICE_CLIENT_ID = "larex-backend-service";
    private static final String SERVICE_CLIENT_SECRET = "it-secret";

    @Container
    private static final GenericContainer<?> KEYCLOAK = new GenericContainer<>("quay.io/keycloak/keycloak:26.8.2-0")
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
            .withCommand("start-dev")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/realms/master").forPort(8080).withStartupTimeout(Duration.ofMinutes(3)));

    private static Keycloak masterAdmin;
    private static Keycloak serviceAccount;
    private static UserService userService;
    private static String localUserId;
    private static String brokeredUserId;

    @BeforeAll
    static void setUpRealm() {
        String serverUrl = "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080);
        masterAdmin = KeycloakBuilder.builder()
                .serverUrl(serverUrl).realm("master").clientId("admin-cli")
                .username("admin").password("admin")
                .build();

        RealmRepresentation realmRepresentation = new RealmRepresentation();
        realmRepresentation.setRealm(REALM);
        realmRepresentation.setEnabled(true);
        masterAdmin.realms().create(realmRepresentation);
        RealmResource realm = masterAdmin.realm(REALM);

        String serviceClientUuid = createServiceClient(realm);
        grantServiceAccountRoles(realm, serviceClientUuid);
        grantAdminApiScope(realm, serviceClientUuid);
        createIdentityProvider(realm);
        localUserId = createUser(realm, "local-user", true, true);
        brokeredUserId = createUser(realm, "sso-user", false, false);
        FederatedIdentityRepresentation link = new FederatedIdentityRepresentation();
        link.setIdentityProvider("partner-sso");
        link.setUserId("external-123");
        link.setUserName("sso-user@partner.example");
        realm.users().get(brokeredUserId).addFederatedIdentity("partner-sso", link).close();

        serviceAccount = KeycloakBuilder.builder()
                .serverUrl(serverUrl).realm(REALM).grantType("client_credentials")
                .clientId(SERVICE_CLIENT_ID).clientSecret(SERVICE_CLIENT_SECRET)
                .build();
        userService = new UserService(
                serviceAccount,
                new KeycloakAdminProperties(serverUrl, REALM, SERVICE_CLIENT_ID, SERVICE_CLIENT_SECRET, null),
                mock(AdminUserAuditService.class),
                new AuthProvisioningProperties(),
                JsonMapper.builder().findAndAddModules().build(),
                mock(ProfileImageService.class),
                mock(UserLoginRepository.class)
        );
    }

    @AfterAll
    static void closeClients() {
        if (serviceAccount != null) {
            serviceAccount.close();
        }
        if (masterAdmin != null) {
            masterAdmin.close();
        }
    }

    @Test
    void adminListDistinguishesBrokeredAndLocalUsers() {
        Map<String, AdminUserDto> users = userService.getUserPageForAdmin(
                        0, 25, null, false, AdminUserStatusFilter.ALL, AdminUserSort.CREATED_DESC, null)
                .items().stream()
                .collect(java.util.stream.Collectors.toMap(AdminUserDto::id, user -> user));

        AdminUserDto brokered = users.get(brokeredUserId);
        assertEquals(AdminUserIdentitySource.IDENTITY_PROVIDER, brokered.identitySource());
        assertEquals(AdminUserOnboardingState.ACTIVE, brokered.onboardingState());
        assertFalse(brokered.externallyManaged());

        AdminUserDto local = users.get(localUserId);
        assertEquals(AdminUserIdentitySource.LOCAL, local.identitySource());
        assertEquals(AdminUserOnboardingState.ACTIVE, local.onboardingState());
    }

    @Test
    void passwordChangeIsOnlyOfferedToUsersWithAPassword() {
        assertTrue(userService.isPasswordChangeAvailable(localUserId));
        assertFalse(userService.isPasswordChangeAvailable(brokeredUserId));
    }

    @Test
    void brokeredUsersGetNoSetupEmail() {
        AdminUserManagementException ex = assertThrows(
                AdminUserManagementException.class,
                () -> userService.resendSetupEmailForAdmin("admin-id", "admin", brokeredUserId)
        );

        assertEquals(AdminUserErrorCode.ADMIN_USER_RESEND_NOT_ALLOWED, ex.getCode());
    }

    private static String createServiceClient(RealmResource realm) {
        ClientRepresentation client = new ClientRepresentation();
        client.setClientId(SERVICE_CLIENT_ID);
        client.setSecret(SERVICE_CLIENT_SECRET);
        client.setPublicClient(false);
        client.setServiceAccountsEnabled(true);
        client.setStandardFlowEnabled(false);
        try (var response = realm.clients().create(client)) {
            return CreatedResponseUtil.getCreatedId(response);
        }
    }

    private static void grantServiceAccountRoles(RealmResource realm, String serviceClientUuid) {
        String serviceAccountUserId = realm.clients().get(serviceClientUuid).getServiceAccountUser().getId();
        String realmManagementUuid = realm.clients().findByClientId("realm-management").getFirst().getId();
        ClientResource realmManagement = realm.clients().get(realmManagementUuid);
        List<RoleRepresentation> roles = List.of("query-users", "view-users", "manage-users", "view-realm").stream()
                .map(role -> realmManagement.roles().get(role).toRepresentation())
                .toList();
        realm.users().get(serviceAccountUserId).roles().clientLevel(realmManagementUuid).add(roles);
    }

    /** Keycloak 26.8.2 rejects admin API tokens that lack the built-in kc-admin-api client scope. */
    private static void grantAdminApiScope(RealmResource realm, String serviceClientUuid) {
        String scopeId = realm.clientScopes().findAll().stream()
                .filter(scope -> "kc-admin-api".equals(scope.getName()))
                .findFirst()
                .orElseThrow()
                .getId();
        realm.clients().get(serviceClientUuid).addDefaultClientScope(scopeId);
    }

    private static void createIdentityProvider(RealmResource realm) {
        IdentityProviderRepresentation provider = new IdentityProviderRepresentation();
        provider.setAlias("partner-sso");
        provider.setProviderId("oidc");
        provider.setEnabled(true);
        provider.setConfig(Map.of(
                "clientId", "larex",
                "clientSecret", "unused",
                "clientAuthMethod", "client_secret_post",
                "authorizationUrl", "https://idp.example/auth",
                "tokenUrl", "https://idp.example/token"
        ));
        realm.identityProviders().create(provider).close();
    }

    private static String createUser(RealmResource realm, String username, boolean emailVerified, boolean withPassword) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEmail(username + "@example.org");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEnabled(true);
        user.setEmailVerified(emailVerified);
        if (withPassword) {
            CredentialRepresentation password = new CredentialRepresentation();
            password.setType(CredentialRepresentation.PASSWORD);
            password.setValue("password");
            password.setTemporary(false);
            user.setCredentials(List.of(password));
        }
        try (var response = realm.users().create(user)) {
            return CreatedResponseUtil.getCreatedId(response);
        }
    }
}

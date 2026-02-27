package de.uniwue.zpd.dachs.larex.backend.service;

import de.uniwue.zpd.dachs.larex.backend.dto.AdminCreateUserRequest;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserDto;
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

import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
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

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                keycloakAdmin,
                "larex-prod",
                "larex-frontend",
                "http://larex.localhost/auth/keycloak",
                43200
        );
        when(keycloakAdmin.realm("larex-prod")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
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

        UserRepresentation createdUserRepresentation = new UserRepresentation();
        createdUserRepresentation.setId("user-1");
        createdUserRepresentation.setUsername("alice");
        createdUserRepresentation.setEmail("alice@example.org");
        createdUserRepresentation.setEnabled(true);
        createdUserRepresentation.setEmailVerified(false);
        when(userResource.toRepresentation()).thenReturn(createdUserRepresentation);

        AdminCreateUserRequest request = new AdminCreateUserRequest(
                "alice",
                "Alice@Example.ORG",
                "Alice",
                "Admin"
        );

        AdminUserDto created = userService.createUserForAdmin(request);

        ArgumentCaptor<UserRepresentation> createCaptor = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(usersResource).create(createCaptor.capture());
        UserRepresentation submitted = createCaptor.getValue();
        assertEquals("alice", submitted.getUsername());
        assertEquals("alice@example.org", submitted.getEmail());
        assertEquals(List.of("VERIFY_EMAIL", "UPDATE_PASSWORD"), submitted.getRequiredActions());
        assertTrue(Boolean.TRUE.equals(submitted.isEnabled()));
        assertTrue(Boolean.FALSE.equals(submitted.isEmailVerified()));

        verify(userResource).executeActionsEmail(
                "larex-frontend",
                "http://larex.localhost/auth/keycloak",
                43200,
                List.of("VERIFY_EMAIL", "UPDATE_PASSWORD")
        );
        verify(userResource, never()).remove();

        assertNotNull(created);
        assertEquals("user-1", created.id());
        assertEquals("alice", created.username());
        assertEquals("alice@example.org", created.email());
    }

    @Test
    void createUserForAdmin_rejectsDuplicateUsername() {
        when(usersResource.searchByUsername("alice", true)).thenReturn(List.of(userWithUsername("ALICE")));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> userService.createUserForAdmin(new AdminCreateUserRequest("alice", "alice@example.org", null, null))
        );

        assertTrue(ex.getMessage().contains("username"));
        verify(usersResource, never()).create(any(UserRepresentation.class));
    }

    @Test
    void createUserForAdmin_rejectsDuplicateEmail() {
        when(usersResource.searchByUsername("alice", true)).thenReturn(List.of());
        when(usersResource.searchByEmail("alice@example.org", true)).thenReturn(List.of(userWithEmail("ALICE@EXAMPLE.ORG")));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> userService.createUserForAdmin(new AdminCreateUserRequest("alice", "alice@example.org", null, null))
        );

        assertTrue(ex.getMessage().contains("email"));
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

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> userService.createUserForAdmin(new AdminCreateUserRequest("alice", "alice@example.org", null, null))
        );

        assertTrue(ex.getMessage().contains("rolled back"));
        verify(userResource).remove();
    }

    @Test
    void getAllUsersForAdmin_hidesServiceAccountsByDefault() {
        UserRepresentation human = userWithUsername("human-user");
        UserRepresentation byClientId = userWithUsername("anything");
        byClientId.setServiceAccountClientId("larex-backend-service");
        UserRepresentation byPrefix = userWithUsername("service-account-larex-backend-service");

        when(usersResource.list()).thenReturn(List.of(human, byClientId, byPrefix));

        List<AdminUserDto> visibleByDefault = userService.getAllUsersForAdmin(false);
        List<AdminUserDto> includeAll = userService.getAllUsersForAdmin(true);

        assertEquals(1, visibleByDefault.size());
        assertEquals("human-user", visibleByDefault.getFirst().username());
        assertEquals(3, includeAll.size());
    }

    private UserRepresentation userWithUsername(String username) {
        UserRepresentation user = new UserRepresentation();
        user.setId("id-" + username);
        user.setUsername(username);
        user.setEnabled(true);
        return user;
    }

    private UserRepresentation userWithEmail(String email) {
        UserRepresentation user = new UserRepresentation();
        user.setId("id-" + email);
        user.setEmail(email);
        user.setEnabled(true);
        return user;
    }
}

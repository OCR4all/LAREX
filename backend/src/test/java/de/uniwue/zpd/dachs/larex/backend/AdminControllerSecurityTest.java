package de.uniwue.zpd.dachs.larex.backend;

import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserDto;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserIdentitySource;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserOnboardingState;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserPageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserStatusFilter;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminGlobalRolesDto;
import de.uniwue.zpd.dachs.larex.backend.exception.AdminUserErrorCode;
import de.uniwue.zpd.dachs.larex.backend.exception.AdminUserManagementException;
import de.uniwue.zpd.dachs.larex.backend.service.admin.AdminService;
import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageFilterIndexService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminService adminService;

    @MockBean
    private PageFilterIndexService pageFilterIndexService;

    @Test
    @WithMockUser(roles = "USER")
    void createUser_forbiddenForNonAdmin() throws Exception {
        mockMvc.perform(post("/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "email": "alice@example.org"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "GLOBAL_ADMIN")
    void createUser_createdForAdmin() throws Exception {
        when(adminService.createUserForAdmin(any(), any(), any())).thenReturn(adminUser(
                "user-1",
                true,
                false,
                false,
                AdminUserIdentitySource.LOCAL,
                AdminUserOnboardingState.PENDING_SETUP,
                false
        ));

        mockMvc.perform(post("/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "email": "alice@example.org",
                                  "firstName": "Alice",
                                  "lastName": "Admin"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.email").value("alice@example.org"))
                .andExpect(jsonPath("$.serviceAccount").value(false))
                .andExpect(jsonPath("$.externallyManaged").value(false))
                .andExpect(jsonPath("$.identitySource").value("LOCAL"))
                .andExpect(jsonPath("$.onboardingState").value("PENDING_SETUP"));
    }

    @Test
    @WithMockUser(roles = "GLOBAL_ADMIN")
    void createUser_rejectsUnknownFields() throws Exception {
        mockMvc.perform(post("/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "email": "alice@example.org",
                                  "roles": ["GLOBAL_ADMIN"]
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(adminService, never()).createUserForAdmin(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "GLOBAL_ADMIN")
    void createUser_returnsProvisioningDisabledErrorCode() throws Exception {
        when(adminService.createUserForAdmin(any(), any(), any()))
                .thenThrow(new AdminUserManagementException(
                        AdminUserErrorCode.ADMIN_USER_PROVISIONING_DISABLED,
                        "User creation is disabled because this deployment uses LDAP-managed identities."
                ));

        mockMvc.perform(post("/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "email": "alice@example.org"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_USER_PROVISIONING_DISABLED"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void disableUser_forbiddenForNonAdmin() throws Exception {
        mockMvc.perform(post("/admin/users/user-1/disable"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void enableUser_forbiddenForNonAdmin() throws Exception {
        mockMvc.perform(post("/admin/users/user-1/enable"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void resendSetup_forbiddenForNonAdmin() throws Exception {
        mockMvc.perform(post("/admin/users/user-1/resend-setup"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "GLOBAL_ADMIN")
    void disableUser_allowedForAdmin() throws Exception {
        when(adminService.disableUserForAdmin(any(), any(), eq("user-1")))
                .thenReturn(adminUser("user-1", false, false, false, AdminUserIdentitySource.LOCAL, AdminUserOnboardingState.DISABLED, false));

        mockMvc.perform(post("/admin/users/user-1/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.onboardingState").value("DISABLED"));
    }

    @Test
    @WithMockUser(roles = "GLOBAL_ADMIN")
    void disableUser_returnsStructuredErrorCode() throws Exception {
        when(adminService.disableUserForAdmin(any(), any(), eq("user-1")))
                .thenThrow(new AdminUserManagementException(
                        AdminUserErrorCode.ADMIN_USER_SELF_DISABLE_FORBIDDEN,
                        "You cannot disable your own account."
                ));

        mockMvc.perform(post("/admin/users/user-1/disable"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_USER_SELF_DISABLE_FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("You cannot disable your own account."));
    }

    @Test
    @WithMockUser(roles = "GLOBAL_ADMIN")
    void disableUser_returnsExternallyManagedErrorCode() throws Exception {
        when(adminService.disableUserForAdmin(any(), any(), eq("ldap-1")))
                .thenThrow(new AdminUserManagementException(
                        AdminUserErrorCode.ADMIN_USER_EXTERNALLY_MANAGED,
                        "This user is managed externally through LDAP and cannot be changed here."
                ));

        mockMvc.perform(post("/admin/users/ldap-1/disable"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_USER_EXTERNALLY_MANAGED"));
    }

    @Test
    @WithMockUser(roles = "GLOBAL_ADMIN")
    void getUsers_returnsPagedPayloadWithCapabilities() throws Exception {
        when(adminService.getUserPageForAdmin(eq(0), eq(25), any(), anyBoolean(), eq(AdminUserStatusFilter.ALL)))
                .thenReturn(new AdminUserPageDto(
                        List.of(adminUser("user-1", true, false, false, AdminUserIdentitySource.LOCAL, AdminUserOnboardingState.PENDING_SETUP, false)),
                        0,
                        25,
                        1,
                        1,
                        false,
                        false
                ));

        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(25))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.creationAllowed").value(false))
                .andExpect(jsonPath("$.setupEmailAllowed").value(false))
                .andExpect(jsonPath("$.items[0].username").value("alice"))
                .andExpect(jsonPath("$.items[0].identitySource").value("LOCAL"))
                .andExpect(jsonPath("$.items[0].externallyManaged").value(false))
                .andExpect(jsonPath("$.items[0].onboardingState").value("PENDING_SETUP"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void globalCuratorEndpoints_forbiddenForNonAdmin() throws Exception {
        mockMvc.perform(get("/admin/users/user-1/global-roles"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/users/user-1/global-curator/grant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "Needed for workspace operations"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/users/user-1/global-curator/revoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "No longer needed"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "GLOBAL_ADMIN")
    void getGlobalRoles_allowedForAdmin() throws Exception {
        when(adminService.getGlobalRolesForAdmin("user-1"))
                .thenReturn(new AdminGlobalRolesDto(true, false));

        mockMvc.perform(get("/admin/users/user-1/global-roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.globalAdmin").value(true))
                .andExpect(jsonPath("$.globalCurator").value(false));
    }

    @Test
    @WithMockUser(roles = "GLOBAL_ADMIN")
    void grantGlobalCurator_validatesReason() throws Exception {
        when(adminService.grantGlobalCuratorForAdmin(any(), any(), eq("user-1"), eq("Needed for workspace operations")))
                .thenReturn(new AdminGlobalRolesDto(false, true));

        mockMvc.perform(post("/admin/users/user-1/global-curator/grant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "Needed for workspace operations"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.globalAdmin").value(false))
                .andExpect(jsonPath("$.globalCurator").value(true));

        mockMvc.perform(post("/admin/users/user-1/global-curator/grant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "   "
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "GLOBAL_ADMIN")
    void revokeGlobalCurator_allowedForAdmin() throws Exception {
        when(adminService.revokeGlobalCuratorForAdmin(any(), any(), eq("user-1"), eq("No longer needed")))
                .thenReturn(new AdminGlobalRolesDto(false, false));

        mockMvc.perform(post("/admin/users/user-1/global-curator/revoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "No longer needed"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.globalAdmin").value(false))
                .andExpect(jsonPath("$.globalCurator").value(false));
    }

    @Test
    @WithMockUser(roles = "USER")
    void privateAccessTokenAccess_forbiddenForNonAdmin() throws Exception {
        mockMvc.perform(post("/admin/users/user-1/private-access-tokens/access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "GLOBAL_ADMIN")
    void privateAccessTokenAccess_allowedForAdmin() throws Exception {
        when(adminService.updatePrivateAccessTokenAccessForAdmin(any(), any(), eq("user-1"), eq(true)))
                .thenReturn(adminUser("user-1", true, true, false, AdminUserIdentitySource.LOCAL, AdminUserOnboardingState.ACTIVE, true));

        mockMvc.perform(post("/admin/users/user-1/private-access-tokens/access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.privateAccessTokensEnabled").value(true));
    }

    private AdminUserDto adminUser(
            String id,
            boolean enabled,
            boolean emailVerified,
            boolean externallyManaged,
            AdminUserIdentitySource identitySource,
            AdminUserOnboardingState onboardingState,
            boolean privateAccessTokensEnabled) {
        return new AdminUserDto(
                id,
                "alice",
                "alice@example.org",
                "Alice",
                "Admin",
                null,
                enabled,
                emailVerified,
                identitySource == AdminUserIdentitySource.SERVICE_ACCOUNT,
                externallyManaged,
                identitySource,
                onboardingState,
                privateAccessTokensEnabled,
                null
        );
    }
}

package de.uniwue.zpd.dachs.larex.backend;

import de.uniwue.zpd.dachs.larex.backend.config.security.WorkspaceSecurityService;
import de.uniwue.zpd.dachs.larex.backend.entity.WorkspaceStorageQuota;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceStorageQuotaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
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
class StorageQuotaControllerSecurityTest {

    private static final String WORKSPACE_ID = "workspace-1";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkspaceStorageQuotaService quotaService;

    @MockitoBean(name = "workspaceSecurity")
    private WorkspaceSecurityService workspaceSecurity;

    @BeforeEach
    void setUp() {
        when(workspaceSecurity.hasAccess(WORKSPACE_ID, "member")).thenReturn(true);
        when(workspaceSecurity.hasAccess(WORKSPACE_ID, "outsider")).thenReturn(false);
        when(quotaService.getOrCreateQuota(WORKSPACE_ID)).thenReturn(new WorkspaceStorageQuota(WORKSPACE_ID, 1000L));
        when(quotaService.recalculateUsage(WORKSPACE_ID)).thenReturn(new WorkspaceStorageQuota(WORKSPACE_ID, 1000L));
        when(quotaService.hasAvailableSpace(WORKSPACE_ID, 10L)).thenReturn(true);
    }

    @Test
    void quotaEndpoints_requireAuthentication() throws Exception {
        mockMvc.perform(get("/storage/quotas/workspace/" + WORKSPACE_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "member", roles = "USER")
    void workspaceMember_canReadQuotaAndCheckSpace() throws Exception {
        mockMvc.perform(get("/storage/quotas/workspace/" + WORKSPACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceId").value(WORKSPACE_ID));

        mockMvc.perform(get("/storage/quotas/workspace/" + WORKSPACE_ID + "/check/10"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "outsider", roles = "USER")
    void outsider_cannotReadQuotaOrCheckSpace() throws Exception {
        mockMvc.perform(get("/storage/quotas/workspace/" + WORKSPACE_ID))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/storage/quotas/workspace/" + WORKSPACE_ID + "/check/10"))
                .andExpect(status().isForbidden());

        verify(quotaService, never()).getOrCreateQuota(anyString());
    }

    @Test
    @WithMockUser(username = "member", roles = "USER")
    void workspaceMember_cannotTriggerRecalculation() throws Exception {
        mockMvc.perform(post("/storage/quotas/workspace/" + WORKSPACE_ID + "/recalculate"))
                .andExpect(status().isForbidden());

        verify(quotaService, never()).recalculateUsage(anyString());
    }

    @Test
    @WithMockUser(username = "admin", roles = "GLOBAL_ADMIN")
    void globalAdmin_canTriggerRecalculation() throws Exception {
        mockMvc.perform(post("/storage/quotas/workspace/" + WORKSPACE_ID + "/recalculate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceId").value(WORKSPACE_ID));
    }
}

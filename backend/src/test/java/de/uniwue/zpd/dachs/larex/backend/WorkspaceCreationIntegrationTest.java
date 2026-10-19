package de.uniwue.zpd.dachs.larex.backend;

import de.uniwue.zpd.dachs.larex.backend.config.StorageProperties;
import de.uniwue.zpd.dachs.larex.backend.entity.WorkspaceStorageQuota;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.TeamWorkspaceRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceStorageQuotaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class WorkspaceCreationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TeamWorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceStorageQuotaRepository quotaRepository;
    @Autowired private StorageProperties storageProperties;

    @Test
    void adminCreatesWorkspaceWithCustomQuota() throws Exception {
        create("custom-quota", "GLOBAL_ADMIN", ", \"quotaLimitBytes\": 2684354560");

        WorkspaceStorageQuota quota = quota("custom-quota");
        assertEquals(2_684_354_560L, quota.getQuotaLimitBytes());
        assertTrue(quota.getIsCustom());
        assertEquals(0L, quota.getCurrentUsageBytes());
    }

    @Test
    void adminCanSetZeroQuota() throws Exception {
        create("zero-quota", "GLOBAL_ADMIN", ", \"quotaLimitBytes\": 0");

        assertEquals(0L, quota("zero-quota").getQuotaLimitBytes());
        assertTrue(quota("zero-quota").getIsCustom());
    }

    @Test
    void omittedQuotaUsesInstanceDefault() throws Exception {
        create("default-quota", "GLOBAL_ADMIN", "");

        assertEquals(storageProperties.getDefaultQuotaBytes(), quota("default-quota").getQuotaLimitBytes());
        assertFalse(quota("default-quota").getIsCustom());
    }

    @Test
    void curatorCanCreateWorkspaceWithDefaultQuota() throws Exception {
        create("curator-default", "GLOBAL_CURATOR", "");

        assertEquals(storageProperties.getDefaultQuotaBytes(), quota("curator-default").getQuotaLimitBytes());
        assertFalse(quota("curator-default").getIsCustom());
    }

    @Test
    void curatorCannotSetQuotaAndNoWorkspaceIsCreated() throws Exception {
        mockMvc.perform(post("/workspaces")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_GLOBAL_CURATOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "curator-custom", "quotaLimitBytes": 1000}
                                """))
                .andExpect(status().isForbidden());

        assertFalse(workspaceRepository.existsByName("curator-custom"));
    }

    @Test
    void negativeQuotaIsRejectedBeforeCreatingWorkspace() throws Exception {
        mockMvc.perform(post("/workspaces")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_GLOBAL_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "negative-quota", "quotaLimitBytes": -1}
                                """))
                .andExpect(status().isBadRequest());

        assertFalse(workspaceRepository.existsByName("negative-quota"));
    }

    private void create(String name, String role, String quotaField) throws Exception {
        mockMvc.perform(post("/workspaces")
                        .with(jwt().jwt(jwt -> jwt.subject("workspace-creator"))
                                .authorities(new SimpleGrantedAuthority("ROLE_" + role)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + name + "\", \"description\": \"New workspace\"" + quotaField + "}"))
                .andExpect(status().isCreated());
    }

    private WorkspaceStorageQuota quota(String name) {
        String workspaceId = workspaceRepository.findByName(name).orElseThrow().getId();
        return quotaRepository.findByWorkspaceId(workspaceId).orElseThrow();
    }
}

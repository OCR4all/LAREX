package de.uniwue.zpd.dachs.larex.backend;

import de.uniwue.zpd.dachs.larex.backend.dto.PageXmlTextDto;
import de.uniwue.zpd.dachs.larex.backend.service.machine.PatXmlAccessService;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaGuardService;
import de.uniwue.zpd.dachs.larex.backend.service.user.PrivateAccessTokenService;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlRawEditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PublicPatPageXmlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PrivateAccessTokenService userMachineTokenService;

    @MockBean
    private PatXmlAccessService machineXmlAccessService;

    @MockBean
    private PageXmlRawEditService pageXmlRawEditService;

    @MockBean
    private PageService pageService;

    @MockBean
    private WorkspaceQuotaGuardService workspaceQuotaGuardService;

    @Test
    void getXmlTextReturnsUnauthorizedWhenTokenMissing() throws Exception {
        when(userMachineTokenService.authenticateBearerToken(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/public/pat/projects/p1/pages/p2/xml/x1/text"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getXmlTextReturnsForbiddenWhenScopeMissing() throws Exception {
        when(userMachineTokenService.authenticateBearerToken("Bearer no-read"))
                .thenReturn(Optional.of(new PrivateAccessTokenService.PrivateAccessTokenAuthContext(
                        "tok-1",
                        "user-1",
                        "ws-1",
                        List.of(PrivateAccessTokenService.SCOPE_XML_WRITE)
                )));

        mockMvc.perform(get("/public/pat/projects/p1/pages/p2/xml/x1/text")
                        .header("Authorization", "Bearer no-read"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getXmlTextReturnsNotFoundWhenWorkspaceBindingFails() throws Exception {
        when(userMachineTokenService.authenticateBearerToken("Bearer ok"))
                .thenReturn(Optional.of(new PrivateAccessTokenService.PrivateAccessTokenAuthContext(
                        "tok-1",
                        "user-1",
                        "ws-1",
                        List.of(PrivateAccessTokenService.SCOPE_XML_READ)
                )));
        when(machineXmlAccessService.xmlBelongsToPageInWorkspace("x1", "p2", "p1", "ws-1"))
                .thenReturn(false);

        mockMvc.perform(get("/public/pat/projects/p1/pages/p2/xml/x1/text")
                        .header("Authorization", "Bearer ok"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getXmlTextReturnsPayloadWhenAuthorized() throws Exception {
        when(userMachineTokenService.authenticateBearerToken("Bearer ok"))
                .thenReturn(Optional.of(new PrivateAccessTokenService.PrivateAccessTokenAuthContext(
                        "tok-1",
                        "user-1",
                        "ws-1",
                        List.of(PrivateAccessTokenService.SCOPE_XML_READ)
                )));
        when(machineXmlAccessService.xmlBelongsToPageInWorkspace("x1", "p2", "p1", "ws-1"))
                .thenReturn(true);
        when(pageXmlRawEditService.getXmlText("p1", "p2", "x1", "user-1"))
                .thenReturn(new PageXmlTextDto.XmlTextResponse(
                        "x1",
                        "PAGE_XML",
                        "<PcGts/>",
                        new PageXmlTextDto.XmlValidationResult(true, List.of(), "2019-07-15", "http://schema.primaresearch.org/PAGE/gts/pagecontent/2019-07-15")
                ));

        mockMvc.perform(get("/public/pat/projects/p1/pages/p2/xml/x1/text")
                        .header("Authorization", "Bearer ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.xmlId").value("x1"))
                .andExpect(jsonPath("$.validation.valid").value(true));
    }

    @Test
    void saveXmlTextReturnsForbiddenWhenScopeMissing() throws Exception {
        when(userMachineTokenService.authenticateBearerToken("Bearer read-only"))
                .thenReturn(Optional.of(new PrivateAccessTokenService.PrivateAccessTokenAuthContext(
                        "tok-1",
                        "user-1",
                        "ws-1",
                        List.of(PrivateAccessTokenService.SCOPE_XML_READ)
                )));

        mockMvc.perform(put("/public/pat/projects/p1/pages/p2/xml/x1/text")
                        .header("Authorization", "Bearer read-only")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "xml": "<PcGts/>",
                                  "comment": "update"
                                }
                                """))
                .andExpect(status().isForbidden());
    }
}

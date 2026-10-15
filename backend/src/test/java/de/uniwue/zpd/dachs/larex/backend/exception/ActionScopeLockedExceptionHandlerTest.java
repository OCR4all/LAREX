package de.uniwue.zpd.dachs.larex.backend.exception;

import de.uniwue.zpd.dachs.larex.backend.controller.action.ActionProjectController;
import de.uniwue.zpd.dachs.larex.backend.service.action.ActionPublicBaseUrlService;
import de.uniwue.zpd.dachs.larex.backend.service.action.ActionRunService;
import de.uniwue.zpd.dachs.larex.backend.service.admin.ErrorEventContextResolver;
import de.uniwue.zpd.dachs.larex.backend.service.admin.ErrorEventService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActionScopeLockedExceptionHandlerTest {

    @Test
    void returnsALockConflictWithThePageNameAndRecoveryMessage() throws Exception {
        ActionRunService service = mock(ActionRunService.class);
        ErrorEventService errorEvents = mock(ErrorEventService.class);
        String message = "Cannot start this Action on \"First\": Page is locked: Under review. Review the pages again to skip locked pages.";
        when(service.startRun(any(), any(), any(), any(), any())).thenThrow(new ActionScopeLockedException(message));
        var mvc = MockMvcBuilders.standaloneSetup(new ActionProjectController(service, mock(ActionPublicBaseUrlService.class)))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler(errorEvents, mock(ErrorEventContextResolver.class), new ObjectMapper()))
                .build();

        mvc.perform(post("/workspaces/workspace/actions/projects/project/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"processorDefinitionId":"processor","pageIds":["page-1"]}
                                """))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACTION_SCOPE_LOCKED"))
                .andExpect(jsonPath("$.message").value(message));
        verifyNoInteractions(errorEvents);
    }
}

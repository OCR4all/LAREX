package de.uniwue.zpd.dachs.larex.backend.exception;

import de.uniwue.zpd.dachs.larex.backend.controller.action.PublicActionMachineController;
import de.uniwue.zpd.dachs.larex.backend.service.action.ActionPublicBaseUrlService;
import de.uniwue.zpd.dachs.larex.backend.service.action.ActionRunService;
import de.uniwue.zpd.dachs.larex.backend.service.admin.ErrorEventContextResolver;
import de.uniwue.zpd.dachs.larex.backend.service.admin.ErrorEventService;
import de.uniwue.zpd.dachs.larex.backend.service.upload.UploadPathService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActionRunFailedExceptionHandlerTest {

    @Test
    void resultUploadToFailedRunReturnsAConflictWithTheReason() throws Exception {
        ActionRunService service = mock(ActionRunService.class);
        ErrorEventService errorEvents = mock(ErrorEventService.class);
        String message = "This Action run has already failed and no longer accepts results: Action heartbeat timed out";
        when(service.receiveResults(eq("run-1"), any(), any(), any())).thenThrow(new ActionRunFailedException(message));
        var mvc = MockMvcBuilders.standaloneSetup(new PublicActionMachineController(
                        service, mock(ActionPublicBaseUrlService.class), mock(UploadPathService.class)))
                .setControllerAdvice(new GlobalExceptionHandler(errorEvents, mock(ErrorEventContextResolver.class), new ObjectMapper()))
                .build();

        mvc.perform(multipart("/public/actions/runs/run-1/results")
                        .file(new MockMultipartFile("manifest", "manifest.json", MediaType.APPLICATION_JSON_VALUE,
                                "{\"protocolVersion\":1,\"status\":\"completed\"}".getBytes()))
                        .header("Authorization", "Bearer secret"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTION_RUN_FAILED"))
                .andExpect(jsonPath("$.message").value(message));
        verifyNoInteractions(errorEvents);
    }
}

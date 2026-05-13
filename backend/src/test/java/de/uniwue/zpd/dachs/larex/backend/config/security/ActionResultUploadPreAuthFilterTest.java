package de.uniwue.zpd.dachs.larex.backend.config.security;

import de.uniwue.zpd.dachs.larex.backend.service.action.ActionRunService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ActionResultUploadPreAuthFilterTest {

    @Mock
    private ActionRunService actionRunService;

    @Test
    void rejectsMissingBearerBeforeContinuing() throws Exception {
        ActionResultUploadPreAuthFilter filter = new ActionResultUploadPreAuthFilter(actionRunService);
        MockHttpServletRequest request = resultUploadRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean(false);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> continued.set(true));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(continued).isFalse();
        verifyNoInteractions(actionRunService);
    }

    @Test
    void authenticatesResultUploadBeforeContinuing() throws Exception {
        ActionResultUploadPreAuthFilter filter = new ActionResultUploadPreAuthFilter(actionRunService);
        MockHttpServletRequest request = resultUploadRequest();
        request.addHeader("Authorization", "Bearer run-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean(false);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> continued.set(true));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(continued).isTrue();
        verify(actionRunService).authenticateMachineRun("run-1", "Bearer run-secret");
    }

    @Test
    void rejectsInvalidBearerBeforeContinuing() throws Exception {
        ActionResultUploadPreAuthFilter filter = new ActionResultUploadPreAuthFilter(actionRunService);
        MockHttpServletRequest request = resultUploadRequest();
        request.addHeader("Authorization", "Bearer bad-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean(false);
        doThrow(new SecurityException("Invalid Action run secret"))
                .when(actionRunService).authenticateMachineRun("run-1", "Bearer bad-secret");

        filter.doFilter(request, response, (servletRequest, servletResponse) -> continued.set(true));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(continued).isFalse();
    }

    @Test
    void ignoresOtherPublicActionEndpoints() throws Exception {
        ActionResultUploadPreAuthFilter filter = new ActionResultUploadPreAuthFilter(actionRunService);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/public/actions/runs/run-1/heartbeat");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean(false);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> continued.set(true));

        assertThat(continued).isTrue();
        verifyNoInteractions(actionRunService);
    }

    private MockHttpServletRequest resultUploadRequest() {
        return new MockHttpServletRequest("POST", "/api/v1/public/actions/runs/run-1/results");
    }
}

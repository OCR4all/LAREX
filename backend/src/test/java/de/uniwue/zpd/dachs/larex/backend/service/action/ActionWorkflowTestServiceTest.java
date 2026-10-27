package de.uniwue.zpd.dachs.larex.backend.service.action;

import de.uniwue.zpd.dachs.larex.backend.config.security.GlobalAdminService;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionWorkflowDto;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionProcessorDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ActionWorkflowTestServiceTest {
    private final GlobalAdminService admin = mock(GlobalAdminService.class);
    private final ActionDefinitionService definitions = mock(ActionDefinitionService.class);
    private final ActionProcessorDefinitionRepository processors = mock(ActionProcessorDefinitionRepository.class);
    private final ActionRunService actions = mock(ActionRunService.class);
    private final ActionWorkflowService service = new ActionWorkflowService(null, null, null, processors,
            null, null, actions, definitions, null, null, admin, null, null, null, null, null);

    @BeforeEach
    void authorize() {
        when(admin.isGlobalAdmin()).thenReturn(true);
    }

    private ActionProcessorDefinition processor(String id) {
        var processor = new ActionProcessorDefinition();
        processor.setId(id);
        processor.setName("Processor " + id);
        processor.setEnabled(true);
        processor.setActionKind(ActionProcessorDefinition.ActionKind.PROCESSING);
        when(processors.findById(id)).thenReturn(Optional.of(processor));
        when(definitions.readTargetTypes(processor)).thenReturn(List.of(ActionProcessorDefinition.ActionTarget.PAGE));
        return processor;
    }

    private ActionWorkflowDto.Step step(String id, double threshold) {
        return new ActionWorkflowDto.Step(id, Map.of("threshold", threshold), null);
    }

    @Test
    void testsDraftStepsWithoutSavingOrRunningAndChecksRepeatedEndpointsOnce() {
        var processor = processor("one");
        when(definitions.testEndpoint("one")).thenReturn(new ActionDto.HealthCheckResponse(true, 200,
                "http://processor/preflight", "Authenticated preflight succeeded", 10));
        var first = step("one", 0.2);
        var second = step("one", 0.8);
        var result = service.test(new ActionWorkflowDto.TestRequest(List.of(first, second)));
        assertThat(result.ok()).isTrue();
        assertThat(result.steps()).extracting(ActionWorkflowDto.StepCheck::step).containsExactly(1, 2);
        assertThat(result.steps()).allSatisfy(check -> {
            assertThat(check.ok()).isTrue();
            assertThat(check.endpoint().statusCode()).isEqualTo(200);
        });
        verify(definitions, times(1)).testEndpoint("one");
        verify(actions).resolveRunParameters(processor, first.parameters(), null, null);
        verify(actions).resolveRunParameters(processor, second.parameters(), null, null);
        verify(processors, never()).save(any());
        verify(actions, never()).startRun(anyString(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void reportsEachFailureAndStillTestsLaterSteps() {
        var disabled = processor("disabled");
        disabled.setEnabled(false);
        processor("offline");
        processor("working");
        when(definitions.testEndpoint("offline")).thenReturn(new ActionDto.HealthCheckResponse(false, 503,
                "http://offline/health", "Endpoint returned HTTP 503", 15));
        when(definitions.testEndpoint("working")).thenReturn(new ActionDto.HealthCheckResponse(true, 200,
                "http://working/health", "Endpoint reachable", 5));
        var result = service.test(new ActionWorkflowDto.TestRequest(List.of(
                step("disabled", 0.1), step("offline", 0.2), step("working", 0.3))));
        assertThat(result.ok()).isFalse();
        assertThat(result.steps()).extracting(ActionWorkflowDto.StepCheck::ok).containsExactly(false, false, true);
        assertThat(result.steps().getFirst().message()).isEqualTo("Processor is disabled");
        verify(definitions, never()).testEndpoint("disabled");
        verify(definitions).testEndpoint("working");
    }

    @Test
    void rejectsInvalidParametersPerStepWithoutSkippingOtherChecks() {
        var invalid = processor("invalid");
        processor("working");
        when(actions.resolveRunParameters(eq(invalid), any(), any(), any())).thenThrow(new IllegalArgumentException("Invalid threshold"));
        when(definitions.testEndpoint("working")).thenReturn(new ActionDto.HealthCheckResponse(true, 200,
                "http://working/health", "Endpoint reachable", 1));
        var result = service.test(new ActionWorkflowDto.TestRequest(List.of(step("invalid", 200), step("working", 0.5))));
        assertThat(result.steps()).extracting(ActionWorkflowDto.StepCheck::ok).containsExactly(false, true);
        assertThat(result.steps().getFirst().message()).isEqualTo("Invalid threshold");
        verify(definitions, never()).testEndpoint("invalid");
    }

    @Test
    void requiresAdminAndAValidStepCountBeforeContactingEndpoints() {
        when(admin.isGlobalAdmin()).thenReturn(false);
        assertThatThrownBy(() -> service.test(new ActionWorkflowDto.TestRequest(List.of(step("one", 0.2)))))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(processors, definitions, actions);
        when(admin.isGlobalAdmin()).thenReturn(true);
        assertThatThrownBy(() -> service.test(new ActionWorkflowDto.TestRequest(List.of())))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(processors, definitions, actions);
    }
}

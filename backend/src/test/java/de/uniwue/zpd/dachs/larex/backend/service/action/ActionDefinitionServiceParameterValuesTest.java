package de.uniwue.zpd.dachs.larex.backend.service.action;

import de.uniwue.zpd.dachs.larex.backend.config.ActionProperties;
import de.uniwue.zpd.dachs.larex.backend.config.security.GlobalAdminService;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition;
import de.uniwue.zpd.dachs.larex.backend.exception.ActionParameterValueDiscoveryException;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionProcessorAssignmentRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionProcessorDefinitionRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionProcessorWorkspaceAvailabilityRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionRunDismissalRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionRunLogEventRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActionDefinitionServiceParameterValuesTest {

    private ActionProcessorDefinitionRepository definitionRepository;
    private ActionEndpointAuthService endpointAuthService;
    private HttpClient httpClient;
    private HttpResponse<String> httpResponse;
    private ActionDefinitionService service;
    private ActionProcessorDefinition definition;
    private final AtomicReference<String> requestId = new AtomicReference<>();

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() throws Exception {
        definitionRepository = mock(ActionProcessorDefinitionRepository.class);
        when(definitionRepository.findByProcessorKey(anyString())).thenReturn(java.util.Optional.empty());
        endpointAuthService = mock(ActionEndpointAuthService.class);
        when(endpointAuthService.normalizeAuthType(any())).thenReturn("none");
        httpClient = mock(HttpClient.class);
        httpResponse = mock(HttpResponse.class);
        when(endpointAuthService.buildDispatchHeaders(
                any(), eq("processor-v1"), anyString(), any(), anyString(), anyString()
        )).thenAnswer(invocation -> {
            requestId.set(invocation.getArgument(2));
            return Map.of("X-LAREX-Action-Auth", "hmac-sha256;v=1");
        });
        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

        service = new ActionDefinitionService(
                definitionRepository,
                mock(ActionProcessorWorkspaceAvailabilityRepository.class),
                mock(ActionProcessorAssignmentRepository.class),
                mock(ActionRunRepository.class),
                mock(ActionRunDismissalRepository.class),
                mock(ActionRunLogEventRepository.class),
                mock(GlobalAdminService.class),
                endpointAuthService,
                mock(ActionAuditService.class),
                new ObjectMapper(),
                new ActionProperties(),
                httpClient
        );

        definition = new ActionProcessorDefinition();
        definition.setProcessorKey("processor-v1");
        definition.setEndpointTimeoutSeconds(30);
        definition.setParsedJson(dynamicDefinitionJson());
    }

    @Test
    void validatesStaticAllowedValuesAndDynamicEndpointRequirements() {
        ActionDto.ValidationResponse valid = service.validateYaml(validStaticYaml(), null);
        ActionDto.ValidationResponse invalidDefault = service.validateYaml(
                validStaticYaml().replace("default: 2", "default: 3"), null);
        ActionDto.ValidationResponse missingEndpoint = service.validateYaml(dynamicYamlWithoutEndpoint(), null);

        assertThat(valid.valid()).isTrue();
        assertThat(invalidDefault.valid()).isFalse();
        assertThat(invalidDefault.diagnostics()).extracting(ActionDto.ValidationDiagnostic::message)
                .contains("default must be one of the statically allowed values");
        assertThat(missingEndpoint.valid()).isFalse();
        assertThat(missingEndpoint.diagnostics()).extracting(ActionDto.ValidationDiagnostic::path)
                .contains("endpoint.parameterValuesUrl");
    }

    @Test
    void discoversSignedTypedValuesAndEnforcesThem() {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenAnswer(ignored -> successfulResponse("processor-v1", "model-a"));

        ActionDto.ParameterValuesResponse response = service.discoverParameterValues(definition);

        assertThat(response.values().get("models")).singleElement()
                .satisfies(choice -> {
                    assertThat(choice.value()).isEqualTo("model-a");
                    assertThat(choice.label()).isEqualTo("Model A");
                });
        service.validateAllowedParameterValues(definition, Map.of("model", "model-a"));
        assertThatThrownBy(() -> service.validateAllowedParameterValues(
                definition, Map.of("model", "forged")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Action parameter model must be one of its allowed values");

        verify(endpointAuthService, atLeastOnce()).buildDispatchHeaders(
                any(), eq("processor-v1"), anyString(), any(), anyString(), anyString());
    }

    @Test
    void rejectsMismatchedOrInvalidDiscoveryResponses() {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenAnswer(ignored -> successfulResponse("other", "model-a"));

        assertThatThrownBy(() -> service.discoverParameterValues(definition))
                .isInstanceOf(ActionParameterValueDiscoveryException.class)
                .hasMessageContaining("processor id mismatch");

        when(httpResponse.body()).thenAnswer(ignored -> successfulResponse("processor-v1", 7));
        assertThatThrownBy(() -> service.discoverParameterValues(definition))
                .isInstanceOf(ActionParameterValueDiscoveryException.class)
                .hasMessageContaining("value must be a string");
    }

    private String successfulResponse(String processorId, Object value) {
        return """
                {
                  "status": "ok",
                  "protocolVersion": 1,
                  "requestId": "%s",
                  "processorId": "%s",
                  "values": {
                    "models": [{"value": %s, "label": "Model A"}]
                  }
                }
                """.formatted(
                requestId.get(),
                processorId,
                value instanceof String ? "\"" + value + "\"" : value
        );
    }

    private String dynamicDefinitionJson() {
        return """
                {
                  "version": 1,
                  "id": "processor-v1",
                  "endpoint": {
                    "url": "http://processor.localhost/dispatch",
                    "parameterValuesUrl": "http://processor.localhost/parameter-values",
                    "auth": {"type": "hmac", "secretRef": "processor-v1"}
                  },
                  "parameters": {
                    "model": {
                      "type": "string",
                      "default": "model-a",
                      "allowedValues": {"provider": "models"}
                    }
                  }
                }
                """;
    }

    private String validStaticYaml() {
        return """
                version: 1
                id: processor-v1
                name: Processor
                endpoint:
                  url: http://processor.localhost/dispatch
                  parameterValuesUrl: http://processor.localhost/parameter-values
                inputs:
                  xml: true
                outputs:
                  xml:
                    enabled: true
                parameters:
                  iterations:
                    type: integer
                    default: 2
                    allowedValues:
                      values:
                        - value: 1
                          label: One
                        - value: 2
                          label: Two
                """;
    }

    private String dynamicYamlWithoutEndpoint() {
        return """
                version: 1
                id: processor-v1
                name: Processor
                endpoint:
                  url: http://processor.localhost/dispatch
                inputs:
                  xml: true
                outputs:
                  xml:
                    enabled: true
                parameters:
                  model:
                    type: string
                    default: model-a
                    allowedValues:
                      provider: models
                """;
    }
}

package de.uniwue.zpd.dachs.larex.backend.service.action;

import de.uniwue.zpd.dachs.larex.backend.config.ActionProperties;
import de.uniwue.zpd.dachs.larex.backend.config.security.GlobalAdminService;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionProcessorAssignmentRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionProcessorDefinitionRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionProcessorWorkspaceAvailabilityRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionRunDismissalRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionRunLogEventRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActionDefinitionServicePreflightTest {

    private static final String HEADER_AUTH = "X-LAREX-Action-Auth";
    private static final String HEADER_SIGNATURE = "X-LAREX-Action-Signature";

    private ActionProcessorDefinitionRepository definitionRepository;
    private GlobalAdminService globalAdminService;
    private ActionEndpointAuthService endpointAuthService;
    private HttpClient httpClient;
    private HttpResponse<String> httpResponse;
    private ActionDefinitionService service;
    private ActionProcessorDefinition definition;
    private final AtomicReference<String> signedRequestId = new AtomicReference<>();

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() throws Exception {
        definitionRepository = mock(ActionProcessorDefinitionRepository.class);
        globalAdminService = mock(GlobalAdminService.class);
        endpointAuthService = mock(ActionEndpointAuthService.class);
        httpClient = mock(HttpClient.class);
        httpResponse = mock(HttpResponse.class);

        org.mockito.Mockito.lenient().when(globalAdminService.isGlobalAdmin()).thenReturn(true);
        org.mockito.Mockito.lenient().when(endpointAuthService.buildDispatchHeaders(
                any(), eq("processor-v1"), anyString(), any(), anyString(), anyString()
        )).thenAnswer(invocation -> {
            signedRequestId.set(invocation.getArgument(2));
            return Map.of(
                    HEADER_AUTH, "hmac-sha256;v=1",
                    HEADER_SIGNATURE, "v1=test-signature"
            );
        });
        org.mockito.Mockito.lenient().doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

        definition = new ActionProcessorDefinition();
        definition.setId("definition-1");
        definition.setProcessorKey("processor-v1");
        definition.setEndpointTimeoutSeconds(30);
        definition.setOutputsFiles(true);
        definition.setParsedJson("""
                {
                  "version": 1,
                  "id": "processor-v1",
                  "endpoint": {
                    "url": "http://processor.localhost/dispatch",
                    "preflightUrl": "http://processor.localhost/preflight",
                    "auth": {
                      "type": "hmac",
                      "secretRef": "processor-v1"
                    }
                  }
                }
                """);
        org.mockito.Mockito.lenient().when(definitionRepository.findById("definition-1")).thenReturn(Optional.of(definition));

        service = new ActionDefinitionService(
                definitionRepository,
                mock(ActionProcessorWorkspaceAvailabilityRepository.class),
                mock(ActionProcessorAssignmentRepository.class),
                mock(ActionRunRepository.class),
                mock(ActionRunDismissalRepository.class),
                mock(ActionRunLogEventRepository.class),
                globalAdminService,
                endpointAuthService,
                mock(ActionAuditService.class),
                new ObjectMapper(),
                new ActionProperties(),
                mock(ActionWorkspaceActivationService.class),
                httpClient
        );
    }

    @Test
    void parsesNormalizedImageTypesAndRejectsInvalidDeclarations() {
        when(endpointAuthService.normalizeAuthType(any())).thenReturn("hmac");
        var typed = new ActionProcessorDefinition();
        typed.setParsedJson("{\"targets\":[\"PAGE\"],\"inputs\":{\"images\":{\"level\":\"required\",\"imageTypes\":[\" binary \",\"BINARY\",\"grayscale\"]}}}");
        assertThat(service.readInputRequirements(typed).images().imageTypes()).containsExactly(
                de.uniwue.zpd.dachs.larex.backend.entity.ImageType.BINARY,
                de.uniwue.zpd.dachs.larex.backend.entity.ImageType.GRAYSCALE);
        String yaml = """
                version: 1
                id: typed-processor
                name: Typed processor
                endpoint:
                  url: http://processor.localhost/dispatch
                  auth: {type: hmac, secretRef: processor-v1}
                inputs:
                  images: {level: required, imageTypes: %s}
                outputs:
                  images: {enabled: true, variant: binary}
                """;
        for (String invalid : java.util.List.of("[]", "[UNKNOWN]", "[sepia]", "binary", "[null]")) {
            assertThat(service.validateYaml(yaml.formatted(invalid), null).diagnostics())
                    .anyMatch(diagnostic -> diagnostic.path().equals("inputs.images.imageTypes"));
        }
        assertThat(service.validateYaml(yaml.formatted("[BINARY]").replace("level: required", "level: none"), null).diagnostics())
                .anyMatch(diagnostic -> diagnostic.path().equals("inputs.images.imageTypes"));
        assertThat(service.validateYaml(yaml.formatted("[BINARY]").replace("images: {level:", "xml: {level:"), null).diagnostics())
                .anyMatch(diagnostic -> diagnostic.path().equals("inputs.xml.imageTypes"));
    }

    @Test
    void performsSignedPreflightAndValidatesProtocolIdentityAndCapabilities() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenAnswer(ignored -> successfulResponse("processor-v1", true));

        ActionDto.HealthCheckResponse result = service.testEndpoint("definition-1");

        assertThat(result.ok()).isTrue();
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.url()).isEqualTo("http://processor.localhost/preflight");
        assertThat(result.message()).contains("identity and capabilities verified");

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any());
        HttpRequest request = requestCaptor.getValue();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.uri().toString()).isEqualTo("http://processor.localhost/preflight");
        assertThat(request.headers().firstValue(HEADER_AUTH))
                .contains("hmac-sha256;v=1");
        assertThat(request.headers().firstValue(HEADER_SIGNATURE))
                .contains("v1=test-signature");
        verify(endpointAuthService).buildDispatchHeaders(
                any(), eq("processor-v1"), eq(signedRequestId.get()), any(), anyString(), anyString()
        );
    }

    @Test
    void rejectsAProcessorIdentityMismatch() {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenAnswer(ignored -> successfulResponse("different-processor", true));

        ActionDto.HealthCheckResponse result = service.testEndpoint("definition-1");

        assertThat(result.ok()).isFalse();
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.message())
                .isEqualTo("Processor id mismatch: expected processor-v1 but received different-processor");
    }

    @Test
    void rejectsMissingCustomFileCapabilityForFileProducingAction() {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenAnswer(ignored -> successfulResponse("processor-v1", false));

        ActionDto.HealthCheckResponse result = service.testEndpoint("definition-1");

        assertThat(result.ok()).isFalse();
        assertThat(result.message()).isEqualTo(
                "Processor does not advertise required capability customFileResults"
        );
    }

    @Test
    void includesBoundedProcessorErrorDetail() {
        when(httpResponse.statusCode()).thenReturn(401);
        when(httpResponse.body()).thenReturn("{\"detail\":\"signature mismatch\"}");

        ActionDto.HealthCheckResponse result = service.testEndpoint("definition-1");

        assertThat(result.ok()).isFalse();
        assertThat(result.statusCode()).isEqualTo(401);
        assertThat(result.message()).isEqualTo(
                "Authenticated preflight returned HTTP 401: signature mismatch"
        );
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void fileEvaluationRequiresCustomFileCapabilityWithoutReportCapability(boolean supported) {
        definition.setActionKind(ActionProcessorDefinition.ActionKind.EVALUATION);
        definition.setOutputsFiles(false);
        definition.setParsedJson(definition.getParsedJson().replace("\"version\": 1,", "\"version\": 1, \"evaluation\": {\"outputMode\":\"FILES\",\"splits\":{\"TEST\":\"required\"}},"));
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenAnswer(ignored -> successfulResponse("processor-v1", supported));
        var result = service.testEndpoint("definition-1");
        assertThat(result.ok()).isEqualTo(supported);
        if (!supported) assertThat(result.message()).contains("customFileResults");
    }

    @Test
    void yamlSupportsFileModeWithoutProfileAndDefaultsExistingReports() {
        when(endpointAuthService.normalizeAuthType(any())).thenReturn("hmac");
        when(endpointAuthService.hasSecret("processor-v1")).thenReturn(true);
        String yaml = """
                version: 1
                id: evaluation-example
                name: Evaluation
                kind: EVALUATION
                endpoint:
                  url: http://processor.localhost/dispatch
                  preflightUrl: http://processor.localhost/preflight
                  auth:
                    type: hmac
                    secretRef: processor-v1
                targets: [PAGE]
                locking:
                  mode: NONE
                inputs:
                  images: {level: required}
                  xml: {level: required}
                evaluation:
                  outputMode: FILES
                  splits: {TEST: required}
                """;
        var files = service.validateYaml(yaml, null);
        assertThat(files.diagnostics()).isEmpty();
        assertThat(files.valid()).isTrue();
        assertThat(files.preview().evaluation().outputMode()).isEqualTo(ActionDto.EvaluationOutputMode.FILES);
        var reports = service.validateYaml(yaml.replace("outputMode: FILES", "profile: example.metrics"), null);
        assertThat(reports.valid()).isTrue();
        assertThat(reports.preview().evaluation().outputMode()).isEqualTo(ActionDto.EvaluationOutputMode.REPORT);
        var invalid = service.validateYaml(yaml.replace("FILES", "OTHER"), null);
        assertThat(invalid.valid()).isFalse();
        assertThat(invalid.diagnostics()).anyMatch(diagnostic -> diagnostic.path().equals("evaluation.outputMode"));
    }

    private String successfulResponse(String processorId, boolean customFileResults) {
        return """
                {
                  "status": "ok",
                  "protocolVersion": 1,
                  "requestId": "%s",
                  "processorId": "%s",
                  "capabilities": {
                    "incrementalPageResults": true,
                    "customFileResults": %s
                  }
                }
                """.formatted(signedRequestId.get(), processorId, customFileResults);
    }
}

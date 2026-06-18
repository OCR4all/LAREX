package de.uniwue.zpd.dachs.larex.backend.service.action;

import de.uniwue.zpd.dachs.larex.backend.config.ActionProperties;
import de.uniwue.zpd.dachs.larex.backend.config.security.GlobalAdminService;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionProcessorAssignmentRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionProcessorDefinitionRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionProcessorWorkspaceAvailabilityRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionRunDismissalRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionRunLogEventRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActionDefinitionServiceEndpointSecretValidationTest {

    private ActionEndpointAuthService endpointAuthService;
    private ActionDefinitionService service;

    @BeforeEach
    void setUp() {
        ActionProcessorDefinitionRepository definitionRepository = mock(ActionProcessorDefinitionRepository.class);
        when(definitionRepository.findByProcessorKey("external-processor")).thenReturn(Optional.empty());
        endpointAuthService = mock(ActionEndpointAuthService.class);
        when(endpointAuthService.envNameForSecretRef("processor-v1"))
                .thenReturn("LAREX_ACTION_ENDPOINT_SECRET_PROCESSOR_V1");
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
                new ActionProperties()
        );
    }

    @Test
    void acceptsHmacDefinitionWhenSecretExists() {
        when(endpointAuthService.normalizeAuthType(new de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDefinitionDocument.EndpointAuth("hmac", "processor-v1")))
                .thenCallRealMethod();
        when(endpointAuthService.hasSecret("processor-v1")).thenReturn(true);

        assertThatCode(() -> service.parseAndValidate(validExternalYaml(), null))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsHmacDefinitionWhenSecretIsMissing() {
        when(endpointAuthService.normalizeAuthType(new de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDefinitionDocument.EndpointAuth("hmac", "processor-v1")))
                .thenCallRealMethod();
        when(endpointAuthService.hasSecret("processor-v1")).thenReturn(false);

        assertThatThrownBy(() -> service.parseAndValidate(validExternalYaml(), null))
                .isInstanceOf(ActionDefinitionService.ValidationException.class)
                .satisfies(error -> {
                    ActionDefinitionService.ValidationException validationException =
                            (ActionDefinitionService.ValidationException) error;
                    org.assertj.core.api.Assertions.assertThat(validationException.diagnostics())
                            .anySatisfy(diagnostic -> org.assertj.core.api.Assertions.assertThat(diagnostic.message())
                                    .contains("admin-managed secret")
                                    .contains("LAREX_ACTION_ENDPOINT_SECRET_PROCESSOR_V1"));
                });
    }

    private String validExternalYaml() {
        return """
                version: 1
                id: external-processor
                name: External Processor
                category: WORKFLOW
                targets:
                  - PAGE
                endpoint:
                  url: https://processor.example.org/dispatch
                  auth:
                    type: hmac
                    secretRef: processor-v1
                access:
                  execute: CURATOR
                locking:
                  mode: PAGES
                inputs:
                  images: true
                  xml: false
                outputs:
                  xml:
                    enabled: true
                    mode: upsert
                  images:
                    enabled: false
                    variant: external-processor
                    mode: upsert
                """;
    }
}

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
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
    void parsesTargetAwareInputRequirementsAndLegacyBooleans() {
        when(endpointAuthService.normalizeAuthType(new de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDefinitionDocument.EndpointAuth("hmac", "processor-v1")))
                .thenCallRealMethod();
        when(endpointAuthService.hasSecret("processor-v1")).thenReturn(true);

        ActionDefinitionService.ParsedDefinition legacy = service.parseAndValidate(validExternalYaml(), null);
        assertThat(legacy.preview().inputs().images().level()).isEqualTo(ActionDto.InputLevel.OPTIONAL);
        assertThat(legacy.preview().inputs().xml().level()).isEqualTo(ActionDto.InputLevel.NONE);

        ActionDefinitionService.ParsedDefinition targetAware = service.parseAndValidate(
                targetAwareExternalYaml(),
                null
        );
        assertThat(targetAware.preview().inputs().images().level()).isEqualTo(ActionDto.InputLevel.REQUIRED);
        assertThat(targetAware.preview().inputs().xml().level()).isEqualTo(ActionDto.InputLevel.OPTIONAL);
        assertThat(targetAware.preview().inputs().xml().requiredForTargets())
                .containsExactly(ActionProcessorDefinition.ActionTarget.REGION);
    }

    @Test
    void rejectsInputRequirementForUnsupportedTarget() {
        when(endpointAuthService.normalizeAuthType(new de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDefinitionDocument.EndpointAuth("hmac", "processor-v1")))
                .thenCallRealMethod();
        when(endpointAuthService.hasSecret("processor-v1")).thenReturn(true);

        assertThatThrownBy(() -> service.parseAndValidate(
                targetAwareExternalYaml().replace("targets:\n  - PAGE\n  - REGION\n", "targets:\n  - PAGE\n"),
                null
        ))
                .isInstanceOf(ActionDefinitionService.ValidationException.class)
                .satisfies(error -> assertThat(((ActionDefinitionService.ValidationException) error).diagnostics())
                        .anySatisfy(diagnostic -> assertThat(diagnostic.message())
                                .isEqualTo("target must also be declared in targets")));
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

    private String targetAwareExternalYaml() {
        return validExternalYaml()
                .replace("targets:\n  - PAGE\n", "targets:\n  - PAGE\n  - REGION\n")
                .replace(
                        "inputs:\n  images: true\n  xml: false\n",
                        "inputs:\n"
                                + "  images:\n"
                                + "    level: required\n"
                                + "  xml:\n"
                                + "    level: optional\n"
                                + "    requiredForTargets:\n"
                                + "      - REGION\n"
                );
    }
}

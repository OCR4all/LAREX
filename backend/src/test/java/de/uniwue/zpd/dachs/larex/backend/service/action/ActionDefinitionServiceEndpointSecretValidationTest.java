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
                new ActionProperties(),
                mock(ActionWorkspaceActivationService.class)
        );
    }

    @Test
    void normalizesTagsAndReturnsThemFromStoredDefinition() {
        configureSecret();
        var parsed = service.parseAndValidate(validExternalYaml().replace("tags: []", "tags: [' layout ', segmentation, layout, Layout]"), null);
        assertThat(parsed.preview().tags()).containsExactly("layout", "segmentation", "Layout");
        var definition = new ActionProcessorDefinition();
        definition.setParsedJson(parsed.parsedJson());
        assertThat(service.readParsedDocument(definition).tags()).containsExactly("layout", "segmentation", "Layout");
        assertThat(service.toDefinitionResponse(definition).tags()).containsExactly("layout", "segmentation", "Layout");
        assertThat(new ObjectMapper().readTree(parsed.parsedJson()).has("category")).isFalse();
        assertThat(service.parseAndValidate(validExternalYaml().replace("tags: []", ""), null).preview().tags()).isEmpty();
        assertThat(service.parseAndValidate(validExternalYaml(), null).preview().tags()).isEmpty();
    }

    @Test
    void rejectsNonStringTagsAndRemovedCategory() {
        configureSecret();
        for (String invalid : java.util.List.of("layout", "null", "{}", "[null]", "[' ' ]", "[42]", "[true]", "[{label: layout}]", "[[layout]]")) {
            assertThatThrownBy(() -> service.parseAndValidate(validExternalYaml().replace("tags: []", "tags: " + invalid), null))
                    .as(invalid).isInstanceOf(ActionDefinitionService.ValidationException.class);
        }
        assertThatThrownBy(() -> service.parseAndValidate(validExternalYaml().replace("tags: []", "category: WORKFLOW"), null))
                .isInstanceOf(ActionDefinitionService.ValidationException.class);
    }

    @Test
    void migratedDefinitionsRemainReadableAndEditable() {
        configureSecret();
        String legacy = validExternalYaml().replace("tags: []", "category: WORKFLOW");
        String migratedYaml = db.migration.V35__replace_action_categories_with_tags.migrateYaml(legacy);
        var parsed = service.parseAndValidate(migratedYaml, null);
        String legacyJson = new ObjectMapper().readTree(parsed.parsedJson()).toString().replace("\"tags\":[]", "\"category\":\"WORKFLOW\"");
        var definition = new ActionProcessorDefinition();
        definition.setParsedJson(db.migration.V35__replace_action_categories_with_tags.migrateJson(legacyJson));
        assertThat(service.readParsedDocument(definition).tags()).isEmpty();
        assertThat(service.toDefinitionResponse(definition).tags()).isEmpty();
        assertThat(service.readParsedDocument(definition).endpoint()).isEqualTo(parsed.document().endpoint());
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

    @Test
    void acceptsTrainingDefinitionWithoutOutputsAndParsesSplits() {
        when(endpointAuthService.normalizeAuthType(new de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDefinitionDocument.EndpointAuth("hmac", "processor-v1")))
                .thenCallRealMethod();
        when(endpointAuthService.hasSecret("processor-v1")).thenReturn(true);

        ActionDefinitionService.ParsedDefinition parsed = service.parseAndValidate(trainingYaml(), null);

        assertThat(parsed.preview().kind()).isEqualTo(ActionProcessorDefinition.ActionKind.TRAINING);
        assertThat(parsed.preview().lockMode()).isEqualTo(ActionProcessorDefinition.LockMode.NONE);
        assertThat(parsed.preview().trainingSplits().train()).isEqualTo(ActionDto.InputLevel.REQUIRED);
        assertThat(parsed.preview().trainingSplits().val()).isEqualTo(ActionDto.InputLevel.OPTIONAL);
        assertThat(parsed.preview().trainingSplits().test()).isEqualTo(ActionDto.InputLevel.NONE);
        assertThat(parsed.preview().outputsFiles()).isFalse();
    }

    @Test
    void rejectsTrainingOutputsAndProcessingNoneLock() {
        when(endpointAuthService.normalizeAuthType(new de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDefinitionDocument.EndpointAuth("hmac", "processor-v1")))
                .thenCallRealMethod();
        when(endpointAuthService.hasSecret("processor-v1")).thenReturn(true);

        assertThatThrownBy(() -> service.parseAndValidate(trainingYaml() + "\noutputs:\n  files:\n    enabled: true\n", null))
                .isInstanceOf(ActionDefinitionService.ValidationException.class);
        assertThatThrownBy(() -> service.parseAndValidate(validExternalYaml().replace("mode: PAGES", "mode: NONE"), null))
                .isInstanceOf(ActionDefinitionService.ValidationException.class);
    }

    @Test
    void parsesOverwriteDeclarationsAndDistinguishesMissingFromEmpty() {
        configureSecret();
        var missing = service.parseAndValidate(validExternalYaml(), null).preview();
        assertThat(missing.overwrites()).isEmpty();
        var empty = service.parseAndValidate(withOverwrites("PAGE: []"), null).preview();
        assertThat(empty.overwrites()).containsKey(ActionProcessorDefinition.ActionTarget.PAGE);
        assertThat(empty.overwrites().get(ActionProcessorDefinition.ActionTarget.PAGE)).isEmpty();
        var declared = service.parseAndValidate(withOverwrites("PAGE: [REGIONS, TEXT, REGIONS]"), null).preview();
        assertThat(declared.overwrites().get(ActionProcessorDefinition.ActionTarget.PAGE))
                .containsExactly(ActionDto.AnnotationLevel.REGIONS, ActionDto.AnnotationLevel.TEXT);
    }

    @Test
    void rejectsInvalidOverwriteDeclarations() {
        configureSecret();
        for (String invalid : java.util.List.of("PAGE: [METADATA]", "REGION: [TEXT]", "UNKNOWN: []", "PAGE: null")) {
            assertThatThrownBy(() -> service.parseAndValidate(withOverwrites(invalid), null))
                    .isInstanceOf(ActionDefinitionService.ValidationException.class);
        }
        assertThatThrownBy(() -> service.parseAndValidate(withOverwrites("PAGE: []")
                .replace("enabled: true", "enabled: false"), null))
                .isInstanceOf(ActionDefinitionService.ValidationException.class);
    }

    @Test
    void rejectsRemovedOutputModeAsAnUnknownField() {
        configureSecret();
        for (String output : java.util.List.of("xml", "images")) {
            for (String value : java.util.List.of("upsert", "append", "replace", "null")) {
                for (boolean enabled : java.util.List.of(true, false)) {
                    String yaml = validExternalYaml().replace("  " + output + ":\n",
                            "  " + output + ":\n    mode: " + value + "\n");
                    if (output.equals("xml") && !enabled) yaml = yaml.replace("    enabled: true", "    enabled: false");
                    if (output.equals("images") && enabled) yaml = yaml.replace("    enabled: false", "    enabled: true");
                    String candidate = yaml;
                    assertThatThrownBy(() -> service.parseAndValidate(candidate, null))
                            .as("%s mode=%s enabled=%s", output, value, enabled)
                            .isInstanceOf(ActionDefinitionService.ValidationException.class)
                            .satisfies(error -> assertThat(((ActionDefinitionService.ValidationException) error).diagnostics())
                                    .anySatisfy(diagnostic -> {
                                        assertThat(diagnostic.path()).isEqualTo("outputs." + output + ".mode");
                                        assertThat(diagnostic.message()).contains("Unrecognized property", "mode");
                                    }));
                }
            }
        }
    }

    @Test
    void serializedDefinitionsHaveNoOutputModeAndRetainLockingAndOverwriteDeclarations() {
        configureSecret();
        var parsed = service.parseAndValidate(withOverwrites("PAGE: [REGIONS]"), null);
        var json = new ObjectMapper().readTree(parsed.parsedJson());
        assertThat(json.path("outputs").path("xml").has("mode")).isFalse();
        assertThat(json.path("outputs").path("images").has("mode")).isFalse();
        assertThat(json.path("locking").path("mode").asString()).isEqualTo("PAGES");
        assertThat(parsed.preview().overwrites().get(ActionProcessorDefinition.ActionTarget.PAGE))
                .containsExactly(ActionDto.AnnotationLevel.REGIONS);
        var definition = new ActionProcessorDefinition();
        definition.setParsedJson(parsed.parsedJson());
        assertThat(service.readParsedDocument(definition).outputs().xml().overwrites())
                .containsEntry("PAGE", java.util.List.of("REGIONS"));
    }

    @Test
    void rejectsRemovedOutputModeInStoredJsonIncludingNullValues() {
        configureSecret();
        String parsedJson = service.parseAndValidate(validExternalYaml(), null).parsedJson();
        for (String output : java.util.List.of("xml", "images")) {
            for (String value : java.util.List.of("\"upsert\"", "null")) {
                var definition = new ActionProcessorDefinition();
                definition.setParsedJson(parsedJson.replace("\"" + output + "\":{",
                        "\"" + output + "\":{\"mode\":" + value + ","));
                assertThatThrownBy(() -> service.readParsedDocument(definition))
                        .as("stored %s mode=%s", output, value)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("Stored Action definition is invalid");
            }
        }
    }

    @Test
    void continuesToValidateImageVariants() {
        configureSecret();
        String imageYaml = validExternalYaml().replace("enabled: false", "enabled: true");
        assertThatCode(() -> service.parseAndValidate(imageYaml, null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.parseAndValidate(imageYaml.replace("variant: external-processor", "variant: invalid variant"), null))
                .isInstanceOf(ActionDefinitionService.ValidationException.class)
                .satisfies(error -> assertThat(((ActionDefinitionService.ValidationException) error).diagnostics())
                        .anySatisfy(diagnostic -> assertThat(diagnostic.path()).isEqualTo("outputs.images.variant")));
    }

    private void configureSecret() {
        when(endpointAuthService.normalizeAuthType(new de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDefinitionDocument.EndpointAuth("hmac", "processor-v1")))
                .thenCallRealMethod();
        when(endpointAuthService.hasSecret("processor-v1")).thenReturn(true);
    }

    private String withOverwrites(String declaration) {
        return validExternalYaml().replace("    enabled: true\n", "    enabled: true\n    overwrites:\n      " + declaration + "\n");
    }

    private String validExternalYaml() {
        return """
                version: 1
                id: external-processor
                name: External Processor
                tags: []
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
                  images:
                    enabled: false
                    variant: external-processor
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

    private String trainingYaml() {
        return """
                version: 1
                id: external-processor
                name: External Training
                kind: TRAINING
                tags: [layout]
                targets: [PAGE]
                endpoint:
                  url: https://processor.example.org/dispatch
                  auth:
                    type: hmac
                    secretRef: processor-v1
                access:
                  execute: CURATOR
                locking:
                  mode: NONE
                inputs:
                  images:
                    level: required
                  xml:
                    level: required
                training:
                  splits:
                    TRAIN: required
                    VAL: optional
                    TEST: none
                """;
    }
}

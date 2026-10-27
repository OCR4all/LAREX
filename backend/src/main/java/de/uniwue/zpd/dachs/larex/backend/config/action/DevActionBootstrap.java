package de.uniwue.zpd.dachs.larex.backend.config.action;

import de.uniwue.zpd.dachs.larex.backend.config.ActionProperties;
import de.uniwue.zpd.dachs.larex.backend.config.ActionProperties.DevProcessor;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionProcessorDefinitionRepository;
import de.uniwue.zpd.dachs.larex.backend.service.action.ActionDefinitionService;
import de.uniwue.zpd.dachs.larex.backend.service.action.ActionDefinitionService.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
public class DevActionBootstrap implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(DevActionBootstrap.class);
    private static final String BOOTSTRAP_USER_ID = "dev-bootstrap";
    private static final String MOCK_PROCESSOR_KEY = "mock-image-copy";
    private static final String MOCK_TRAINING_PROCESSOR_KEY = "mock-training";
    private static final String MOCK_OCR_EVALUATION_PROCESSOR_KEY = "mock-ocr-evaluation";
    private static final String MOCK_LAYOUT_EVALUATION_PROCESSOR_KEY = "mock-layout-evaluation";
    private static final String KRAKEN_PROCESSOR_KEY = "kraken-segmentation";
    private static final String KRAKEN_TRAINING_PROCESSOR_KEY = "kraken-layout-training";

    private final ActionDefinitionService actionDefinitionService;
    private final ActionProcessorDefinitionRepository definitionRepository;
    private final ActionProperties actionProperties;

    public DevActionBootstrap(ActionDefinitionService actionDefinitionService,
                              ActionProcessorDefinitionRepository definitionRepository,
                              ActionProperties actionProperties) {
        this.actionDefinitionService = actionDefinitionService;
        this.definitionRepository = definitionRepository;
        this.actionProperties = actionProperties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        DevProcessor mockProcessor = actionProperties.getDev().getMockProcessor();
        upsertDevAction(
                MOCK_PROCESSOR_KEY,
                mockProcessor.isEnabled(),
                mockProcessorYaml(
                        mockProcessor.getEndpointUrl(),
                        mockProcessor.getHealthUrl(),
                        mockProcessor.getPreflightUrl()
                )
        );

        upsertDevAction(
                MOCK_OCR_EVALUATION_PROCESSOR_KEY,
                mockProcessor.isEnabled(),
                mockEvaluationYaml(
                        MOCK_OCR_EVALUATION_PROCESSOR_KEY,
                        "Mock OCR Evaluation",
                        "larex.ocr-recognition",
                        replaceEndpoint(mockProcessor.getEndpointUrl(), "/evaluation/ocr/dispatch"),
                        replaceEndpoint(mockProcessor.getHealthUrl(), "/evaluation/ocr/health"),
                        replaceEndpoint(mockProcessor.getPreflightUrl(), "/evaluation/ocr/preflight"),
                        "ocr, htr", "[BINARY]"
                )
        );
        bootstrapWorkflowMocks(mockProcessor);
        upsertDevAction(
                MOCK_LAYOUT_EVALUATION_PROCESSOR_KEY,
                mockProcessor.isEnabled(),
                mockEvaluationYaml(
                        MOCK_LAYOUT_EVALUATION_PROCESSOR_KEY,
                        "Mock Layout Evaluation",
                        "larex.layout-segmentation",
                        replaceEndpoint(mockProcessor.getEndpointUrl(), "/evaluation/layout/dispatch"),
                        replaceEndpoint(mockProcessor.getHealthUrl(), "/evaluation/layout/health"),
                        replaceEndpoint(mockProcessor.getPreflightUrl(), "/evaluation/layout/preflight"),
                        "layout", "[COLOR, GRAYSCALE]"
                )
        );

        DevProcessor integration = actionProperties.getDev().getMockIntegrationProcessor();
        upsertDevAction("mock-integration", integration.isEnabled(), mockIntegrationYaml(
                integration.getEndpointUrl(), integration.getHealthUrl(), integration.getPreflightUrl()));

        DevProcessor mockTrainingProcessor = actionProperties.getDev().getMockTrainingProcessor();
        upsertDevAction(
                MOCK_TRAINING_PROCESSOR_KEY,
                mockTrainingProcessor.isEnabled(),
                mockTrainingProcessorYaml(
                        mockTrainingProcessor.getEndpointUrl(),
                        mockTrainingProcessor.getHealthUrl(),
                        mockTrainingProcessor.getPreflightUrl()
                )
        );

        DevProcessor krakenProcessor = actionProperties.getDev().getKrakenProcessor();
        upsertDevAction(
                KRAKEN_PROCESSOR_KEY,
                krakenProcessor.isEnabled(),
                krakenProcessorYaml(
                        krakenProcessor.getEndpointUrl(),
                        krakenProcessor.getHealthUrl(),
                        krakenProcessor.getPreflightUrl()
                )
        );

        DevProcessor krakenTrainingProcessor = actionProperties.getDev().getKrakenTrainingProcessor();
        upsertDevAction(
                KRAKEN_TRAINING_PROCESSOR_KEY,
                krakenTrainingProcessor.isEnabled(),
                krakenTrainingProcessorYaml(
                        krakenTrainingProcessor.getEndpointUrl(),
                        krakenTrainingProcessor.getHealthUrl(),
                        krakenTrainingProcessor.getPreflightUrl()
                )
        );
    }

    private void upsertDevAction(String processorKey, boolean enabled, String yaml) {
        if (!enabled) {
            logger.info("Dev Action bootstrap for '{}' is disabled", processorKey);
            return;
        }

        ActionProcessorDefinition existing = definitionRepository.findByProcessorKey(processorKey).orElse(null);
        if (existing != null && !BOOTSTRAP_USER_ID.equals(existing.getCreatedByUserId())) {
            logger.info("Skipping dev Action bootstrap because '{}' is user-managed", processorKey);
            return;
        }

        try {
            actionDefinitionService.upsertSystemDefinition(
                    processorKey,
                    yaml,
                    true,
                    true,
                    BOOTSTRAP_USER_ID
            );
        } catch (ValidationException exception) {
            logger.error("Dev Action '{}' failed validation: {}", processorKey, exception.diagnostics());
            throw exception;
        }
        logger.info("Dev Action '{}' is available globally", processorKey);
    }

    private String mockProcessorYaml(String endpointUrl, String healthUrl, String preflightUrl) {
        return """
                version: 1
                id: mock-image-copy
                name: Mock Image and XML Copy
                description: Development Action that copies selected page images and XML back into LAREX.
                tags: []
                targets:
                  - PAGE
                  - REGION
                  - TEXT_LINE

                endpoint:
                  url: %s
                  healthUrl: %s
                  preflightUrl: %s
                  timeoutSeconds: 30
                  auth:
                    type: hmac
                    secretRef: mock-processor-v1

                access:
                  execute: CURATOR

                locking:
                  mode: PAGES

                inputs:
                  images:
                    level: optional
                  xml:
                    level: optional

                outputs:
                  xml:
                    enabled: true
                  images:
                    enabled: true
                    variant: action-copy
                  files:
                    enabled: true

                parameters:
                  failEveryNthPage:
                    type: number
                    default: 0
                    description: Simulate a recoverable failure for every Nth page; 0 disables failures.

                concurrency:
                  maxActiveRuns: 2
                  scope: PROJECT
                """.formatted(endpointUrl, healthUrl, preflightUrl);
    }

    private void bootstrapWorkflowMocks(DevProcessor processor) {
        upsertWorkflowMock(processor, "binarize", "mock-binarization", "Mock Binarization",
                "Thresholds a color or grayscale image into the binary binarized variant.", "[COLOR, GRAYSCALE]", false, "[]", """
                threshold:
                  type: integer
                  default: 128
                  min: 0
                  max: 255
                  description: Grayscale threshold for black and white pixels.
                """);
        upsertWorkflowMock(processor, "region", "mock-whole-page-region", "Mock Whole-page Region",
                "Requires a binary image. Replaces layout with one full-page text region and creates PAGE XML when missing.", "[BINARY]", false, "[REGIONS, READING_ORDER]", "{}");
        upsertWorkflowMock(processor, "line", "mock-single-text-line", "Mock Single Text Line",
                "Requires a binary image and an existing text region. Adds one text line and baseline.", "[BINARY]", true, "[TEXT_LINES, TEXT]", "{}");
        upsertWorkflowMock(processor, "ocr", "mock-random-ocr", "Mock Random OCR",
                "Requires a binary image and at least one text line. Writes sample words into existing text lines.", "[BINARY]", true, "[TEXT, WORDS]", """
                wordCount:
                  type: integer
                  default: 8
                  min: 1
                  max: 100
                  description: Number of sample words per text line.
                seed:
                  type: integer
                  default: 42
                  min: 0
                  max: 2147483647
                  description: Change the seed to generate different sample text.
                """);
    }

    private void upsertWorkflowMock(DevProcessor processor, String operation, String key, String name,
                                   String description, String imageTypes, boolean requiredXml,
                                   String overwrites, String parameters) {
        String prefix = "/workflow/" + operation;
        String outputs = operation.equals("binarize")
                ? "images:\n  enabled: true\n  variant: binarized\n"
                : "xml:\n  enabled: true\n  overwrites:\n    PAGE: " + overwrites + "\n";
        String yaml = """
                version: 1
                id: %s
                name: %s
                description: %s
                tags: [mock, workflow]
                targets: [PAGE]
                endpoint:
                  url: %s
                  healthUrl: %s
                  preflightUrl: %s
                  timeoutSeconds: 30
                  auth:
                    type: hmac
                    secretRef: mock-processor-v1
                access:
                  execute: CURATOR
                locking:
                  mode: PAGES
                inputs:
                  images:
                    level: required
                    imageTypes: %s
                  xml:
                    level: %s
                outputs:
                %s
                parameters:
                %s
                concurrency:
                  maxActiveRuns: 2
                  scope: PROJECT
                """.formatted(key, name, description,
                replaceEndpoint(processor.getEndpointUrl(), prefix + "/dispatch"),
                replaceEndpoint(processor.getHealthUrl(), prefix + "/health"),
                replaceEndpoint(processor.getPreflightUrl(), prefix + "/preflight"),
                imageTypes, requiredXml ? "required" : operation.equals("region") ? "optional" : "none",
                outputs.indent(2).stripTrailing(), parameters.indent(2).stripTrailing());
        upsertDevAction(key, processor.isEnabled(), yaml);
    }

    private String krakenProcessorYaml(String endpointUrl, String healthUrl, String preflightUrl) {
        return """
                version: 1
                id: kraken-segmentation
                name: Kraken Segmentation
                description: Runs Kraken OCR baseline segmentation and incrementally imports PAGE XML.
                tags: [layout, segmentation]
                targets:
                  - PAGE
                  - REGION

                endpoint:
                  url: %s
                  healthUrl: %s
                  preflightUrl: %s
                  timeoutSeconds: 30
                  auth:
                    type: hmac
                    secretRef: kraken-segmentation-v1

                access:
                  execute: CURATOR

                locking:
                  mode: PAGES

                inputs:
                  images:
                    level: required
                  xml:
                    level: optional
                    requiredForTargets:
                      - REGION

                outputs:
                  xml:
                    enabled: true
                  images:
                    enabled: false

                concurrency:
                  maxActiveRuns: 1
                  scope: PROJECT
                """.formatted(endpointUrl, healthUrl, preflightUrl);
    }

    private String mockIntegrationYaml(String endpointUrl, String healthUrl, String preflightUrl) {
        return """
                version: 1
                id: mock-integration
                name: Mock Integration
                description: Requires grayscale images. Downloads frozen inputs and simulates external work with status feedback.
                kind: INTEGRATION
                tags: [integration, export]
                targets: [PAGE, REGION, TEXT_LINE]
                endpoint:
                  url: %s
                  healthUrl: %s
                  preflightUrl: %s
                  auth:
                    type: hmac
                    secretRef: mock-processor-v1
                access:
                  execute: CURATOR
                locking:
                  mode: NONE
                inputs:
                  images:
                    level: required
                    imageTypes: [GRAYSCALE]
                  xml:
                    level: optional
                    requiredForTargets: [REGION, TEXT_LINE]
                concurrency:
                  maxActiveRuns: 2
                  scope: WORKSPACE
                parameters:
                  delaySeconds:
                    type: number
                    default: 5
                    min: 0
                    max: 30
                  simulateFailure:
                    type: boolean
                    default: false
                """.formatted(endpointUrl, healthUrl, preflightUrl);
    }

    private String mockTrainingProcessorYaml(String endpointUrl, String healthUrl, String preflightUrl) {
        return """
                version: 1
                id: mock-training
                name: Mock Training
                description: Requires binary images and PAGE XML. Validates frozen dataset inputs and simulates training.
                kind: TRAINING
                tags: [training]
                targets:
                  - PAGE

                endpoint:
                  url: %s
                  healthUrl: %s
                  preflightUrl: %s
                  timeoutSeconds: 30
                  auth:
                    type: hmac
                    secretRef: mock-processor-v1

                access:
                  execute: CURATOR

                locking:
                  mode: NONE

                inputs:
                  images:
                    level: required
                    imageTypes: [BINARY]
                  xml:
                    level: required

                training:
                  splits:
                    TRAIN: required
                    VAL: optional
                    TEST: none

                concurrency:
                  maxActiveRuns: 2
                  scope: WORKSPACE

                parameters:
                  delaySeconds:
                    type: integer
                    default: 5
                    min: 1
                    max: 30
                    description: Seconds spent simulating model training.
                """.formatted(endpointUrl, healthUrl, preflightUrl);
    }

    private String krakenTrainingProcessorYaml(String endpointUrl, String healthUrl, String preflightUrl) {
        return """
                version: 1
                id: kraken-layout-training
                name: Kraken Layout Training
                description: Trains a Kraken segmentation model from frozen dataset inputs.
                kind: TRAINING
                tags: [layout, training]
                targets:
                  - PAGE

                endpoint:
                  url: %s
                  healthUrl: %s
                  preflightUrl: %s
                  timeoutSeconds: 30
                  auth:
                    type: hmac
                    secretRef: kraken-layout-training-v1

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

                concurrency:
                  maxActiveRuns: 1
                  scope: WORKSPACE

                parameters:
                  modelName:
                    type: string
                    required: true
                    description: Safe model name.
                  epochs:
                    type: integer
                    default: 50
                    min: 1
                    max: 1000
                """.formatted(endpointUrl, healthUrl, preflightUrl);
    }

    private String mockEvaluationYaml(String id, String name, String profile,
                                      String endpointUrl, String healthUrl, String preflightUrl,
                                      String tags, String imageTypes) {
        return """
                version: 1
                id: %s
                name: %s
                description: Requires %s images and PAGE XML. Returns a deterministic evaluation report.
                kind: EVALUATION
                tags: [%s, evaluation]

                endpoint:
                  url: %s
                  healthUrl: %s
                  preflightUrl: %s
                  timeoutSeconds: 30
                  auth:
                    type: hmac
                    secretRef: mock-processor-v1

                access:
                  execute: CURATOR

                locking:
                  mode: NONE

                inputs:
                  images:
                    level: required
                    imageTypes: %s
                  xml:
                    level: required

                evaluation:
                  profile: %s
                  profileVersion: 1
                  splits:
                    TRAIN: required
                    VAL: optional
                    TEST: none

                concurrency:
                  maxActiveRuns: 2
                  scope: WORKSPACE

                parameters:
                  delaySeconds:
                    type: integer
                    default: 5
                    min: 1
                    max: 30
                  quality:
                    type: number
                    default: 0.95
                    min: 0
                    max: 1
                """.formatted(id, name, imageTypes, tags, endpointUrl, healthUrl, preflightUrl, imageTypes, profile);
    }

    private String replaceEndpoint(String endpoint, String suffix) {
        if (endpoint == null || endpoint.isBlank()) {
            return endpoint;
        }
        int slash = endpoint.lastIndexOf('/');
        return slash < 0 ? endpoint + suffix : endpoint.substring(0, slash) + suffix;
    }

}

package de.uniwue.zpd.dachs.larex.backend.config.action;

import de.uniwue.zpd.dachs.larex.backend.config.ActionProperties;
import de.uniwue.zpd.dachs.larex.backend.config.ActionProperties.DevProcessor;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionProcessorDefinitionRepository;
import de.uniwue.zpd.dachs.larex.backend.service.action.ActionDefinitionService;
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
                mockProcessorYaml(mockProcessor.getEndpointUrl(), mockProcessor.getHealthUrl())
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

        actionDefinitionService.upsertSystemDefinition(
                processorKey,
                yaml,
                true,
                true,
                BOOTSTRAP_USER_ID
        );
        logger.info("Dev Action '{}' is available globally", processorKey);
    }

    private String mockProcessorYaml(String endpointUrl, String healthUrl) {
        return """
                version: 1
                id: mock-image-copy
                name: Mock Image and XML Copy
                description: Development Action that copies selected page images and XML back into LAREX.
                category: WORKFLOW
                targets:
                  - PAGE
                  - REGION
                  - TEXT_LINE

                endpoint:
                  url: %s
                  healthUrl: %s
                  timeoutSeconds: 30
                  auth:
                    type: hmac
                    secretRef: mock-processor-v1

                access:
                  execute: CURATOR

                locking:
                  mode: PAGES

                inputs:
                  images: true
                  xml: true

                outputs:
                  xml:
                    enabled: true
                    mode: upsert
                  images:
                    enabled: true
                    variant: action-copy
                    mode: upsert

                concurrency:
                  maxActiveRuns: 2
                  scope: PROJECT
                """.formatted(endpointUrl, healthUrl);
    }

}

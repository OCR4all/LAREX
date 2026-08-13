package de.uniwue.zpd.dachs.larex.backend.service;

import de.uniwue.zpd.dachs.larex.backend.dto.AuthorizationCapabilitiesDto;
import de.uniwue.zpd.dachs.larex.backend.entity.LabelSet;
import de.uniwue.zpd.dachs.larex.backend.repository.label.LabelSetRepository;
import de.uniwue.zpd.dachs.larex.backend.service.label.LabelSetDefinitionValidator;
import de.uniwue.zpd.dachs.larex.backend.service.label.LabelSetService;
import de.uniwue.zpd.dachs.larex.backend.service.security.AuthorizationPolicyService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LabelSetServiceTest {

    private static final String WORKSPACE_ID = "workspace-1";
    private static final String USER_ID = "user-1";

    @Mock
    private LabelSetRepository labelSetRepository;

    @Mock
    private WorkspaceAccessService workspaceAccessService;

    @Mock
    private AuthorizationPolicyService authorizationPolicyService;

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private final LabelSetDefinitionValidator validator = new LabelSetDefinitionValidator(
            Validation.buildDefaultValidatorFactory().getValidator()
    );

    @Test
    void createLabelSet_normalizesLegacyCustomRegionPayload() throws Exception {
        LabelSetService service = new LabelSetService(
                labelSetRepository,
                workspaceAccessService,
                objectMapper,
                validator,
                authorizationPolicyService
        );

        when(labelSetRepository.save(any(LabelSet.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(authorizationPolicyService.resolveWorkspaceResourceCapabilities(WORKSPACE_ID, USER_ID))
                .thenReturn(new AuthorizationCapabilitiesDto.ResourceCapabilities(true, true, true));

        JsonNode request = objectMapper.readTree("""
                {
                  "meta": {
                    "name": "PAGE XML Standard (Copy)",
                    "description": "",
                    "tags": []
                  },
                  "labels": [
                    {
                      "id": "custom-region",
                      "scope": "region",
                      "name": "Custom Region",
                      "description": "",
                      "color": "#00BFA5",
                      "hasText": false,
                      "isContainer": false,
                      "group": null,
                      "mapping": {
                        "pageXml": {
                          "regionType": null,
                          "textType": null,
                          "customSubType": "custom",
                          "customKey": "layout",
                          "customData": "subclass:lead"
                        }
                      }
                    }
                  ]
                }
                """);

        service.createLabelSet(USER_ID, WORKSPACE_ID, request);

        ArgumentCaptor<LabelSet> labelSetCaptor = ArgumentCaptor.forClass(LabelSet.class);
        verify(labelSetRepository).save(labelSetCaptor.capture());
        JsonNode pageXml = labelSetCaptor.getValue()
                .getDefinition()
                .get("labels")
                .get(0)
                .get("mapping")
                .get("pageXml");

        assertEquals("UnknownRegion", pageXml.get("regionType").asText());
        assertEquals("custom", pageXml.get("customSubType").asText());
        assertEquals("structure", pageXml.get("customKey").asText());
        assertEquals("", pageXml.get("customData").asText());
    }

    @Test
    void createLabelSet_rejectsTextlineLabels() throws Exception {
        LabelSetService service = new LabelSetService(
                labelSetRepository,
                workspaceAccessService,
                objectMapper,
                validator,
                authorizationPolicyService
        );
        JsonNode request = objectMapper.readTree("""
                {
                  "meta": { "name": "Invalid", "description": "", "tags": [] },
                  "labels": [{
                    "id": "line-label",
                    "scope": "line",
                    "name": "Text Line",
                    "description": "",
                    "color": "#123ABC",
                    "hasText": true,
                    "isContainer": false,
                    "group": null,
                    "mapping": { "pageXml": {
                      "regionType": null,
                      "textType": null,
                      "customSubType": null,
                      "customKey": "structure",
                      "customData": ""
                    }}
                  }]
                }
                """);

        assertThrows(IllegalArgumentException.class, () ->
                service.createLabelSet(USER_ID, WORKSPACE_ID, request));
    }

    @Test
    void createLabelSet_preservesLabelOrder() throws Exception {
        LabelSetService service = new LabelSetService(
                labelSetRepository,
                workspaceAccessService,
                objectMapper,
                validator,
                authorizationPolicyService
        );
        when(labelSetRepository.save(any(LabelSet.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(authorizationPolicyService.resolveWorkspaceResourceCapabilities(WORKSPACE_ID, USER_ID))
                .thenReturn(new AuthorizationCapabilitiesDto.ResourceCapabilities(true, true, true));

        JsonNode request = objectMapper.readTree("""
                {
                  "meta": { "name": "Ordered", "description": "", "tags": [], "defaultLabelId": "image" },
                  "labels": [
                    {
                      "id": "image",
                      "scope": "region",
                      "name": "Image",
                      "description": "",
                      "color": "#123ABC",
                      "hasText": false,
                      "isContainer": false,
                      "group": null,
                      "mapping": { "pageXml": {
                        "regionType": "ImageRegion", "textType": null, "customSubType": "",
                        "customKey": "structure", "customData": ""
                      }}
                    },
                    {
                      "id": "paragraph",
                      "scope": "region",
                      "name": "Paragraph",
                      "description": "",
                      "color": "#456DEF",
                      "hasText": true,
                      "isContainer": false,
                      "group": "Text",
                      "mapping": { "pageXml": {
                        "regionType": "TextRegion", "textType": "paragraph", "customSubType": "",
                        "customKey": "structure", "customData": ""
                      }}
                    }
                  ]
                }
                """);

        service.createLabelSet(USER_ID, WORKSPACE_ID, request);

        ArgumentCaptor<LabelSet> labelSetCaptor = ArgumentCaptor.forClass(LabelSet.class);
        verify(labelSetRepository).save(labelSetCaptor.capture());
        JsonNode savedLabels = labelSetCaptor.getValue().getDefinition().get("labels");
        assertEquals("image", savedLabels.get(0).get("id").asText());
        assertEquals("paragraph", savedLabels.get(1).get("id").asText());
        assertEquals("image", labelSetCaptor.getValue().getDefinition().get("meta").get("defaultLabelId").asText());
    }
}

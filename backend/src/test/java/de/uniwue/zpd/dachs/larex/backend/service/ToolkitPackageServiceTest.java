package de.uniwue.zpd.dachs.larex.backend.service;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import de.uniwue.zpd.dachs.larex.backend.dto.AuthorizationCapabilitiesDto;
import de.uniwue.zpd.dachs.larex.backend.dto.CodecDto;
import de.uniwue.zpd.dachs.larex.backend.dto.ToolkitPackageDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Codec;
import de.uniwue.zpd.dachs.larex.backend.entity.VirtualKeyboard;
import de.uniwue.zpd.dachs.larex.backend.repository.dictionary.ControlledDictionaryEntryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.dictionary.ControlledDictionaryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.codec.CodecRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.keyboard.VirtualKeyboardRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.label.LabelSetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.normalization.NormalizationProfileRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.tag.TagSetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.validation.ValidationRulesetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceQueryService;
import de.uniwue.zpd.dachs.larex.backend.service.codec.CodecService;
import de.uniwue.zpd.dachs.larex.backend.service.dictionary.DictionaryService;
import de.uniwue.zpd.dachs.larex.backend.service.keyboard.VirtualKeyboardService;
import de.uniwue.zpd.dachs.larex.backend.service.label.LabelSetService;
import de.uniwue.zpd.dachs.larex.backend.service.normalization.NormalizationProfileService;
import de.uniwue.zpd.dachs.larex.backend.service.tag.TagSetService;
import de.uniwue.zpd.dachs.larex.backend.service.toolkit.ToolkitPackageService;
import de.uniwue.zpd.dachs.larex.backend.service.validation.ValidationRulesetService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolkitPackageServiceTest {

    @Mock
    private WorkspaceAccessService workspaceAccessService;

    @Mock
    private WorkspaceQueryService workspaceQueryService;

    @Mock
    private CodecRepository codecRepository;
    @Mock
    private ControlledDictionaryEntryRepository dictionaryEntryRepository;
    @Mock
    private ControlledDictionaryRepository dictionaryRepository;

    @Mock
    private LabelSetRepository labelSetRepository;

    @Mock
    private TagSetRepository tagSetRepository;
    @Mock
    private NormalizationProfileRepository normalizationProfileRepository;
    @Mock
    private ValidationRulesetRepository validationRulesetRepository;

    @Mock
    private VirtualKeyboardRepository virtualKeyboardRepository;

    @Mock
    private CodecService codecService;
    @Mock
    private DictionaryService dictionaryService;

    @Mock
    private LabelSetService labelSetService;

    @Mock
    private TagSetService tagSetService;
    @Mock
    private NormalizationProfileService normalizationProfileService;
    @Mock
    private ValidationRulesetService validationRulesetService;

    @Mock
    private VirtualKeyboardService virtualKeyboardService;

    private ToolkitPackageService service;

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @BeforeEach
    void setUp() {
        service = new ToolkitPackageService(
                workspaceAccessService,
                workspaceQueryService,
                codecRepository,
                dictionaryEntryRepository,
                dictionaryRepository,
                labelSetRepository,
                tagSetRepository,
                normalizationProfileRepository,
                validationRulesetRepository,
                virtualKeyboardRepository,
                codecService,
                dictionaryService,
                labelSetService,
                tagSetService,
                normalizationProfileService,
                validationRulesetService,
                virtualKeyboardService,
                objectMapper
        );
    }

    @Test
    void importToolkitPackageFromContent_legacyCodec_createsCodec() throws Exception {
        String workspaceId = "ws-1";
        String userId = "u-1";

        String legacyCodec = """
                {
                  "name": "Sample Codec",
                  "description": "desc",
                  "tags": ["a", "b"],
                  "codec": ["x", "y"]
                }
                """;

        when(codecRepository.findByNameAndLibraryWorkspaceId("Sample Codec", workspaceId)).thenReturn(Optional.empty());
        when(codecService.createCodec(any(), any(), any())).thenReturn(new CodecDto.Response(
                "codec-1",
                "Sample Codec",
                "desc",
                List.of("a", "b"),
                List.of("x", "y"),
                2,
                null,
                null,
                new AuthorizationCapabilitiesDto.ResourceCapabilities(true, true, true)
        ));

        ToolkitPackageDto.ImportResult result = service.importToolkitPackageFromContent(workspaceId, userId, legacyCodec);

        assertEquals(1, result.importedCount());
        assertEquals(0, result.reusedCount());
        assertEquals(1, result.resources().size());
        assertEquals("IMPORTED", result.resources().getFirst().action());
        assertEquals("codec-1", result.resources().getFirst().targetId());
    }

    @Test
    void importToolkitPackage_existingCodecWithSamePayload_reusesCodec() throws Exception {
        String workspaceId = "ws-1";
        String userId = "u-1";

        String packageJson = """
                {
                  "meta": {"schemaVersion": "1.0", "exportedAt": "2026-02-21T10:00:00", "workspaceId": "ws-source", "workspaceName": "Source"},
                  "resources": [
                    {
                      "type": "CODEC",
                      "sourceId": "codec-source",
                      "name": "Shared Codec",
                      "payload": {
                        "name": "Shared Codec",
                        "description": "desc",
                        "tags": ["a", "b"],
                        "codec": ["x", "y"]
                      }
                    }
                  ]
                }
                """;

        Codec existing = new Codec();
        existing.setId("codec-existing");
        existing.setName("Shared Codec");
        existing.setDescription("desc");
        existing.setTags(Set.of("a", "b"));
        existing.setCharacters(Set.of("x", "y"));

        when(codecRepository.findByNameAndLibraryWorkspaceId("Shared Codec", workspaceId)).thenReturn(Optional.of(existing));

        ToolkitPackageDto.ImportResult result = service.importToolkitPackageFromContent(workspaceId, userId, packageJson);

        assertEquals(0, result.importedCount());
        assertEquals(1, result.reusedCount());
        assertEquals(1, result.resources().size());
        assertEquals("REUSED", result.resources().getFirst().action());
        assertEquals("codec-existing", result.resources().getFirst().targetId());
        assertTrue(result.sourceToTargetIds().containsKey("codec-source"));
        verify(codecService, never()).createCodec(any(), any(), any());
    }

    @Test
    void projectToolkitSnapshotIncludesSelectedVirtualKeyboard() {
        VirtualKeyboard keyboard = new VirtualKeyboard();
        keyboard.setId("keyboard-1");
        keyboard.setWorkspaceId("ws-1");
        keyboard.setName("Transcription");
        keyboard.setDescription("Common glyphs");
        keyboard.setCols(4);
        keyboard.setRows(2);
        keyboard.setTags(List.of("project"));
        keyboard.setItems(List.of());
        when(virtualKeyboardRepository.findByWorkspaceId("ws-1")).thenReturn(List.of(keyboard));

        ToolkitPackageDto.ToolkitPackage snapshot = service.buildProjectToolkitSnapshot(
                "ws-1",
                null,
                null,
                null,
                null,
                null,
                null,
                "keyboard-1"
        );

        assertEquals(1, snapshot.resources().size());
        ToolkitPackageDto.ToolkitResource resource = snapshot.resources().getFirst();
        assertEquals(ToolkitPackageDto.ToolkitType.VIRTUAL_KEYBOARD, resource.type());
        assertEquals("Transcription", resource.name());
        assertEquals(4, resource.payload().path("cols").asInt());
    }

    @Test
    void packageImportCanReplaceConflictingCodecInPlace() throws Exception {
        Codec existing = new Codec();
        existing.setId("codec-existing");
        existing.setName("Shared Codec");
        existing.setDescription("old");
        existing.setTags(Set.of());
        existing.setCharacters(Set.of("a"));
        when(codecRepository.findByNameAndLibraryWorkspaceId("Shared Codec", "ws-1"))
                .thenReturn(Optional.of(existing));
        when(codecService.updateCodec(any(), any(), any(), any())).thenReturn(new CodecDto.Response(
                "codec-existing",
                "Shared Codec",
                "new",
                List.of(),
                List.of("a", "b"),
                2,
                null,
                null,
                new AuthorizationCapabilitiesDto.ResourceCapabilities(true, true, true)
        ));

        ToolkitPackageDto.ToolkitResource resource = new ToolkitPackageDto.ToolkitResource(
                ToolkitPackageDto.ToolkitType.CODEC,
                "CODEC",
                "Shared Codec",
                null,
                null,
                objectMapper.readTree("""
                        {
                          "name": "Shared Codec",
                          "description": "new",
                          "tags": [],
                          "codec": ["a", "b"]
                        }
                        """)
        );
        ToolkitPackageDto.ImportResult result = service.importToolkitPackage(
                "ws-1",
                "user-1",
                new ToolkitPackageDto.ToolkitPackage(null, List.of(resource)),
                Map.of(ToolkitPackageDto.ToolkitType.CODEC, ToolkitPackageDto.ImportAction.REPLACE)
        );

        assertEquals("REPLACED", result.resources().getFirst().action());
        assertEquals("codec-existing", result.resources().getFirst().targetId());
        verify(codecService).updateCodec(eq("user-1"), eq("ws-1"), eq("codec-existing"), any());
    }

    @Test
    void packagePreviewRecognizesAnIdenticalCodecForReuse() throws Exception {
        Codec existing = new Codec();
        existing.setId("codec-existing");
        existing.setName("Shared Codec");
        existing.setDescription("same");
        existing.setTags(Set.of("project"));
        existing.setCharacters(Set.of("a", "b"));
        when(codecRepository.findByNameAndLibraryWorkspaceId("Shared Codec", "ws-1"))
                .thenReturn(Optional.of(existing));

        ToolkitPackageDto.ResourcePreview preview = service.previewToolkitResource(
                "ws-1",
                new ToolkitPackageDto.ToolkitResource(
                        ToolkitPackageDto.ToolkitType.CODEC,
                        "CODEC",
                        "Shared Codec",
                        null,
                        null,
                        objectMapper.readTree("""
                                {
                                  "name": "Shared Codec",
                                  "description": "same",
                                  "tags": ["project"],
                                  "codec": ["a", "b"]
                                }
                                """)
                )
        );

        assertEquals("codec-existing", preview.existingId());
        assertTrue(preview.identical());
        assertTrue(preview.replaceAllowed());
    }

    @Test
    void packageImportCanSkipAResourceWithoutCreatingIt() throws Exception {
        ToolkitPackageDto.ToolkitResource resource = new ToolkitPackageDto.ToolkitResource(
                ToolkitPackageDto.ToolkitType.CODEC,
                "CODEC",
                "Shared Codec",
                null,
                null,
                objectMapper.readTree("""
                        {
                          "name": "Shared Codec",
                          "description": "ignored",
                          "tags": [],
                          "codec": ["a"]
                        }
                        """)
        );

        ToolkitPackageDto.ImportResult result = service.importToolkitPackage(
                "ws-1",
                "user-1",
                new ToolkitPackageDto.ToolkitPackage(null, List.of(resource)),
                Map.of(ToolkitPackageDto.ToolkitType.CODEC, ToolkitPackageDto.ImportAction.SKIP)
        );

        assertEquals("SKIPPED", result.resources().getFirst().action());
        assertNull(result.resources().getFirst().targetId());
        verify(codecService, never()).createCodec(any(), any(), any());
        verify(codecService, never()).updateCodec(any(), any(), any(), any());
    }
}

package de.uniwue.zpd.dachs.larex.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.uniwue.zpd.dachs.larex.backend.dto.CodecDto;
import de.uniwue.zpd.dachs.larex.backend.dto.UtilityPackageDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Codec;
import de.uniwue.zpd.dachs.larex.backend.repository.BoardThemeRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.CodecRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.LabelSetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.TagSetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.VirtualKeyboardRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceQueryService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UtilityPackageServiceTest {

    @Mock
    private WorkspaceAccessService workspaceAccessService;

    @Mock
    private WorkspaceQueryService workspaceQueryService;

    @Mock
    private CodecRepository codecRepository;

    @Mock
    private LabelSetRepository labelSetRepository;

    @Mock
    private TagSetRepository tagSetRepository;

    @Mock
    private VirtualKeyboardRepository virtualKeyboardRepository;

    @Mock
    private BoardThemeRepository boardThemeRepository;

    @Mock
    private CodecService codecService;

    @Mock
    private LabelSetService labelSetService;

    @Mock
    private TagSetService tagSetService;

    @Mock
    private VirtualKeyboardService virtualKeyboardService;

    @Mock
    private BoardThemeService boardThemeService;

    private UtilityPackageService service;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        service = new UtilityPackageService(
                workspaceAccessService,
                workspaceQueryService,
                codecRepository,
                labelSetRepository,
                tagSetRepository,
                virtualKeyboardRepository,
                boardThemeRepository,
                codecService,
                labelSetService,
                tagSetService,
                virtualKeyboardService,
                boardThemeService,
                objectMapper
        );
    }

    @Test
    void importUtilityPackageFromContent_legacyCodec_createsCodec() throws Exception {
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
                null
        ));

        UtilityPackageDto.ImportResult result = service.importUtilityPackageFromContent(workspaceId, userId, legacyCodec);

        assertEquals(1, result.importedCount());
        assertEquals(0, result.reusedCount());
        assertEquals(1, result.resources().size());
        assertEquals("IMPORTED", result.resources().getFirst().action());
        assertEquals("codec-1", result.resources().getFirst().targetId());
    }

    @Test
    void importUtilityPackage_existingCodecWithSamePayload_reusesCodec() throws Exception {
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

        UtilityPackageDto.ImportResult result = service.importUtilityPackageFromContent(workspaceId, userId, packageJson);

        assertEquals(0, result.importedCount());
        assertEquals(1, result.reusedCount());
        assertEquals(1, result.resources().size());
        assertEquals("REUSED", result.resources().getFirst().action());
        assertEquals("codec-existing", result.resources().getFirst().targetId());
        assertTrue(result.sourceToTargetIds().containsKey("codec-source"));
        verify(codecService, never()).createCodec(any(), any(), any());
    }
}

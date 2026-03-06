package de.uniwue.zpd.dachs.larex.backend.service;

import de.uniwue.zpd.dachs.larex.backend.dto.CodecDto;
import de.uniwue.zpd.dachs.larex.backend.dto.AuthorizationCapabilitiesDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Codec;
import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageTextContent;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.repository.codec.CodecRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.library.LibraryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageTextContentRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.service.codec.CodecService;
import de.uniwue.zpd.dachs.larex.backend.service.security.AuthorizationPolicyService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CodecServiceTest {

    @Mock
    private CodecRepository codecRepository;

    @Mock
    private LibraryRepository libraryRepository;

    @Mock
    private WorkspaceAccessService workspaceAccessService;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private PageRepository pageRepository;

    @Mock
    private PageTextContentRepository pageTextContentRepository;

    @Mock
    private AuthorizationPolicyService authorizationPolicyService;

    private CodecService service;

    @BeforeEach
    void setUp() {
        service = new CodecService(
                codecRepository,
                libraryRepository,
                workspaceAccessService,
                projectRepository,
                pageRepository,
                pageTextContentRepository,
                authorizationPolicyService
        );
        org.mockito.Mockito.lenient().when(authorizationPolicyService.resolveWorkspaceResourceCapabilities(any(), any()))
                .thenReturn(new AuthorizationCapabilitiesDto.ResourceCapabilities(true, true));
    }

    @Test
    void generateFromSources_createNew_ignoresWhitespaceByDefault() throws Exception {
        String workspaceId = "ws-1";
        String userId = "u-1";
        String projectId = "p-1";
        String pageId = "pg-1";

        Library library = new Library("workspace-lib", "lib");
        setId(library, "lib-1");

        Project project = new Project();
        setId(project, projectId);

        Page page = new Page();
        setId(page, pageId);

        PageTextContent row1 = new PageTextContent(page, "tl-1", null, "a b\n", 0);
        PageTextContent row2 = new PageTextContent(page, "tl-2", null, "𝔘", 1);

        when(projectRepository.findByIdAndLibraryWorkspaceId(projectId, workspaceId)).thenReturn(Optional.of(project));
        when(pageRepository.findByProjectId(projectId)).thenReturn(List.of(page));
        when(pageTextContentRepository.findByProjectId(projectId)).thenReturn(List.of(row1, row2));
        when(libraryRepository.findByWorkspaceId(workspaceId)).thenReturn(Optional.of(library));
        when(codecRepository.existsByNameAndLibraryId("Generated", "lib-1")).thenReturn(false);
        when(codecRepository.save(any(Codec.class))).thenAnswer(invocation -> {
            Codec codec = invocation.getArgument(0);
            if (codec.getId() == null) {
                setId(codec, "codec-generated");
            }
            return codec;
        });

        CodecDto.GenerateFromSourcesRequest request = new CodecDto.GenerateFromSourcesRequest(
                List.of(new CodecDto.ProjectScope(projectId, List.of())),
                null,
                "Generated",
                "desc",
                List.of("test"),
                null,
                null,
                false,
                null
        );

        CodecDto.GenerateFromSourcesResponse response = service.generateFromSources(userId, workspaceId, request);

        assertTrue(response.createdNewCodec());
        assertEquals(1, response.analyzedProjectCount());
        assertEquals(1, response.analyzedPageCount());
        assertEquals(3, response.extractedCharacterCount());
        assertEquals(Set.of("a", "b", "𝔘"), Set.copyOf(response.codec().codec()));
        assertFalse(response.codec().codec().contains(" "));
    }

    @Test
    void generateFromSources_appendExistingCodec_countsOnlyNewCharacters() throws Exception {
        String workspaceId = "ws-1";
        String userId = "u-1";
        String projectId = "p-1";
        String pageId = "pg-1";

        Project project = new Project();
        setId(project, projectId);

        Page page = new Page();
        setId(page, pageId);

        PageTextContent row = new PageTextContent(page, "tl-1", null, "bcd", 0);

        Codec existing = new Codec();
        setId(existing, "codec-1");
        existing.setName("Existing");
        existing.setCharacters(new java.util.HashSet<>(Set.of("a", "b")));
        existing.setLibrary(new Library(workspaceId, "lib"));

        when(projectRepository.findByIdAndLibraryWorkspaceId(projectId, workspaceId)).thenReturn(Optional.of(project));
        when(pageRepository.findByProjectId(projectId)).thenReturn(List.of(page));
        when(pageTextContentRepository.findByProjectId(projectId)).thenReturn(List.of(row));
        when(codecRepository.findByIdAndLibraryWorkspaceId("codec-1", workspaceId)).thenReturn(Optional.of(existing));
        when(codecRepository.save(any(Codec.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CodecDto.GenerateFromSourcesRequest request = new CodecDto.GenerateFromSourcesRequest(
                List.of(new CodecDto.ProjectScope(projectId, List.of())),
                "codec-1",
                null,
                null,
                List.of(),
                CodecDto.VariantScope.ALL,
                null,
                false,
                false
        );

        CodecDto.GenerateFromSourcesResponse response = service.generateFromSources(userId, workspaceId, request);

        assertFalse(response.createdNewCodec());
        assertEquals(3, response.extractedCharacterCount());
        assertEquals(2, response.addedCharacterCount());
        assertEquals(Set.of("a", "b", "c", "d"), Set.copyOf(response.codec().codec()));
    }

    @Test
    void validateAgainstSources_primaryVariant_ignoresNonPrimaryVariants() throws Exception {
        String workspaceId = "ws-1";
        String userId = "u-1";
        String projectId = "p-1";
        String pageId = "pg-1";

        Project project = new Project();
        setId(project, projectId);

        Page page = new Page();
        setId(page, pageId);

        PageTextContent primary = new PageTextContent(page, "tl-1", null, "ab", 0);
        PageTextContent secondary = new PageTextContent(page, "tl-1", null, "xyz", 1);

        Codec codec = new Codec();
        setId(codec, "codec-1");
        codec.setCharacters(new java.util.HashSet<>(Set.of("a", "b")));
        codec.setLibrary(new Library(workspaceId, "lib"));

        when(codecRepository.findByIdAndLibraryWorkspaceId("codec-1", workspaceId)).thenReturn(Optional.of(codec));
        when(projectRepository.findByIdAndLibraryWorkspaceId(projectId, workspaceId)).thenReturn(Optional.of(project));
        when(pageRepository.findByProjectId(projectId)).thenReturn(List.of(page));
        when(pageTextContentRepository.findByProjectId(projectId)).thenReturn(List.of(primary, secondary));

        CodecDto.ValidateAgainstSourcesRequest request = new CodecDto.ValidateAgainstSourcesRequest(
                List.of(new CodecDto.ProjectScope(projectId, List.of())),
                CodecDto.VariantScope.PRIMARY,
                null,
                false,
                false
        );

        CodecDto.ValidateAgainstSourcesResponse response = service.validateAgainstSources(userId, workspaceId, "codec-1", request);

        assertTrue(response.valid());
        assertEquals(0, response.missingCharacterCount());
        assertTrue(response.missingCharacters().isEmpty());
    }

    @Test
    void validateAgainstSources_specificVariantIndex_filtersByRequestedIndex() throws Exception {
        String workspaceId = "ws-1";
        String userId = "u-1";
        String projectId = "p-1";
        String pageId = "pg-1";

        Project project = new Project();
        setId(project, projectId);

        Page page = new Page();
        setId(page, pageId);

        PageTextContent variantOne = new PageTextContent(page, "tl-1", null, "ab", 0);
        PageTextContent variantTwo = new PageTextContent(page, "tl-1", null, "xy", 2);

        Codec codec = new Codec();
        setId(codec, "codec-1");
        codec.setCharacters(new java.util.HashSet<>(Set.of("x", "y")));
        codec.setLibrary(new Library(workspaceId, "lib"));

        when(codecRepository.findByIdAndLibraryWorkspaceId("codec-1", workspaceId)).thenReturn(Optional.of(codec));
        when(projectRepository.findByIdAndLibraryWorkspaceId(projectId, workspaceId)).thenReturn(Optional.of(project));
        when(pageRepository.findByProjectId(projectId)).thenReturn(List.of(page));
        when(pageTextContentRepository.findByProjectId(projectId)).thenReturn(List.of(variantOne, variantTwo));

        CodecDto.ValidateAgainstSourcesRequest request = new CodecDto.ValidateAgainstSourcesRequest(
                List.of(new CodecDto.ProjectScope(projectId, List.of())),
                CodecDto.VariantScope.ALL,
                2,
                false,
                false
        );

        CodecDto.ValidateAgainstSourcesResponse response = service.validateAgainstSources(userId, workspaceId, "codec-1", request);

        assertTrue(response.valid());
        assertEquals(0, response.missingCharacterCount());
    }

    @Test
    void validateAgainstSources_unindexedOnly_filtersOutIndexedRows() throws Exception {
        String workspaceId = "ws-1";
        String userId = "u-1";
        String projectId = "p-1";
        String pageId = "pg-1";

        Project project = new Project();
        setId(project, projectId);

        Page page = new Page();
        setId(page, pageId);

        PageTextContent unindexed = new PageTextContent(page, "tl-1", null, "ab", 0);
        PageTextContent indexed = new PageTextContent(page, "tl-1", null, "xy", 1);

        Codec codec = new Codec();
        setId(codec, "codec-1");
        codec.setCharacters(new java.util.HashSet<>(Set.of("a", "b")));
        codec.setLibrary(new Library(workspaceId, "lib"));

        when(codecRepository.findByIdAndLibraryWorkspaceId("codec-1", workspaceId)).thenReturn(Optional.of(codec));
        when(projectRepository.findByIdAndLibraryWorkspaceId(projectId, workspaceId)).thenReturn(Optional.of(project));
        when(pageRepository.findByProjectId(projectId)).thenReturn(List.of(page));
        when(pageTextContentRepository.findByProjectId(projectId)).thenReturn(List.of(unindexed, indexed));

        CodecDto.ValidateAgainstSourcesRequest request = new CodecDto.ValidateAgainstSourcesRequest(
                List.of(new CodecDto.ProjectScope(projectId, List.of())),
                CodecDto.VariantScope.ALL,
                null,
                true,
                false
        );

        CodecDto.ValidateAgainstSourcesResponse response = service.validateAgainstSources(userId, workspaceId, "codec-1", request);

        assertTrue(response.valid());
        assertEquals(0, response.missingCharacterCount());
    }

    @Test
    void validateAgainstSources_includeWhitespace_reportsMissingWhitespace() throws Exception {
        String workspaceId = "ws-1";
        String userId = "u-1";
        String projectId = "p-1";
        String pageId = "pg-1";

        Project project = new Project();
        setId(project, projectId);

        Page page = new Page();
        setId(page, pageId);

        PageTextContent text = new PageTextContent(page, "tl-1", null, "a b", 0);

        Codec codec = new Codec();
        setId(codec, "codec-1");
        codec.setCharacters(new java.util.HashSet<>(Set.of("a", "b")));
        codec.setLibrary(new Library(workspaceId, "lib"));

        when(codecRepository.findByIdAndLibraryWorkspaceId("codec-1", workspaceId)).thenReturn(Optional.of(codec));
        when(projectRepository.findByIdAndLibraryWorkspaceId(projectId, workspaceId)).thenReturn(Optional.of(project));
        when(pageRepository.findByProjectId(projectId)).thenReturn(List.of(page));
        when(pageTextContentRepository.findByProjectId(projectId)).thenReturn(List.of(text));

        CodecDto.ValidateAgainstSourcesRequest request = new CodecDto.ValidateAgainstSourcesRequest(
                List.of(new CodecDto.ProjectScope(projectId, List.of())),
                CodecDto.VariantScope.ALL,
                null,
                false,
                true
        );

        CodecDto.ValidateAgainstSourcesResponse response = service.validateAgainstSources(userId, workspaceId, "codec-1", request);

        assertFalse(response.valid());
        assertEquals(1, response.missingCharacterCount());
        assertEquals(List.of(" "), response.missingCharacters());
    }

    @Test
    void validateAgainstSources_includesMissingCharacterPageReferences() throws Exception {
        String workspaceId = "ws-1";
        String userId = "u-1";
        String projectId = "p-1";
        String pageOneId = "pg-1";
        String pageTwoId = "pg-2";

        Project project = new Project();
        setId(project, projectId);
        project.setName("Project One");

        Page pageOne = new Page();
        setId(pageOne, pageOneId);
        pageOne.setName("Page One");

        Page pageTwo = new Page();
        setId(pageTwo, pageTwoId);
        pageTwo.setName("Page Two");

        PageTextContent rowOne = new PageTextContent(pageOne, "tl-1", null, "a§", 0);
        PageTextContent rowTwo = new PageTextContent(pageTwo, "tl-2", null, "§", 0);

        Codec codec = new Codec();
        setId(codec, "codec-1");
        codec.setCharacters(new java.util.HashSet<>(Set.of("a")));
        codec.setLibrary(new Library(workspaceId, "lib"));

        when(codecRepository.findByIdAndLibraryWorkspaceId("codec-1", workspaceId)).thenReturn(Optional.of(codec));
        when(projectRepository.findByIdAndLibraryWorkspaceId(projectId, workspaceId)).thenReturn(Optional.of(project));
        when(pageRepository.findByProjectId(projectId)).thenReturn(List.of(pageOne, pageTwo));
        when(pageTextContentRepository.findByProjectId(projectId)).thenReturn(List.of(rowOne, rowTwo));

        CodecDto.ValidateAgainstSourcesRequest request = new CodecDto.ValidateAgainstSourcesRequest(
                List.of(new CodecDto.ProjectScope(projectId, List.of())),
                CodecDto.VariantScope.ALL,
                null,
                false,
                false
        );

        CodecDto.ValidateAgainstSourcesResponse response = service.validateAgainstSources(userId, workspaceId, "codec-1", request);

        assertFalse(response.valid());
        assertEquals(List.of("§"), response.missingCharacters());
        assertEquals(1, response.missingCharacterResults().size());

        CodecDto.ValidateCharacterResult missing = response.missingCharacterResults().get(0);
        assertEquals("§", missing.character());
        assertEquals(2, missing.pages().size());
        assertEquals(Set.of(pageOneId, pageTwoId), missing.pages().stream().map(CodecDto.ValidateCharacterPageRef::pageId).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void validateAgainstSources_reportsMissingPageIdsPerProject() throws Exception {
        String workspaceId = "ws-1";
        String userId = "u-1";
        String projectId = "p-1";
        String pageOneId = "pg-1";
        String pageTwoId = "pg-2";

        Project project = new Project();
        setId(project, projectId);
        project.setName("Project One");

        Page pageOne = new Page();
        setId(pageOne, pageOneId);
        pageOne.setName("Page One");

        Page pageTwo = new Page();
        setId(pageTwo, pageTwoId);
        pageTwo.setName("Page Two");

        PageTextContent rowOne = new PageTextContent(pageOne, "tl-1", null, "ab", 0);
        PageTextContent rowTwo = new PageTextContent(pageTwo, "tl-2", null, "c", 0);

        Codec codec = new Codec();
        setId(codec, "codec-1");
        codec.setCharacters(new java.util.HashSet<>(Set.of("a", "b")));
        codec.setLibrary(new Library(workspaceId, "lib"));

        when(codecRepository.findByIdAndLibraryWorkspaceId("codec-1", workspaceId)).thenReturn(Optional.of(codec));
        when(projectRepository.findByIdAndLibraryWorkspaceId(projectId, workspaceId)).thenReturn(Optional.of(project));
        when(pageRepository.findByProjectId(projectId)).thenReturn(List.of(pageOne, pageTwo));
        when(pageTextContentRepository.findByProjectId(projectId)).thenReturn(List.of(rowOne, rowTwo));

        CodecDto.ValidateAgainstSourcesRequest request = new CodecDto.ValidateAgainstSourcesRequest(
                List.of(new CodecDto.ProjectScope(projectId, List.of())),
                CodecDto.VariantScope.ALL,
                null,
                false,
                false
        );

        CodecDto.ValidateAgainstSourcesResponse response = service.validateAgainstSources(userId, workspaceId, "codec-1", request);

        assertFalse(response.valid());
        assertEquals(1, response.projectResults().size());

        CodecDto.ValidateProjectResult projectResult = response.projectResults().get(0);
        assertEquals(projectId, projectResult.projectId());
        assertEquals("Project One", projectResult.projectName());
        assertEquals(List.of("c"), projectResult.missingCharacters());
        assertEquals(1, projectResult.missingPageCount());
        assertEquals(List.of(pageTwoId), projectResult.missingPageIds());
    }

    @Test
    void generateFromSources_invalidPageScope_throwsIllegalArgumentException() throws Exception {
        String workspaceId = "ws-1";
        String userId = "u-1";
        String projectId = "p-1";

        Project project = new Project();
        setId(project, projectId);

        when(projectRepository.findByIdAndLibraryWorkspaceId(projectId, workspaceId)).thenReturn(Optional.of(project));
        when(pageRepository.findByIdInAndProjectId(List.of("missing"), projectId)).thenReturn(List.of());

        CodecDto.GenerateFromSourcesRequest request = new CodecDto.GenerateFromSourcesRequest(
                List.of(new CodecDto.ProjectScope(projectId, List.of("missing"))),
                null,
                "Generated",
                null,
                List.of(),
                CodecDto.VariantScope.ALL,
                null,
                false,
                false
        );

        assertThrows(IllegalArgumentException.class, () -> service.generateFromSources(userId, workspaceId, request));
    }

    private static void setId(Object target, String id) throws Exception {
        Field field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }
}

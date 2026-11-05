package de.uniwue.zpd.dachs.larex.backend.service.normalization;

import de.uniwue.zpd.dachs.larex.backend.dto.NormalizationProfileDto;
import de.uniwue.zpd.dachs.larex.backend.entity.NormalizationProfile;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageTextContent;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.repository.normalization.NormalizationProfileRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageTextContentRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NormalizationProfileServicePagedPreviewTest {

    private static final String WORKSPACE_ID = "workspace";
    private static final String PROFILE_ID = "profile";
    private static final String PROJECT_ID = "project";
    private static final String USER_ID = "user";

    @Mock
    private NormalizationProfileRepository normalizationProfileRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private PageRepository pageRepository;
    @Mock
    private PageTextContentRepository pageTextContentRepository;
    @Mock
    private WorkspaceAccessService workspaceAccessService;

    @InjectMocks
    private NormalizationProfileService service;

    private Project project;

    @BeforeEach
    void setUp() {
        NormalizationProfile profile = new NormalizationProfile();
        profile.setCollapseWhitespace(true);
        when(normalizationProfileRepository.findByIdAndWorkspaceId(PROFILE_ID, WORKSPACE_ID)).thenReturn(Optional.of(profile));

        project = new Project();
        project.setId(PROJECT_ID);
        project.setName("Project");
        when(projectRepository.findByIdAndLibraryWorkspaceId(PROJECT_ID, WORKSPACE_ID)).thenReturn(Optional.of(project));
        when(pageRepository.findIdsByProjectIdOrderByName(PROJECT_ID)).thenReturn(List.of("p1", "p2", "p3"));
        when(pageTextContentRepository.findByProjectIdAndPageIds(eq(PROJECT_ID), anyList())).thenAnswer(invocation -> {
            List<String> pageIds = invocation.getArgument(1);
            List<PageTextContent> rows = new ArrayList<>();
            for (String pageId : pageIds.reversed()) {
                rows.add(row(pageId));
            }
            return rows;
        });
    }

    @Test
    void returnsTheRequestedPagesInScanOrderWithTheNextOffset() {
        NormalizationProfileDto.NormalizeSourcesResponse response = preview(0, 2);

        assertEquals(List.of("p1", "p2"), previewPageIds(response));
        assertEquals(3, response.totalPageCount());
        assertEquals(2, response.nextPageOffset());
        assertEquals(2, response.analyzedPageCount());
    }

    @Test
    void endsTheScanOnTheLastPage() {
        NormalizationProfileDto.NormalizeSourcesResponse response = preview(2, 2);

        assertEquals(List.of("p3"), previewPageIds(response));
        assertNull(response.nextPageOffset());
    }

    @Test
    void continuesAcrossProjectsWithoutRepeatingPages() {
        Project second = new Project();
        second.setId("second");
        second.setName("Second");
        when(projectRepository.findByIdAndLibraryWorkspaceId("second", WORKSPACE_ID)).thenReturn(Optional.of(second));
        when(pageRepository.findIdsByProjectIdOrderByName("second")).thenReturn(List.of("s1", "s2"));
        when(pageTextContentRepository.findByProjectIdAndPageIds(eq("second"), anyList())).thenAnswer(invocation -> {
            List<String> pageIds = invocation.getArgument(1);
            return pageIds.stream().map(pageId -> row(pageId, second)).toList();
        });

        NormalizationProfileDto.NormalizeSourcesResponse response = service.normalizeSources(USER_ID, WORKSPACE_ID, PROFILE_ID, request(
                List.of(source(PROJECT_ID), source("second")), 2, 2
        ));

        assertEquals(List.of("p3", "s1"), previewPageIds(response));
        assertEquals(5, response.totalPageCount());
        assertEquals(4, response.nextPageOffset());
    }

    @Test
    void scansEverythingWithoutAPageLimit() {
        when(pageTextContentRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of(row("p1"), row("p2"), row("p3")));

        NormalizationProfileDto.NormalizeSourcesResponse response = preview(null, null);

        assertEquals(List.of("p1", "p2", "p3"), previewPageIds(response));
        assertNull(response.nextPageOffset());
        verify(pageTextContentRepository, never()).findByProjectIdAndPageIds(eq(PROJECT_ID), anyList());
    }

    private NormalizationProfileDto.NormalizeSourcesResponse preview(Integer pageOffset, Integer pageLimit) {
        return service.normalizeSources(USER_ID, WORKSPACE_ID, PROFILE_ID, request(List.of(source(PROJECT_ID)), pageOffset, pageLimit));
    }

    private NormalizationProfileDto.NormalizeSourcesRequest request(List<NormalizationProfileDto.ProjectScope> sources,
                                                                    Integer pageOffset,
                                                                    Integer pageLimit) {
        return new NormalizationProfileDto.NormalizeSourcesRequest(sources, null, null, null, null, pageOffset, pageLimit);
    }

    private NormalizationProfileDto.ProjectScope source(String projectId) {
        return new NormalizationProfileDto.ProjectScope(projectId, List.of());
    }

    private List<String> previewPageIds(NormalizationProfileDto.NormalizeSourcesResponse response) {
        return response.previews().stream().map(NormalizationProfileDto.NormalizePreview::pageId).toList();
    }

    private PageTextContent row(String pageId) {
        return row(pageId, project);
    }

    private PageTextContent row(String pageId, Project owner) {
        Page page = new Page();
        page.setId(pageId);
        page.setName(pageId);
        page.setProject(owner);

        PageTextContent row = new PageTextContent();
        row.setPage(page);
        row.setTextLineId("line-" + pageId);
        row.setTextContent("double  space");
        return row;
    }
}

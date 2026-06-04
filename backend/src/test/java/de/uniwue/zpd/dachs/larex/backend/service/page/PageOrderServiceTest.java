package de.uniwue.zpd.dachs.larex.backend.service.page;

import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PageOrderServiceTest {

    @Mock
    private PageRepository pageRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private WorkspaceAccessService workspaceAccessService;

    private PageOrderService service;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new PageOrderService(pageRepository, projectRepository, workspaceAccessService);
        project = new Project("Project", null, new Library("workspace-1", "Library"));
        project.setId("project-1");
    }

    @Test
    void reorderProjectPagesPersistsCompleteOrder() {
        Page first = page("page-1", "page 1", null);
        Page second = page("page-2", "page 2", null);
        when(projectRepository.findById("project-1")).thenReturn(Optional.of(project));
        when(workspaceAccessService.canManageProjects("workspace-1", "user-1")).thenReturn(true);
        when(pageRepository.findByProjectIdForUpdate("project-1")).thenReturn(List.of(first, second));
        when(pageRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<List<Page>> result = service.reorderProjectPages("project-1", List.of("page-2", "page-1"), "user-1");

        assertThat(result).isPresent();
        assertThat(second.getSortOrder()).isEqualTo(0);
        assertThat(first.getSortOrder()).isEqualTo(PageOrderService.SORT_ORDER_STEP);
        assertThat(result.get()).extracting(Page::getId).containsExactly("page-2", "page-1");
    }

    @Test
    void reorderProjectPagesRejectsDuplicateOrMissingIds() {
        Page first = page("page-1", "page 1", null);
        Page second = page("page-2", "page 2", null);
        when(projectRepository.findById("project-1")).thenReturn(Optional.of(project));
        when(workspaceAccessService.canManageProjects("workspace-1", "user-1")).thenReturn(true);
        when(pageRepository.findByProjectIdForUpdate("project-1")).thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service.reorderProjectPages("project-1", List.of("page-1", "page-1"), "user-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");
        assertThatThrownBy(() -> service.reorderProjectPages("project-1", List.of("page-1"), "user-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("every project page");
    }

    @Test
    void reorderProjectPagesRejectsForeignIds() {
        Page first = page("page-1", "page 1", null);
        Page second = page("page-2", "page 2", null);
        when(projectRepository.findById("project-1")).thenReturn(Optional.of(project));
        when(workspaceAccessService.canManageProjects("workspace-1", "user-1")).thenReturn(true);
        when(pageRepository.findByProjectIdForUpdate("project-1")).thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service.reorderProjectPages("project-1", List.of("page-1", "foreign"), "user-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside this project");
    }

    @Test
    void reorderProjectPagesRejectsUnauthorizedUser() {
        when(projectRepository.findById("project-1")).thenReturn(Optional.of(project));
        when(workspaceAccessService.canManageProjects("workspace-1", "user-1")).thenReturn(false);

        Optional<List<Page>> result = service.reorderProjectPages("project-1", List.of("page-1"), "user-1");

        assertThat(result).isEmpty();
    }

    @Test
    void reorderProjectPagesRejectsLockedProjectOrPages() {
        Page first = page("page-1", "page 1", null);
        when(projectRepository.findById("project-1")).thenReturn(Optional.of(project));
        when(workspaceAccessService.canManageProjects("workspace-1", "user-1")).thenReturn(true);

        project.setLocked(true);
        assertThatThrownBy(() -> service.reorderProjectPages("project-1", List.of("page-1"), "user-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Project is locked");

        project.setLocked(false);
        first.setLocked(true);
        when(pageRepository.findByProjectIdForUpdate("project-1")).thenReturn(List.of(first));
        assertThatThrownBy(() -> service.reorderProjectPages("project-1", List.of("page-1"), "user-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pages are locked");
    }

    @Test
    void sortPagesUsesStoredOrderThenNaturalNameFallback() {
        Page page10 = page("page-10", "page 10", null);
        Page page2 = page("page-2", "page 2", null);
        Page ordered = page("page-1", "page 100", 1000);

        assertThat(service.sortPages(List.of(page10, ordered, page2)))
                .extracting(Page::getId)
                .containsExactly("page-2", "page-10", "page-1");
    }

    @Test
    void reserveAppendSortOrdersStartsAfterExistingMaximum() {
        when(pageRepository.findByProjectIdForUpdate("project-1")).thenReturn(List.of(
                page("page-1", "page 1", null),
                page("page-2", "page 2", 3000)
        ));

        assertThat(service.reserveAppendSortOrders("project-1", 2)).containsExactly(4000, 5000);
    }

    private Page page(String id, String name, Integer sortOrder) {
        Page page = new Page();
        page.setId(id);
        page.setName(name);
        page.setProject(project);
        page.setSortOrder(sortOrder);
        return page;
    }
}

package de.uniwue.zpd.dachs.larex.backend.service.page;

import de.uniwue.zpd.dachs.larex.backend.dto.PageMoveDto.*;
import de.uniwue.zpd.dachs.larex.backend.entity.*;
import de.uniwue.zpd.dachs.larex.backend.exception.*;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.*;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.collaboration.AnnotationLeaseService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaRefreshService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PageMoveServiceTest {
    @Mock ProjectRepository projects;
    @Mock PageRepository pages;
    @Mock WorkspaceAccessService access;
    @Mock AnnotationLeaseService leases;
    @Mock PageMoveFileService files;
    @Mock TaskPageLinkRepository taskLinks;
    @Mock SubtaskRepository subtasks;
    @Mock WorkspaceQuotaRefreshService quota;
    @Mock de.uniwue.zpd.dachs.larex.backend.repository.dataset.DatasetItemRepository datasetItems;
    PageMoveService service;
    Project source;
    Project destination;
    Page first;
    Page second;
    Page clash;
    List<Page> destinationPages;

    @BeforeEach
    void setUp() {
        source = project("source");
        destination = project("destination");
        first = page("first", "001", source);
        first.setSortOrder(1000);
        second = page("second", "002", source);
        second.setSortOrder(2000);
        clash = page("clash", "001", destination);
        destinationPages = new ArrayList<>(List.of(clash));
        lenient().when(access.canManageProjects("workspace", "user")).thenReturn(true);
        for (Project project : List.of(source, destination)) {
            lenient().when(projects.findByIdAndLibraryWorkspaceId(project.getId(), "workspace"))
                    .thenReturn(Optional.of(project));
            lenient().when(projects.findByIdAndLibraryWorkspaceIdForUpdate(project.getId(), "workspace"))
                    .thenReturn(Optional.of(project));
        }
        lenient().when(pages.findByProjectId("source")).thenReturn(List.of(second, first));
        lenient().when(pages.findByProjectIdForUpdate("source")).thenReturn(List.of(second, first));
        lenient().when(pages.findByProjectId("destination")).thenReturn(destinationPages);
        lenient().when(pages.findByProjectIdForUpdate("destination")).thenReturn(destinationPages);
        service = new PageMoveService(projects, pages, access, new PageOrderService(pages, projects, access),
                leases, files, taskLinks, subtasks, quota, datasetItems);
    }

    @Test
    void previewSkipIncludesEveryPageInSourceOrder() {
        Preview result = preview(ConflictPolicy.SKIP, "", "");
        assertThat(result.items()).extracting(Item::pageId).containsExactly("first", "second");
        assertThat(result.items()).extracting(Item::outcome).containsExactly(Outcome.SKIP, Outcome.MOVE);
        assertThat(result.movedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.canMove()).isTrue();
        verifyNoInteractions(files);
    }

    @Test
    void renameOnlyClashesWithBothAffixes() {
        Preview result = preview(ConflictPolicy.RENAME, "pre-", "-post");
        assertThat(result.items()).extracting(Item::resultingName).containsExactly("pre-001-post", "002");
        assertThat(result.renamedCount()).isEqualTo(1);
        assertThat(result.canMove()).isTrue();
    }

    @Test
    void renameRequiresAffixAndRejectsOccupiedAndDuplicateResultingNames() {
        assertThat(preview(ConflictPolicy.RENAME, "", "").canMove()).isFalse();
        destinationPages.add(page("other", "pre-001", destination));
        assertThat(preview(ConflictPolicy.RENAME, "pre-", "").canMove()).isFalse();
        destinationPages.removeLast();
        second.setName("pre-001");
        Preview duplicate = preview(ConflictPolicy.RENAME, "pre-", "");
        assertThat(duplicate.canMove()).isFalse();
        assertThat(duplicate.items()).allMatch(item -> !item.blockers().isEmpty());
    }

    @Test
    void rejectsOverlongAndControlCharacterNames() {
        assertThat(preview(ConflictPolicy.RENAME, "p".repeat(255), "").canMove()).isFalse();
        assertThat(preview(ConflictPolicy.RENAME, "\n", "").canMove()).isFalse();
    }

    @Test
    void sourceLocksBlockEvenSkippedPagesAndDestinationLocksBlockOnlyOverwrite() {
        first.setLocked(true);
        assertThat(preview(ConflictPolicy.SKIP, "", "").canMove()).isFalse();
        first.setLocked(false);
        clash.setLocked(true);
        assertThat(preview(ConflictPolicy.SKIP, "", "").canMove()).isTrue();
        assertThat(preview(ConflictPolicy.OVERWRITE, "", "").canMove()).isFalse();
        clash.setLocked(false);
        destination.setLocked(true);
        assertThat(preview(ConflictPolicy.RENAME, "pre", "").canMove()).isFalse();
        destination.setLocked(false);
        source.setLocked(true);
        assertThat(preview(ConflictPolicy.RENAME, "pre", "").canMove()).isFalse();
    }

    @Test
    void activeEditorsBlockSourceAndOverwriteTargetsIncludingCurrentUser() {
        lenient().doThrow(new AnnotationLeaseLockedException("Editor active", null, "active"))
                .when(leases).assertNoOtherActiveEditor("clash", null);
        assertThat(preview(ConflictPolicy.OVERWRITE, "", "").canMove()).isFalse();
        assertThat(preview(ConflictPolicy.SKIP, "", "").canMove()).isTrue();
        lenient().doThrow(new AnnotationLeaseLockedException("Editor active", null, "active"))
                .when(leases).assertNoOtherActiveEditor("second", null);
        assertThat(preview(ConflictPolicy.SKIP, "", "").canMove()).isFalse();
    }

    @Test
    void checksPermissionAndBothProjectsWorkspaceAndSourceOwnership() {
        when(access.canManageProjects("workspace", "user")).thenReturn(false);
        assertThatThrownBy(() -> preview(ConflictPolicy.SKIP, "", "")).isInstanceOf(SecurityException.class);
        when(access.canManageProjects("workspace", "user")).thenReturn(true);
        when(projects.findByIdAndLibraryWorkspaceId("destination", "workspace")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> preview(ConflictPolicy.SKIP, "", "")).isInstanceOf(ResourceNotFoundException.class);
        when(projects.findByIdAndLibraryWorkspaceId("destination", "workspace")).thenReturn(Optional.of(destination));
        assertThatThrownBy(() -> service.preview("workspace", "source",
                new Request(List.of("clash"), "destination", ConflictPolicy.SKIP, "", "", null), "user"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.preview("workspace", "source",
                new Request(List.of("first"), "source", ConflictPolicy.SKIP, "", "", null), "user"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDuplicateIdsAndEmptySelection() {
        for (List<String> ids : List.of(List.<String>of(), List.of("first", "first"))) {
            assertThatThrownBy(() -> service.preview("workspace", "source",
                    new Request(ids, "destination", ConflictPolicy.SKIP, "", "", null), "user"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void allSkippedCannotExecute() {
        destinationPages.add(page("clash2", "002", destination));
        Preview preview = preview(ConflictPolicy.SKIP, "", "");
        assertThat(preview.canMove()).isFalse();
        assertThatThrownBy(() -> move(ConflictPolicy.SKIP, preview.fingerprint()))
                .isInstanceOf(PageMoveConflictException.class);
        verifyNoInteractions(files);
    }

    @Test
    void stalePreviewDetectsChangedNameLockAndOverwriteIdentity() {
        Preview preview = preview(ConflictPolicy.OVERWRITE, "", "");
        clash.setId("replacement");
        assertThatThrownBy(() -> move(ConflictPolicy.OVERWRITE, preview.fingerprint()))
                .isInstanceOf(PageMoveConflictException.class).hasMessageContaining("changed");
        clash.setId("clash");
        first.setName("changed");
        assertThatThrownBy(() -> move(ConflictPolicy.OVERWRITE, preview.fingerprint()))
                .isInstanceOf(PageMoveConflictException.class);
        first.setName("001");
        first.setLocked(true);
        assertThatThrownBy(() -> move(ConflictPolicy.OVERWRITE, preview.fingerprint()))
                .isInstanceOf(PageMoveConflictException.class);
        verifyNoInteractions(files);
    }

    @Test
    void previewIgnoresUnrelatedEditsButDetectsSelectedOrderAndDestinationNames() {
        Page unrelated = page("unrelated", "unrelated", source);
        when(pages.findByProjectId("source")).thenReturn(List.of(first, second, unrelated));
        String fingerprint = preview(ConflictPolicy.OVERWRITE, "", "").fingerprint();
        unrelated.setName("unrelated-renamed");
        unrelated.setLocked(true);
        first.setDescription("new metadata");
        first.setSortOrder(1500); // Same relative source order.
        destinationPages.add(page("other", "other", destination));
        assertThat(preview(ConflictPolicy.OVERWRITE, "", "").fingerprint()).isEqualTo(fingerprint);
        first.setSortOrder(3000);
        assertThat(preview(ConflictPolicy.OVERWRITE, "", "").fingerprint()).isNotEqualTo(fingerprint);
        first.setSortOrder(1000);
        destinationPages.add(page("new-clash", "002", destination));
        assertThat(preview(ConflictPolicy.OVERWRITE, "", "").fingerprint()).isNotEqualTo(fingerprint);
    }

    @Test
    void editorArrivingAfterPlanningPreventsAnyAssetCopies() {
        String fingerprint = preview(ConflictPolicy.OVERWRITE, "", "").fingerprint();
        doThrow(new AnnotationLeaseLockedException("Editor active", null, "active"))
                .when(leases).reservePagesForMove(anyCollection());
        assertThatThrownBy(() -> move(ConflictPolicy.OVERWRITE, fingerprint))
                .isInstanceOf(AnnotationLeaseLockedException.class);
        verifyNoInteractions(files);
    }

    @Test
    void overwriteCopiesBeforeDeletingAndPreservesSourceIdentity() throws Exception {
        first.setDescription("description");
        first.setTags(List.of("tag"));
        clash.setSortOrder(9000);
        Preview preview = preview(ConflictPolicy.OVERWRITE, "", "");
        Preview result = move(ConflictPolicy.OVERWRITE, preview.fingerprint());
        assertThat(result.overwrittenCount()).isEqualTo(1);
        assertThat(first.getId()).isEqualTo("first");
        assertThat(first.getDescription()).isEqualTo("description");
        assertThat(first.getTags()).containsExactly("tag");
        assertThat(first.getProject()).isSameAs(destination);
        assertThat(second.getProject()).isSameAs(destination);
        assertThat(first.getSortOrder()).isLessThan(second.getSortOrder());
        var sequence = inOrder(files, taskLinks, subtasks, pages);
        sequence.verify(files).copyAssets(eq(List.of("first", "second")), eq("workspace"), eq("destination"), eq("user"), any());
        sequence.verify(files).collectOverwrittenAssets(eq(List.of("clash")), any());
        sequence.verify(taskLinks).deleteByPageIdIn(List.of("clash"));
        sequence.verify(subtasks).deleteByPageIdIn(List.of("clash"));
        sequence.verify(pages).deleteTagsByPageIds(List.of("clash"));
        sequence.verify(pages).deleteByIdIn(List.of("clash"));
        verify(taskLinks, never()).deleteByPageIdIn(List.of("first", "second"));
    }

    @Test
    void skipKeepsSourcePageAndUsesStableProjectLockOrder() throws Exception {
        move(ConflictPolicy.SKIP, preview(ConflictPolicy.SKIP, "", "").fingerprint());
        assertThat(first.getProject()).isSameAs(source);
        assertThat(second.getProject()).isSameAs(destination);
        var locks = inOrder(projects);
        locks.verify(projects).findByIdAndLibraryWorkspaceIdForUpdate("destination", "workspace");
        locks.verify(projects).findByIdAndLibraryWorkspaceIdForUpdate("source", "workspace");
        verifyNoInteractions(taskLinks, subtasks);
    }

    @Test
    void copyFailureDoesNotDeleteOverwriteTargets() throws Exception {
        Preview preview = preview(ConflictPolicy.OVERWRITE, "", "");
        doThrow(new IOException("copy failed")).when(files).copyAssets(anyList(), anyString(), anyString(), anyString(), any());
        assertThatThrownBy(() -> move(ConflictPolicy.OVERWRITE, preview.fingerprint())).isInstanceOf(IOException.class);
        verify(pages, never()).deleteByIdIn(anyList());
        assertThat(first.getProject()).isSameAs(source);
    }

    private Preview preview(ConflictPolicy policy, String prefix, String suffix) {
        return service.preview("workspace", "source", request(policy, prefix, suffix, null), "user");
    }

    private Preview move(ConflictPolicy policy, String fingerprint) throws IOException {
        return service.move("workspace", "source", request(policy, "", "", fingerprint), "user");
    }

    private Request request(ConflictPolicy policy, String prefix, String suffix, String fingerprint) {
        return new Request(List.of("second", "first"), "destination", policy, prefix, suffix, fingerprint);
    }

    private Project project(String id) {
        Project project = new Project(id, null, new Library("workspace", "Library"));
        project.setId(id);
        return project;
    }

    private Page page(String id, String name, Project project) {
        Page page = new Page(name, null, project);
        page.setId(id);
        return page;
    }
}

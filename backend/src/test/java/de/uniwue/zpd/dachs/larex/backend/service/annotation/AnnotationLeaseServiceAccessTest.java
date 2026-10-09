package de.uniwue.zpd.dachs.larex.backend.service.annotation;

import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.exception.AnnotationLeaseLockedException;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.collaboration.AnnotationLeaseService;
import de.uniwue.zpd.dachs.larex.backend.service.notification.NotificationService;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageService;
import de.uniwue.zpd.dachs.larex.backend.service.security.AuthorizationPolicyService;
import de.uniwue.zpd.dachs.larex.backend.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import de.uniwue.zpd.dachs.larex.backend.dto.AnnotationCollaborationDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnotationLeaseServiceAccessTest {

    @Mock
    private PageService pageService;
    @Mock
    private AuthorizationPolicyService authorizationPolicyService;
    @Mock
    private UserService userService;
    @Mock
    private NotificationService notificationService;

    private AnnotationLeaseService service;

    @BeforeEach
    void setUp() {
        service = new AnnotationLeaseService(
                pageService,
                authorizationPolicyService,
                userService,
                notificationService
        );
        org.springframework.test.util.ReflectionTestUtils.setField(service, "leaseTtlMs", 45_000L);
    }

    @AfterEach
    void completeReservationTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            for (var synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void reservationBlocksEveryAcquisitionPathAndIsReleasedOnRollback() {
        var context = editorContext("page-1");
        when(pageService.pageBelongsToProject("page-1", "project-1")).thenReturn(true);
        TransactionSynchronizationManager.initSynchronization();
        service.reservePagesForMove(List.of("page-1"));
        assertThrows(AnnotationLeaseLockedException.class, () -> service.joinLease(context, "editor"));
        assertThrows(AnnotationLeaseLockedException.class, () -> service.heartbeat(context, "editor"));
        assertThrows(AnnotationLeaseLockedException.class, () -> service.requestTakeoverAction(context, true, "editor"));
        assertThrows(AnnotationLeaseLockedException.class, () -> service.assertWriteAccess(context, "user-1"));
        completeReservationTransaction();
        assertEquals("user-1", service.joinLease(context, "editor").editor().user().id());
    }

    @Test
    void failedReservationDoesNotReserveAnyOtherPage() {
        var active = editorContext("page-1");
        var other = editorContext("page-2");
        when(pageService.pageBelongsToProject("page-1", "project-1")).thenReturn(true);
        when(pageService.pageBelongsToProject("page-2", "project-1")).thenReturn(true);
        service.joinLease(active, "active");
        TransactionSynchronizationManager.initSynchronization();
        assertThrows(AnnotationLeaseLockedException.class, () -> service.reservePagesForMove(List.of("page-2", "page-1")));
        assertEquals("user-1", service.joinLease(other, "other").editor().user().id());
    }

    @Test
    void staleContextsCannotAcquireOrSaveAfterOwnershipChanges() {
        var context = editorContext("page-1");
        when(pageService.pageBelongsToProject("page-1", "project-1")).thenReturn(false);
        assertThrows(AnnotationLeaseLockedException.class, () -> service.joinLease(context, "editor"));
        assertThrows(AnnotationLeaseLockedException.class, () -> service.assertWriteAccess(context, "user-1"));
    }

    private AnnotationLeaseService.RoomAccessContext editorContext(String pageId) {
        return new AnnotationLeaseService.RoomAccessContext("workspace-1", "project-1", pageId, "xml-" + pageId,
                "project-1:" + pageId, "Project", "Page", true, true,
                new AnnotationCollaborationDto.UserSummary("user-1", "user", "User", null), new PageXml());
    }

    @Test
    void resolveRoomAccess_rejectsUserWithoutPageWorkspaceAccessBeforeXmlLookup() {
        when(pageService.getPageById("page-1", "user-other")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.resolveRoomAccess("project-1", "page-1", "xml-1", "user-other"));

        verify(pageService, never()).getXmlById("xml-1", "user-other");
    }

    @Test
    void resolvePageAccess_rejectsProjectAndPageIdMismatch() {
        Page page = page("project-actual", "page-1", false, false);
        when(pageService.getPageById("page-1", "user-1")).thenReturn(Optional.of(page));

        assertThrows(IllegalArgumentException.class,
                () -> service.resolvePageAccess("project-route", "page-1", "user-1"));
    }

    @Test
    void resolveRoomAccess_rejectsXmlAndPageIdMismatch() {
        Page routePage = page("project-1", "page-1", false, false);
        Page otherPage = page("project-1", "page-other", false, false);
        PageXml xml = new PageXml();
        xml.setId("xml-other");
        xml.setPage(otherPage);

        when(pageService.getPageById("page-1", "user-1")).thenReturn(Optional.of(routePage));
        when(authorizationPolicyService.canAccessWorkspace("workspace-1", "user-1")).thenReturn(true);
        when(pageService.getXmlById("xml-other", "user-1")).thenReturn(xml);

        assertThrows(IllegalArgumentException.class,
                () -> service.resolveRoomAccess("project-1", "page-1", "xml-other", "user-1"));
    }

    @Test
    void assertPageWriteAccess_rejectsLockedPage() {
        Page page = page("project-1", "page-1", false, true);
        when(pageService.getPageById("page-1", "user-1")).thenReturn(Optional.of(page));
        when(authorizationPolicyService.canAccessWorkspace("workspace-1", "user-1")).thenReturn(true);

        AnnotationLeaseLockedException error = assertThrows(AnnotationLeaseLockedException.class,
                () -> service.assertPageWriteAccess("project-1", "page-1", "user-1"));

        assertEquals("editing-disabled", error.getReason());
    }

    @Test
    void resolvePageAccess_allowsWorkspaceMemberToReadLockedPage() {
        Page page = page("project-1", "page-1", true, false);
        when(pageService.getPageById("page-1", "user-1")).thenReturn(Optional.of(page));
        when(authorizationPolicyService.canAccessWorkspace("workspace-1", "user-1")).thenReturn(true);

        AnnotationLeaseService.PageAccessContext context =
                service.resolvePageAccess("project-1", "page-1", "user-1");

        assertEquals("workspace-1", context.workspaceId());
        assertFalse(context.canEdit());
    }

    private Page page(String projectId, String pageId, boolean projectLocked, boolean pageLocked) {
        Library library = new Library();
        library.setWorkspaceId("workspace-1");

        Project project = new Project();
        project.setId(projectId);
        project.setName("Project");
        project.setLibrary(library);
        project.setLocked(projectLocked);

        Page page = new Page();
        page.setId(pageId);
        page.setName("Page");
        page.setProject(project);
        page.setLocked(pageLocked);
        return page;
    }
}

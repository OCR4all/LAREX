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

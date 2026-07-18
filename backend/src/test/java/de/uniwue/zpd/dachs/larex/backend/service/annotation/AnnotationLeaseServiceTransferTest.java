package de.uniwue.zpd.dachs.larex.backend.service.annotation;

import de.uniwue.zpd.dachs.larex.backend.dto.AnnotationCollaborationDto;
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
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class AnnotationLeaseServiceTransferTest {

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
        ReflectionTestUtils.setField(service, "leaseTtlMs", 45_000L);
    }

    @Test
    void acceptedTakeoverRemainsWithRequesterWhenPreviousEditorHeartbeats() {
        AnnotationLeaseService.RoomAccessContext previousEditor = context("previous-editor", false);
        AnnotationLeaseService.RoomAccessContext requester = context("requester", false);

        service.joinLease(previousEditor, "previous-instance");
        service.joinLease(requester, "requester-instance");
        service.requestTakeover(requester, false);

        AnnotationCollaborationDto.LeaseState accepted =
                service.respondToTakeover(previousEditor, "accept", "save");

        assertEditor(accepted, "requester");
        assertFalse(accepted.leaseOwner());

        AnnotationCollaborationDto.LeaseState afterPreviousHeartbeat =
                service.heartbeat(previousEditor, "previous-instance");
        assertEditor(afterPreviousHeartbeat, "requester");
        assertFalse(afterPreviousHeartbeat.leaseOwner());

        AnnotationCollaborationDto.LeaseState afterRequesterHeartbeat =
                service.heartbeat(requester, "requester-instance");
        assertEditor(afterRequesterHeartbeat, "requester");
        assertTrue(afterRequesterHeartbeat.leaseOwner());
    }

    @Test
    void forcedTakeoverRemainsWithRequesterWhenPreviousEditorHeartbeats() {
        AnnotationLeaseService.RoomAccessContext previousEditor = context("previous-editor", false);
        AnnotationLeaseService.RoomAccessContext manager = context("manager", true);

        service.joinLease(previousEditor, "previous-instance");
        service.joinLease(manager, "manager-instance");

        AnnotationCollaborationDto.LeaseState forced = service.requestTakeover(manager, true);
        assertEditor(forced, "manager");
        assertTrue(forced.leaseOwner());

        AnnotationCollaborationDto.LeaseState afterPreviousHeartbeat =
                service.heartbeat(previousEditor, "previous-instance");
        assertEditor(afterPreviousHeartbeat, "manager");
        assertFalse(afterPreviousHeartbeat.leaseOwner());

        AnnotationCollaborationDto.LeaseState afterManagerHeartbeat =
                service.heartbeat(manager, "manager-instance");
        assertEditor(afterManagerHeartbeat, "manager");
        assertTrue(afterManagerHeartbeat.leaseOwner());
    }

    @Test
    void transferredLeaseGetsFullTtlEvenBeforeRequesterInstanceHeartbeats() {
        AnnotationLeaseService.RoomAccessContext previousEditor = context("previous-editor", false);
        AnnotationLeaseService.RoomAccessContext manager = context("manager", true);

        service.joinLease(previousEditor, "previous-instance");

        AnnotationCollaborationDto.LeaseState forced = service.requestTakeover(manager, true);
        assertEditor(forced, "manager");
        assertTrue(forced.leaseOwner());

        AnnotationCollaborationDto.LeaseState afterPreviousHeartbeat =
                service.heartbeat(previousEditor, "previous-instance");
        assertEditor(afterPreviousHeartbeat, "manager");
        assertFalse(afterPreviousHeartbeat.leaseOwner());
    }

    private AnnotationLeaseService.RoomAccessContext context(String userId, boolean canForceTakeover) {
        AnnotationCollaborationDto.UserSummary user = new AnnotationCollaborationDto.UserSummary(
                userId,
                userId,
                userId,
                null
        );
        return new AnnotationLeaseService.RoomAccessContext(
                "workspace-1",
                "project-1",
                "page-1",
                "xml-1",
                "project-1:page-1:xml-1",
                "Project",
                "Page",
                true,
                canForceTakeover,
                user,
                null
        );
    }

    private void assertEditor(AnnotationCollaborationDto.LeaseState lease, String expectedUserId) {
        assertEquals(expectedUserId, lease.editor().user().id());
    }
}

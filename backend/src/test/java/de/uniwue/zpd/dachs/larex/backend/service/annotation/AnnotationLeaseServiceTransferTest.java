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
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        service.requestTakeoverAction(requester, false, "requester-instance");

        AnnotationCollaborationDto.LeaseState accepted =
                service.respondToTakeover(previousEditor, "accept", "save");

        assertEditor(accepted, "requester");

        AnnotationCollaborationDto.LeaseState afterPreviousHeartbeat =
                service.heartbeat(previousEditor, "previous-instance");
        assertEditor(afterPreviousHeartbeat, "requester");

        AnnotationCollaborationDto.LeaseState afterRequesterHeartbeat =
                service.heartbeat(requester, "requester-instance");
        assertEditor(afterRequesterHeartbeat, "requester");
    }

    @Test
    void forcedTakeoverRemainsWithRequesterWhenPreviousEditorHeartbeats() {
        AnnotationLeaseService.RoomAccessContext previousEditor = context("previous-editor", false);
        AnnotationLeaseService.RoomAccessContext manager = context("manager", true);

        service.joinLease(previousEditor, "previous-instance");
        service.joinLease(manager, "manager-instance");

        AnnotationCollaborationDto.LeaseState forced =
                service.requestTakeoverAction(manager, true, "manager-instance").lease();
        assertEditor(forced, "manager");

        AnnotationCollaborationDto.LeaseState afterPreviousHeartbeat =
                service.heartbeat(previousEditor, "previous-instance");
        assertEditor(afterPreviousHeartbeat, "manager");

        AnnotationCollaborationDto.LeaseState afterManagerHeartbeat =
                service.heartbeat(manager, "manager-instance");
        assertEditor(afterManagerHeartbeat, "manager");
    }

    @Test
    void transferredLeaseGetsFullTtlEvenBeforeRequesterInstanceHeartbeats() {
        AnnotationLeaseService.RoomAccessContext previousEditor = context("previous-editor", false);
        AnnotationLeaseService.RoomAccessContext manager = context("manager", true);

        service.joinLease(previousEditor, "previous-instance");

        AnnotationCollaborationDto.LeaseState forced =
                service.requestTakeoverAction(manager, true, "manager-instance").lease();
        assertEditor(forced, "manager");

        AnnotationCollaborationDto.LeaseState afterPreviousHeartbeat =
                service.heartbeat(previousEditor, "previous-instance");
        assertEditor(afterPreviousHeartbeat, "manager");
    }

    @Test
    void acceptedTakeoverBindsLeaseToRequestingInstanceWithoutPriorJoin() {
        AnnotationLeaseService.RoomAccessContext previousEditor = context("previous-editor", false);
        AnnotationLeaseService.RoomAccessContext requester = context("requester", false);

        service.joinLease(previousEditor, "previous-instance");

        AnnotationCollaborationDto.LeaseActionResult requested =
                service.requestTakeoverAction(requester, false, "requester-instance");
        assertEquals(AnnotationCollaborationDto.LeaseActionOutcome.PENDING, requested.outcome());

        AnnotationCollaborationDto.LeaseState accepted =
                service.respondToTakeover(previousEditor, "accept", "save");
        assertEditor(accepted, "requester");

        AnnotationCollaborationDto.LeaseState renewed =
                service.heartbeat(requester, "requester-instance");
        assertEditor(renewed, "requester");
    }

    @Test
    void forcedTakeoverBindsLeaseToRequestingInstanceWithoutPriorJoin() {
        AnnotationLeaseService.RoomAccessContext previousEditor = context("previous-editor", false);
        AnnotationLeaseService.RoomAccessContext manager = context("manager", true);

        service.joinLease(previousEditor, "previous-instance");

        AnnotationCollaborationDto.LeaseState forced =
                service.requestTakeoverAction(manager, true, "manager-instance").lease();
        assertEditor(forced, "manager");

        AnnotationCollaborationDto.LeaseState renewed =
                service.heartbeat(manager, "manager-instance");
        assertEditor(renewed, "manager");
    }

    @Test
    void repeatedAndCompetingTakeoverRequestsHaveExplicitOutcomes() {
        AnnotationLeaseService.RoomAccessContext owner = context("owner", false);
        AnnotationLeaseService.RoomAccessContext requester = context("requester", false);
        AnnotationLeaseService.RoomAccessContext competingRequester = context("competing-requester", false);

        service.joinLease(owner, "owner-instance");
        service.joinLease(requester, "requester-instance");
        service.joinLease(competingRequester, "competing-instance");

        AnnotationCollaborationDto.LeaseActionResult first =
                service.requestTakeoverAction(requester, false);
        AnnotationCollaborationDto.LeaseActionResult repeated =
                service.requestTakeoverAction(requester, false);
        AnnotationCollaborationDto.LeaseActionResult competing =
                service.requestTakeoverAction(competingRequester, false);

        assertEquals(AnnotationCollaborationDto.LeaseActionOutcome.PENDING, first.outcome());
        assertEquals(AnnotationCollaborationDto.LeaseActionOutcome.PENDING, repeated.outcome());
        assertEquals(first.lease().leaseEpoch(), repeated.lease().leaseEpoch());
        assertEquals(AnnotationCollaborationDto.LeaseActionOutcome.CONFLICT, competing.outcome());
        assertEquals("requester", competing.lease().pendingTakeover().requester().id());
    }

    @Test
    void unauthorizedActionsHaveExplicitForbiddenOutcome() {
        AnnotationLeaseService.RoomAccessContext owner = context("owner", false);
        AnnotationLeaseService.RoomAccessContext requester = context("requester", false);

        service.joinLease(owner, "owner-instance");
        service.joinLease(requester, "requester-instance");

        AnnotationCollaborationDto.LeaseActionResult force =
                service.requestTakeoverAction(requester, true);
        AnnotationCollaborationDto.LeaseActionResult response =
                service.respondToTakeoverAction(requester, "decline", "save");

        assertEquals(AnnotationCollaborationDto.LeaseActionOutcome.FORBIDDEN, force.outcome());
        assertEquals(AnnotationCollaborationDto.LeaseActionOutcome.FORBIDDEN, response.outcome());
        assertEditor(response.lease(), "owner");
    }

    @Test
    void invalidTakeoverResponsesAreRejectedWithoutMutatingPendingRequest() {
        AnnotationLeaseService.RoomAccessContext owner = context("owner", false);
        AnnotationLeaseService.RoomAccessContext requester = context("requester", false);

        service.joinLease(owner, "owner-instance");
        service.joinLease(requester, "requester-instance");
        AnnotationCollaborationDto.LeaseActionResult pending =
                service.requestTakeoverAction(requester, false);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.respondToTakeoverAction(owner, "approve", "save")
        );
        assertEquals(
                pending.lease().leaseEpoch(),
                service.getLeaseState(owner).leaseEpoch()
        );
        assertEquals(
                "requester",
                service.getLeaseState(owner).pendingTakeover().requester().id()
        );
    }

    @Test
    void closingOneOwnerTabKeepsLeaseWithAnotherLiveTabOfSameUser() {
        AnnotationLeaseService.RoomAccessContext owner = context("owner", false);
        AnnotationLeaseService.RoomAccessContext viewer = context("viewer", false);

        service.joinLease(owner, "owner-tab-a");
        service.joinLease(owner, "owner-tab-b");
        service.joinLease(viewer, "viewer-instance");

        AnnotationCollaborationDto.LeaseState afterFirstTabCloses =
                service.releaseLease(owner, "owner-tab-a");

        assertEditor(afterFirstTabCloses, "owner");

        AnnotationCollaborationDto.LeaseState afterSecondTabHeartbeat =
                service.heartbeat(owner, "owner-tab-b");
        assertEditor(afterSecondTabHeartbeat, "owner");
    }

    @Test
    void requesterDepartureClearsPendingRequestForAnotherCollaborator() {
        AnnotationLeaseService.RoomAccessContext owner = context("owner", false);
        AnnotationLeaseService.RoomAccessContext firstRequester = context("first-requester", false);
        AnnotationLeaseService.RoomAccessContext secondRequester = context("second-requester", false);

        service.joinLease(owner, "owner-instance");
        service.joinLease(firstRequester, "first-instance");
        service.joinLease(secondRequester, "second-instance");
        service.requestTakeoverAction(firstRequester, false);

        service.releaseLease(firstRequester, "first-instance");
        AnnotationCollaborationDto.LeaseActionResult secondRequest =
                service.requestTakeoverAction(secondRequester, false);

        assertEquals(AnnotationCollaborationDto.LeaseActionOutcome.PENDING, secondRequest.outcome());
        assertEquals(
                "second-requester",
                secondRequest.lease().pendingTakeover().requester().id()
        );
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

package de.uniwue.zpd.dachs.larex.backend.controller.annotation;

import de.uniwue.zpd.dachs.larex.backend.dto.AnnotationCollaborationDto;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.collaboration.AnnotationLeaseService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/projects/{projectId}/pages/{pageId}/annotations/{xmlId}/collaboration")
public class AnnotationCollaborationController {

    private final AnnotationLeaseService annotationLeaseService;

    public AnnotationCollaborationController(AnnotationLeaseService annotationLeaseService) {
        this.annotationLeaseService = annotationLeaseService;
    }

    @GetMapping("/bootstrap")
    public ResponseEntity<AnnotationCollaborationDto.BootstrapResponse> bootstrap(
            @PathVariable String projectId,
            @PathVariable String pageId,
            @PathVariable String xmlId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        AnnotationLeaseService.RoomAccessContext context = annotationLeaseService.resolveRoomAccess(projectId, pageId, xmlId, userId);
        String persistedRevision = resolvePersistedRevision(context);

        return ResponseEntity.ok(new AnnotationCollaborationDto.BootstrapResponse(
                context.roomKey(),
                context.workspaceId(),
                context.projectId(),
                context.pageId(),
                context.xmlId(),
                persistedRevision,
                context.canEdit(),
                context.canForceTakeover(),
                context.user(),
                annotationLeaseService.getLeaseState(context)
        ));
    }

    @GetMapping("/revision")
    public ResponseEntity<AnnotationCollaborationDto.RevisionResponse> revision(
            @PathVariable String projectId,
            @PathVariable String pageId,
            @PathVariable String xmlId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        AnnotationLeaseService.RoomAccessContext context = annotationLeaseService.resolveRoomAccess(projectId, pageId, xmlId, userId);

        return ResponseEntity.ok(new AnnotationCollaborationDto.RevisionResponse(
                context.workspaceId(),
                context.projectId(),
                context.pageId(),
                context.xmlId(),
                resolvePersistedRevision(context),
                context.pageXml().getUpdated()
        ));
    }

    @PostMapping("/lease/join")
    public ResponseEntity<AnnotationCollaborationDto.LeaseResponse> joinLease(
            @PathVariable String projectId,
            @PathVariable String pageId,
            @PathVariable String xmlId,
            @RequestBody AnnotationCollaborationDto.LeaseInstancePayload payload,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        AnnotationLeaseService.RoomAccessContext context = annotationLeaseService.resolveRoomAccess(projectId, pageId, xmlId, userId);
        return ResponseEntity.ok(new AnnotationCollaborationDto.LeaseResponse(
                context.roomKey(),
                annotationLeaseService.joinLease(context, requireInstanceId(payload))
        ));
    }

    @PostMapping("/lease/heartbeat")
    public ResponseEntity<AnnotationCollaborationDto.LeaseResponse> heartbeat(
            @PathVariable String projectId,
            @PathVariable String pageId,
            @PathVariable String xmlId,
            @RequestBody AnnotationCollaborationDto.LeaseInstancePayload payload,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        AnnotationLeaseService.RoomAccessContext context = annotationLeaseService.resolveRoomAccess(projectId, pageId, xmlId, userId);
        return ResponseEntity.ok(new AnnotationCollaborationDto.LeaseResponse(
                context.roomKey(),
                annotationLeaseService.heartbeat(context, requireInstanceId(payload))
        ));
    }

    @PostMapping("/lease/request")
    public ResponseEntity<AnnotationCollaborationDto.LeaseActionResponse> requestTakeover(
            @PathVariable String projectId,
            @PathVariable String pageId,
            @PathVariable String xmlId,
            @RequestBody(required = false) AnnotationCollaborationDto.TakeoverRequestPayload payload,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        AnnotationLeaseService.RoomAccessContext context = annotationLeaseService.resolveRoomAccess(projectId, pageId, xmlId, userId);
        AnnotationCollaborationDto.LeaseActionResult result =
                annotationLeaseService.requestTakeoverAction(context, payload != null && payload.force());
        return ResponseEntity.ok(new AnnotationCollaborationDto.LeaseActionResponse(
                context.roomKey(),
                result.lease(),
                result.outcome(),
                result.message()
        ));
    }

    @PostMapping("/lease/respond")
    public ResponseEntity<AnnotationCollaborationDto.LeaseActionResponse> respondToTakeover(
            @PathVariable String projectId,
            @PathVariable String pageId,
            @PathVariable String xmlId,
            @Valid @RequestBody AnnotationCollaborationDto.TakeoverResponsePayload payload,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        AnnotationLeaseService.RoomAccessContext context = annotationLeaseService.resolveRoomAccess(projectId, pageId, xmlId, userId);
        AnnotationCollaborationDto.LeaseActionResult result =
                annotationLeaseService.respondToTakeoverAction(context, payload.decision(), payload.handoffMode());
        return ResponseEntity.ok(new AnnotationCollaborationDto.LeaseActionResponse(
                context.roomKey(),
                result.lease(),
                result.outcome(),
                result.message()
        ));
    }

    @PostMapping("/lease/release")
    public ResponseEntity<AnnotationCollaborationDto.LeaseResponse> releaseLease(
            @PathVariable String projectId,
            @PathVariable String pageId,
            @PathVariable String xmlId,
            @RequestBody AnnotationCollaborationDto.LeaseInstancePayload payload,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        AnnotationLeaseService.RoomAccessContext context = annotationLeaseService.resolveRoomAccess(projectId, pageId, xmlId, userId);
        return ResponseEntity.ok(new AnnotationCollaborationDto.LeaseResponse(
                context.roomKey(),
                annotationLeaseService.releaseLease(context, requireInstanceId(payload))
        ));
    }

    private String resolvePersistedRevision(AnnotationLeaseService.RoomAccessContext context) {
        if (context.pageXml().getUpdated() != null) {
            return context.pageXml().getUpdated().toString();
        }
        if (context.pageXml().getCreated() != null) {
            return context.pageXml().getCreated().toString();
        }
        return context.pageXml().getId();
    }

    private String requireInstanceId(AnnotationCollaborationDto.LeaseInstancePayload payload) {
        if (payload == null || payload.instanceId() == null || payload.instanceId().isBlank()) {
            throw new IllegalArgumentException("Missing collaboration instance ID");
        }
        return payload.instanceId();
    }
}

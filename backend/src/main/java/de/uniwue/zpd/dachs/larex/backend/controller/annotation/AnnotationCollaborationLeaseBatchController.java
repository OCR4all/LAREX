package de.uniwue.zpd.dachs.larex.backend.controller.annotation;

import de.uniwue.zpd.dachs.larex.backend.dto.AnnotationCollaborationDto;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.collaboration.AnnotationLeaseService;
import de.uniwue.zpd.dachs.larex.backend.service.dataset.DatasetCopyAnnotationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/collaboration/leases")
public class AnnotationCollaborationLeaseBatchController {

    private final AnnotationLeaseService annotationLeaseService;
    private final DatasetCopyAnnotationService datasetCopyAnnotationService;

    public AnnotationCollaborationLeaseBatchController(
            AnnotationLeaseService annotationLeaseService,
            DatasetCopyAnnotationService datasetCopyAnnotationService) {
        this.annotationLeaseService = annotationLeaseService;
        this.datasetCopyAnnotationService = datasetCopyAnnotationService;
    }

    @PostMapping("/renew")
    public ResponseEntity<AnnotationCollaborationDto.LeaseRenewalBatchResponse> renew(
            @Valid @RequestBody AnnotationCollaborationDto.LeaseRenewalBatchRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        List<AnnotationCollaborationDto.LeaseResponse> renewals = request.targets().stream()
                .map(target -> renewTarget(target, request.instanceId(), userId))
                .toList();

        return ResponseEntity.ok(new AnnotationCollaborationDto.LeaseRenewalBatchResponse(renewals));
    }

    private AnnotationCollaborationDto.LeaseResponse renewTarget(
            AnnotationCollaborationDto.LeaseRenewalTarget target,
            String instanceId,
            String userId) {

        AnnotationLeaseService.RoomAccessContext context = switch (target.scope()) {
            case PROJECT -> resolveProjectContext(target, userId);
            case DATASET -> resolveDatasetContext(target, userId);
        };

        return new AnnotationCollaborationDto.LeaseResponse(
                context.roomKey(),
                annotationLeaseService.heartbeat(context, instanceId)
        );
    }

    private AnnotationLeaseService.RoomAccessContext resolveProjectContext(
            AnnotationCollaborationDto.LeaseRenewalTarget target,
            String userId) {
        return annotationLeaseService.resolveRoomAccess(
                requireValue(target.projectId(), "projectId"),
                requireValue(target.pageId(), "pageId"),
                target.xmlId(),
                userId
        );
    }

    private AnnotationLeaseService.RoomAccessContext resolveDatasetContext(
            AnnotationCollaborationDto.LeaseRenewalTarget target,
            String userId) {
        String workspaceId = requireValue(target.workspaceId(), "workspaceId");
        String datasetId = requireValue(target.datasetId(), "datasetId");
        String itemId = requireValue(target.itemId(), "itemId");

        DatasetCopyAnnotationService.DatasetCopyXmlAccessContext context =
                datasetCopyAnnotationService.resolveAccessContext(
                        workspaceId,
                        datasetId,
                        itemId,
                        target.xmlId(),
                        userId
                );

        String projectId = valueOrFallback(context.sourceProjectId(), datasetId);
        String pageId = valueOrFallback(context.sourcePageId(), itemId);
        String projectLabel = valueOrFallback(context.sourceProjectName(), context.dataset().getName());
        String pageLabel = valueOrFallback(context.sourcePageName(), context.item().getSourcePageName());

        return new AnnotationLeaseService.RoomAccessContext(
                workspaceId,
                projectId,
                pageId,
                context.copyXml().getId(),
                context.roomKey(),
                projectLabel,
                pageLabel,
                context.canEdit(),
                context.canForceTakeover(),
                annotationLeaseService.resolveUserSummary(userId),
                null
        );
    }

    private String requireValue(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing collaboration renewal " + field);
        }
        return value;
    }

    private String valueOrFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}

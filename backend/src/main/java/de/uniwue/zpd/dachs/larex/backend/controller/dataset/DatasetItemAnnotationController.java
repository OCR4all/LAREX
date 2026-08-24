package de.uniwue.zpd.dachs.larex.backend.controller.dataset;

import de.uniwue.zpd.dachs.larex.backend.dto.AnnotationCollaborationDto;
import de.uniwue.zpd.dachs.larex.backend.dto.PageXmlTextDto;
import de.uniwue.zpd.dachs.larex.backend.dto.PageXmlVersionDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.collaboration.AnnotationLeaseService;
import de.uniwue.zpd.dachs.larex.backend.service.dataset.DatasetCopyAnnotationService;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

@RestController
@RequestMapping("/workspaces/{workspaceId}/datasets/{datasetId}/items/{itemId}")
public class DatasetItemAnnotationController {

    private final DatasetCopyAnnotationService datasetCopyAnnotationService;
    private final AnnotationLeaseService annotationLeaseService;

    public DatasetItemAnnotationController(DatasetCopyAnnotationService datasetCopyAnnotationService,
                                           AnnotationLeaseService annotationLeaseService) {
        this.datasetCopyAnnotationService = datasetCopyAnnotationService;
        this.annotationLeaseService = annotationLeaseService;
    }

    @GetMapping("/annotations/{xmlId}")
    public ResponseEntity<PageDto> loadAnnotation(
            @PathVariable String workspaceId,
            @PathVariable String datasetId,
            @PathVariable String itemId,
            @PathVariable String xmlId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        try {
            return ResponseEntity.ok(datasetCopyAnnotationService.loadAnnotation(workspaceId, datasetId, itemId, xmlId, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (UnsupportedOperationException e) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/annotations/{xmlId}")
    public ResponseEntity<Void> saveAnnotation(
            @PathVariable String workspaceId,
            @PathVariable String datasetId,
            @PathVariable String itemId,
            @PathVariable String xmlId,
            @RequestBody PageDto pageDto,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        try {
            DatasetCopyAnnotationService.DatasetCopyXmlAccessContext context = datasetCopyAnnotationService.resolveAccessContext(
                    workspaceId,
                    datasetId,
                    itemId,
                    xmlId,
                    userId
            );
            annotationLeaseService.assertWriteAccess(toRoomAccessContext(context, workspaceId, datasetId, itemId, userId), userId);
            datasetCopyAnnotationService.saveAnnotation(workspaceId, datasetId, itemId, xmlId, pageDto, userId);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (UnsupportedOperationException e) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/annotations/{xmlId}/collaboration/bootstrap")
    public ResponseEntity<AnnotationCollaborationDto.BootstrapResponse> bootstrap(
            @PathVariable String workspaceId,
            @PathVariable String datasetId,
            @PathVariable String itemId,
            @PathVariable String xmlId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        DatasetCopyAnnotationService.DatasetCopyXmlAccessContext context = datasetCopyAnnotationService.resolveAccessContext(
                workspaceId,
                datasetId,
                itemId,
                xmlId,
                userId
        );
        AnnotationLeaseService.RoomAccessContext roomContext = toRoomAccessContext(context, workspaceId, datasetId, itemId, userId);

        return ResponseEntity.ok(new AnnotationCollaborationDto.BootstrapResponse(
                roomContext.roomKey(),
                roomContext.workspaceId(),
                roomContext.projectId(),
                roomContext.pageId(),
                roomContext.xmlId(),
                context.persistedRevision(),
                roomContext.canEdit(),
                roomContext.canForceTakeover(),
                roomContext.user(),
                annotationLeaseService.getLeaseState(roomContext)
        ));
    }

    @GetMapping("/annotations/{xmlId}/collaboration/revision")
    public ResponseEntity<AnnotationCollaborationDto.RevisionResponse> revision(
            @PathVariable String workspaceId,
            @PathVariable String datasetId,
            @PathVariable String itemId,
            @PathVariable String xmlId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        DatasetCopyAnnotationService.DatasetCopyXmlAccessContext context = datasetCopyAnnotationService.resolveAccessContext(
                workspaceId,
                datasetId,
                itemId,
                xmlId,
                userId
        );
        AnnotationLeaseService.RoomAccessContext roomContext = toRoomAccessContext(context, workspaceId, datasetId, itemId, userId);

        return ResponseEntity.ok(new AnnotationCollaborationDto.RevisionResponse(
                roomContext.workspaceId(),
                roomContext.projectId(),
                roomContext.pageId(),
                roomContext.xmlId(),
                context.persistedRevision(),
                context.updatedAt()
        ));
    }

    @PostMapping("/annotations/{xmlId}/collaboration/lease/join")
    public ResponseEntity<AnnotationCollaborationDto.LeaseResponse> joinLease(
            @PathVariable String workspaceId,
            @PathVariable String datasetId,
            @PathVariable String itemId,
            @PathVariable String xmlId,
            @RequestBody AnnotationCollaborationDto.LeaseInstancePayload payload,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        DatasetCopyAnnotationService.DatasetCopyXmlAccessContext context = datasetCopyAnnotationService.resolveAccessContext(
                workspaceId,
                datasetId,
                itemId,
                xmlId,
                userId
        );
        AnnotationLeaseService.RoomAccessContext roomContext = toRoomAccessContext(context, workspaceId, datasetId, itemId, userId);

        return ResponseEntity.ok(new AnnotationCollaborationDto.LeaseResponse(
                roomContext.roomKey(),
                annotationLeaseService.joinLease(roomContext, requireInstanceId(payload))
        ));
    }

    @PostMapping("/annotations/{xmlId}/collaboration/lease/heartbeat")
    public ResponseEntity<AnnotationCollaborationDto.LeaseResponse> heartbeat(
            @PathVariable String workspaceId,
            @PathVariable String datasetId,
            @PathVariable String itemId,
            @PathVariable String xmlId,
            @RequestBody AnnotationCollaborationDto.LeaseInstancePayload payload,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        DatasetCopyAnnotationService.DatasetCopyXmlAccessContext context = datasetCopyAnnotationService.resolveAccessContext(
                workspaceId,
                datasetId,
                itemId,
                xmlId,
                userId
        );
        AnnotationLeaseService.RoomAccessContext roomContext = toRoomAccessContext(context, workspaceId, datasetId, itemId, userId);

        return ResponseEntity.ok(new AnnotationCollaborationDto.LeaseResponse(
                roomContext.roomKey(),
                annotationLeaseService.heartbeat(roomContext, requireInstanceId(payload))
        ));
    }

    @PostMapping("/annotations/{xmlId}/collaboration/lease/request")
    public ResponseEntity<AnnotationCollaborationDto.LeaseActionResponse> requestTakeover(
            @PathVariable String workspaceId,
            @PathVariable String datasetId,
            @PathVariable String itemId,
            @PathVariable String xmlId,
            @Valid @RequestBody AnnotationCollaborationDto.TakeoverRequestPayload payload,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        DatasetCopyAnnotationService.DatasetCopyXmlAccessContext context = datasetCopyAnnotationService.resolveAccessContext(
                workspaceId,
                datasetId,
                itemId,
                xmlId,
                userId
        );
        AnnotationLeaseService.RoomAccessContext roomContext = toRoomAccessContext(context, workspaceId, datasetId, itemId, userId);

        AnnotationCollaborationDto.LeaseActionResult result =
                annotationLeaseService.requestTakeoverAction(roomContext, payload.force(), payload.instanceId());
        return ResponseEntity.ok(new AnnotationCollaborationDto.LeaseActionResponse(
                roomContext.roomKey(),
                result.lease(),
                result.outcome(),
                result.message()
        ));
    }

    @PostMapping("/annotations/{xmlId}/collaboration/lease/respond")
    public ResponseEntity<AnnotationCollaborationDto.LeaseActionResponse> respondToTakeover(
            @PathVariable String workspaceId,
            @PathVariable String datasetId,
            @PathVariable String itemId,
            @PathVariable String xmlId,
            @Valid @RequestBody AnnotationCollaborationDto.TakeoverResponsePayload payload,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        DatasetCopyAnnotationService.DatasetCopyXmlAccessContext context = datasetCopyAnnotationService.resolveAccessContext(
                workspaceId,
                datasetId,
                itemId,
                xmlId,
                userId
        );
        AnnotationLeaseService.RoomAccessContext roomContext = toRoomAccessContext(context, workspaceId, datasetId, itemId, userId);

        AnnotationCollaborationDto.LeaseActionResult result =
                annotationLeaseService.respondToTakeoverAction(roomContext, payload.decision(), payload.handoffMode());
        return ResponseEntity.ok(new AnnotationCollaborationDto.LeaseActionResponse(
                roomContext.roomKey(),
                result.lease(),
                result.outcome(),
                result.message()
        ));
    }

    @PostMapping("/annotations/{xmlId}/collaboration/lease/release")
    public ResponseEntity<AnnotationCollaborationDto.LeaseResponse> releaseLease(
            @PathVariable String workspaceId,
            @PathVariable String datasetId,
            @PathVariable String itemId,
            @PathVariable String xmlId,
            @RequestBody AnnotationCollaborationDto.LeaseInstancePayload payload,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        DatasetCopyAnnotationService.DatasetCopyXmlAccessContext context = datasetCopyAnnotationService.resolveAccessContext(
                workspaceId,
                datasetId,
                itemId,
                xmlId,
                userId
        );
        AnnotationLeaseService.RoomAccessContext roomContext = toRoomAccessContext(context, workspaceId, datasetId, itemId, userId);

        return ResponseEntity.ok(new AnnotationCollaborationDto.LeaseResponse(
                roomContext.roomKey(),
                annotationLeaseService.releaseLease(roomContext, requireInstanceId(payload))
        ));
    }

    @GetMapping("/annotations/{xmlId}/versions")
    public ResponseEntity<List<PageXmlVersionDto>> listVersions(
            @PathVariable String workspaceId,
            @PathVariable String datasetId,
            @PathVariable String itemId,
            @PathVariable String xmlId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        try {
            return ResponseEntity.ok(datasetCopyAnnotationService.listVersions(workspaceId, datasetId, itemId, xmlId, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/annotations/{xmlId}/versions/{versionId}")
    public ResponseEntity<String> getVersionContent(
            @PathVariable String workspaceId,
            @PathVariable String datasetId,
            @PathVariable String itemId,
            @PathVariable String xmlId,
            @PathVariable String versionId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        try {
            String content = datasetCopyAnnotationService.getVersionContent(workspaceId, datasetId, itemId, xmlId, versionId, userId);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_XML)
                    .body(content);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/annotations/{xmlId}/versions/{versionId}/annotation")
    public ResponseEntity<PageDto> getVersionAnnotation(
            @PathVariable String workspaceId,
            @PathVariable String datasetId,
            @PathVariable String itemId,
            @PathVariable String xmlId,
            @PathVariable String versionId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        try {
            PageDto pageDto = datasetCopyAnnotationService.loadVersionAnnotation(
                    workspaceId,
                    datasetId,
                    itemId,
                    xmlId,
                    versionId,
                    userId
            );
            return ResponseEntity.ok(pageDto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (UnsupportedOperationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/annotations/{xmlId}/versions/{versionId}/restore")
    public ResponseEntity<Void> restoreVersion(
            @PathVariable String workspaceId,
            @PathVariable String datasetId,
            @PathVariable String itemId,
            @PathVariable String xmlId,
            @PathVariable String versionId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        try {
            DatasetCopyAnnotationService.DatasetCopyXmlAccessContext context = datasetCopyAnnotationService.resolveAccessContext(
                    workspaceId,
                    datasetId,
                    itemId,
                    xmlId,
                    userId
            );
            annotationLeaseService.assertWriteAccess(toRoomAccessContext(context, workspaceId, datasetId, itemId, userId), userId);
            datasetCopyAnnotationService.restoreVersion(workspaceId, datasetId, itemId, xmlId, versionId, userId);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/xml/{xmlId}/text")
    public ResponseEntity<PageXmlTextDto.XmlTextResponse> getXmlText(
            @PathVariable String workspaceId,
            @PathVariable String datasetId,
            @PathVariable String itemId,
            @PathVariable String xmlId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        try {
            return ResponseEntity.ok(datasetCopyAnnotationService.getXmlText(workspaceId, datasetId, itemId, xmlId, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (UnsupportedOperationException e) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/xml/{xmlId}/validate")
    public ResponseEntity<PageXmlTextDto.XmlValidationResult> validateXmlText(
            @PathVariable String workspaceId,
            @PathVariable String datasetId,
            @PathVariable String itemId,
            @PathVariable String xmlId,
            @Valid @RequestBody PageXmlTextDto.ValidateRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        try {
            datasetCopyAnnotationService.assertXmlAccess(workspaceId, datasetId, itemId, xmlId, userId);
            return ResponseEntity.ok(datasetCopyAnnotationService.validateXmlText(request.xml()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (UnsupportedOperationException e) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        }
    }

    @PutMapping("/xml/{xmlId}/text")
    public ResponseEntity<?> saveXmlText(
            @PathVariable String workspaceId,
            @PathVariable String datasetId,
            @PathVariable String itemId,
            @PathVariable String xmlId,
            @Valid @RequestBody PageXmlTextDto.SaveRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        try {
            PageXmlTextDto.XmlValidationResult validation = datasetCopyAnnotationService.saveXmlText(
                    workspaceId,
                    datasetId,
                    itemId,
                    xmlId,
                    request.xml(),
                    request.comment(),
                    userId
            );
            if (!validation.valid()) {
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(validation);
            }
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (UnsupportedOperationException e) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/images/{imageId}/blob")
    public ResponseEntity<Resource> getImageBlob(
            @PathVariable String workspaceId,
            @PathVariable String datasetId,
            @PathVariable String itemId,
            @PathVariable String imageId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        try {
            DatasetCopyAnnotationService.DatasetCopyImageAccessContext context = datasetCopyAnnotationService.resolveImageAccessContext(
                    workspaceId,
                    datasetId,
                    itemId,
                    imageId,
                    userId
            );
            if (!Files.exists(context.imagePath())) {
                return ResponseEntity.notFound().build();
            }

            FileSystemResource resource = new FileSystemResource(context.imagePath());
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(context.copyImage().getMimeType()))
                    .header("Cache-Control", "public, max-age=3600")
                    .header("Content-Length", String.valueOf(resource.contentLength()))
                    .header("Accept-Ranges", "bytes")
                    .body(resource);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private AnnotationLeaseService.RoomAccessContext toRoomAccessContext(
            DatasetCopyAnnotationService.DatasetCopyXmlAccessContext context,
            String workspaceId,
            String datasetId,
            String itemId,
            String userId) {

        String projectId = normalizeContextValue(context.sourceProjectId(), datasetId);
        String pageId = normalizeContextValue(context.sourcePageId(), itemId);
        String projectLabel = normalizeContextValue(context.sourceProjectName(), context.dataset().getName());
        String pageLabel = normalizeContextValue(context.sourcePageName(), context.item().getSourcePageName());

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

    private String normalizeContextValue(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private String requireInstanceId(AnnotationCollaborationDto.LeaseInstancePayload payload) {
        if (payload == null || payload.instanceId() == null || payload.instanceId().isBlank()) {
            throw new IllegalArgumentException("Missing collaboration instance ID");
        }
        return payload.instanceId();
    }
}

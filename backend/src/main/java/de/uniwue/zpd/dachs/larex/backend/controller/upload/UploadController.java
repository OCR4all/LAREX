package de.uniwue.zpd.dachs.larex.backend.controller.upload;

import de.uniwue.zpd.dachs.larex.backend.dto.UploadSessionDto;
import de.uniwue.zpd.dachs.larex.backend.service.upload.ChunkedUploadService;
import de.uniwue.zpd.dachs.larex.backend.service.upload.UploadSessionEventBroadcaster;
import de.uniwue.zpd.dachs.larex.backend.service.upload.UploadSessionProcessingCoordinator;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/workspaces/{workspaceId}/projects/{projectId}/upload-sessions")
public class UploadController {

    private final ChunkedUploadService chunkedUploadService;
    private final UploadSessionProcessingCoordinator uploadSessionProcessingCoordinator;
    private final UploadSessionEventBroadcaster uploadSessionEventBroadcaster;

    public UploadController(ChunkedUploadService chunkedUploadService,
                            UploadSessionProcessingCoordinator uploadSessionProcessingCoordinator,
                            UploadSessionEventBroadcaster uploadSessionEventBroadcaster) {
        this.chunkedUploadService = chunkedUploadService;
        this.uploadSessionProcessingCoordinator = uploadSessionProcessingCoordinator;
        this.uploadSessionEventBroadcaster = uploadSessionEventBroadcaster;
    }

    /**
     * Create a new upload session with file metadata.
     * This initializes the session and returns file IDs for chunk uploads.
     */
    @PostMapping
    public ResponseEntity<UploadSessionDto.SessionResponse> createSession(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @Valid @RequestBody UploadSessionDto.CreateSessionRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        UploadSessionDto.SessionResponse session = chunkedUploadService.createSession(
                userId, workspaceId, projectId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    /**
     * Get the current status of an upload session.
     * Used for resuming interrupted uploads.
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<UploadSessionDto.SessionResponse> getSession(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @PathVariable String sessionId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        UploadSessionDto.SessionResponse session = chunkedUploadService.getSession(userId, sessionId);
        return ResponseEntity.ok(session);
    }

    @PostMapping("/{sessionId}/preflight")
    public ResponseEntity<UploadSessionDto.PdfPreflightResponse> preflightPdfSession(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @PathVariable String sessionId,
            @Valid @RequestBody(required = false) UploadSessionDto.PdfPreflightRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) throws IOException {

        UploadSessionDto.PdfPreflightResponse response = chunkedUploadService.preflightPdfSession(
                userId,
                workspaceId,
                projectId,
                sessionId,
                request
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping(path = "/{sessionId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSessionEvents(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @PathVariable String sessionId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        UploadSessionDto.SessionResponse session = chunkedUploadService.getSession(userId, sessionId);
        if (!workspaceId.equals(session.workspaceId()) || !projectId.equals(session.projectId())) {
            throw new IllegalArgumentException("Upload session does not belong to the requested project/workspace");
        }
        return uploadSessionEventBroadcaster.subscribe(sessionId);
    }

    /**
     * Upload a chunk for a specific file in the session.
     */
    @PostMapping("/{sessionId}/files/{fileId}/chunks")
    public ResponseEntity<UploadSessionDto.UploadChunkResponse> uploadChunk(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @PathVariable String sessionId,
            @PathVariable String fileId,
            @RequestParam("chunkIndex") int chunkIndex,
            @RequestParam("totalChunks") int totalChunks,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal(expression = "subject") String userId) throws IOException {

        UploadSessionDto.UploadChunkResponse response = chunkedUploadService.uploadChunk(
                userId, sessionId, fileId, chunkIndex, totalChunks, file);

        return ResponseEntity.ok(response);
    }

    /**
     * Finalize the upload session and trigger async processing.
     * Optionally include conflict resolutions.
     * Note: The async processor is called from the controller (after transaction commits)
     * to avoid race conditions where the processor reads stale data.
     */
    @PostMapping("/{sessionId}/finalize")
    public ResponseEntity<Void> finalizeSession(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @PathVariable String sessionId,
            @Valid @RequestBody(required = false) UploadSessionDto.FinalizeSessionRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        // This updates the session status to PROCESSING within a transaction
        chunkedUploadService.finalizeSession(userId, sessionId, request);

        // Trigger/coalesce background processing AFTER the transaction has committed
        // via the finalized event published in the service, and request once here as a fallback.
        uploadSessionProcessingCoordinator.requestProcessing(sessionId);

        return ResponseEntity.accepted().build();
    }

    /**
     * Cancel an upload session.
     */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> cancelSession(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @PathVariable String sessionId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        chunkedUploadService.cancelSession(userId, sessionId);
        return ResponseEntity.noContent().build();
    }
}

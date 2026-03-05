package de.uniwue.zpd.dachs.larex.backend.controller.upload;

import de.uniwue.zpd.dachs.larex.backend.dto.UploadSessionDto;
import de.uniwue.zpd.dachs.larex.backend.service.upload.ChunkedUploadService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/upload-sessions")
public class UserUploadSessionController {

    private final ChunkedUploadService chunkedUploadService;

    public UserUploadSessionController(ChunkedUploadService chunkedUploadService) {
        this.chunkedUploadService = chunkedUploadService;
    }

    /**
     * Get all active upload sessions for the current user.
     * Used to show overall upload progress and resume interrupted uploads.
     */
    @GetMapping
    public ResponseEntity<List<UploadSessionDto.SessionSummaryResponse>> getUserSessions(
            @AuthenticationPrincipal(expression = "subject") String userId) {

        List<UploadSessionDto.SessionSummaryResponse> sessions = chunkedUploadService.getUserSessions(userId);
        return ResponseEntity.ok(sessions);
    }
}

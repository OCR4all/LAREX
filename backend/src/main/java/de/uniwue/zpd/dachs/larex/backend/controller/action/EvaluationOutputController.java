package de.uniwue.zpd.dachs.larex.backend.controller.action;

import de.uniwue.zpd.dachs.larex.backend.service.action.ActionOutputService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/workspaces/{workspaceId}/datasets/{datasetId}/outputs")
public class EvaluationOutputController {
    private final ActionOutputService outputService;
    public EvaluationOutputController(ActionOutputService outputService) { this.outputService = outputService; }

    @GetMapping("/{outputId}/files/{fileId}/download")
    public ResponseEntity<Resource> downloadFile(@PathVariable String workspaceId, @PathVariable String datasetId,
            @PathVariable String outputId, @PathVariable String fileId,
            @AuthenticationPrincipal(expression = "subject") String userId) throws IOException {
        ActionOutputService.FileDownload download = outputService.prepareDatasetFileDownload(workspaceId, datasetId, outputId, fileId, userId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(download.mimeType()));
        headers.setContentLength(download.contentLength());
        headers.setContentDisposition(ContentDisposition.attachment().filename(download.fileName(), StandardCharsets.UTF_8).build());
        headers.set("X-Checksum-Sha256", download.checksumSha256());
        return ResponseEntity.ok().headers(headers).body(new FileSystemResource(download.absolutePath()));
    }
}

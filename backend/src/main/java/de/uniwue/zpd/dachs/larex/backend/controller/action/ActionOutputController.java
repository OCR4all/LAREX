package de.uniwue.zpd.dachs.larex.backend.controller.action;

import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionOutputDto;
import de.uniwue.zpd.dachs.larex.backend.service.action.ActionOutputService;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/workspaces/{workspaceId}/projects/{projectId}/outputs")
public class ActionOutputController {
    private final ActionOutputService outputService;

    public ActionOutputController(ActionOutputService outputService) {
        this.outputService = outputService;
    }

    @GetMapping
    public List<ActionOutputDto.OutputResponse> list(@PathVariable String workspaceId,
                                                     @PathVariable String projectId,
                                                     @AuthenticationPrincipal(expression = "subject") String userId) {
        return outputService.listOutputs(workspaceId, projectId, userId);
    }

    @GetMapping("/{outputId}/files/{fileId}/download")
    public ResponseEntity<Resource> downloadFile(@PathVariable String workspaceId,
                                                  @PathVariable String projectId,
                                                  @PathVariable String outputId,
                                                  @PathVariable String fileId,
                                                  @AuthenticationPrincipal(expression = "subject") String userId) throws IOException {
        ActionOutputService.FileDownload download = outputService.prepareFileDownload(
                workspaceId, projectId, outputId, fileId, userId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType(download.mimeType()));
        headers.setContentLength(download.contentLength());
        headers.setContentDisposition(ContentDisposition.attachment().filename(download.fileName()).build());
        headers.set("X-Checksum-Sha256", download.checksumSha256());
        return ResponseEntity.ok().headers(headers).body(new FileSystemResource(download.absolutePath()));
    }

    @GetMapping("/{outputId}/download")
    public ResponseEntity<StreamingResponseBody> downloadBundle(@PathVariable String workspaceId,
                                                                 @PathVariable String projectId,
                                                                 @PathVariable String outputId,
                                                                 @AuthenticationPrincipal(expression = "subject") String userId) {
        ActionOutputService.BundleDownload download = outputService.prepareBundleDownload(
                workspaceId, projectId, outputId, userId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        headers.setContentDisposition(ContentDisposition.attachment().filename(download.fileName()).build());
        return ResponseEntity.ok().headers(headers)
                .body(stream -> outputService.writeBundle(download.outputId(), stream));
    }

    @DeleteMapping("/{outputId}")
    public ResponseEntity<Void> delete(@PathVariable String workspaceId,
                                       @PathVariable String projectId,
                                       @PathVariable String outputId,
                                       @AuthenticationPrincipal(expression = "subject") String userId) {
        outputService.deleteOutput(workspaceId, projectId, outputId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{outputId}/share")
    public ActionOutputDto.ShareResponse createShare(@PathVariable String workspaceId,
                                                      @PathVariable String projectId,
                                                      @PathVariable String outputId,
                                                      @Valid @RequestBody ActionOutputDto.ShareRequest request,
                                                      @AuthenticationPrincipal(expression = "subject") String userId) {
        return outputService.createOrRotateShare(workspaceId, projectId, outputId, request, userId);
    }

    @PatchMapping("/{outputId}/share")
    public ActionOutputDto.OutputResponse updateShare(@PathVariable String workspaceId,
                                                       @PathVariable String projectId,
                                                       @PathVariable String outputId,
                                                       @Valid @RequestBody ActionOutputDto.ShareRequest request,
                                                       @AuthenticationPrincipal(expression = "subject") String userId) {
        return outputService.updateShare(workspaceId, projectId, outputId, request, userId);
    }

    @DeleteMapping("/{outputId}/share")
    public ActionOutputDto.OutputResponse revokeShare(@PathVariable String workspaceId,
                                                       @PathVariable String projectId,
                                                       @PathVariable String outputId,
                                                       @AuthenticationPrincipal(expression = "subject") String userId) {
        return outputService.revokeShare(workspaceId, projectId, outputId, userId);
    }

    private MediaType mediaType(String value) {
        try {
            return MediaType.parseMediaType(value);
        } catch (Exception ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}

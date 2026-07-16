package de.uniwue.zpd.dachs.larex.backend.controller.action;

import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto;
import de.uniwue.zpd.dachs.larex.backend.service.action.ActionPublicBaseUrlService;
import de.uniwue.zpd.dachs.larex.backend.service.action.ActionRunService;
import de.uniwue.zpd.dachs.larex.backend.service.upload.UploadPathService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.MDC;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/public/actions/runs")
public class PublicActionMachineController {

    private final ActionRunService actionRunService;
    private final ActionPublicBaseUrlService publicBaseUrlService;
    private final UploadPathService uploadPathService;

    public PublicActionMachineController(ActionRunService actionRunService,
                                         ActionPublicBaseUrlService publicBaseUrlService,
                                         UploadPathService uploadPathService) {
        this.actionRunService = actionRunService;
        this.publicBaseUrlService = publicBaseUrlService;
        this.uploadPathService = uploadPathService;
    }

    @GetMapping("/{runId}/input")
    public ResponseEntity<ActionDto.MachineInputResponse> getInput(
            @PathVariable String runId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpServletRequest request) {
        return ResponseEntity.ok(actionRunService.buildMachineInput(runId, authorizationHeader,
                publicBaseUrlService.publicApiBaseUrl(request)));
    }

    @GetMapping("/{runId}/files/{type}/{fileId}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable String runId,
            @PathVariable String type,
            @PathVariable String fileId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) throws IOException {
        ActionRunService.MachineFile file = actionRunService.resolveMachineFile(runId, authorizationHeader, type, fileId);
        Path path;
        try {
            path = uploadPathService.resolve(file.storagePath());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!Files.exists(path)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        FileSystemResource resource = new FileSystemResource(path);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(resolveContentType(file.mimeType()));
        headers.setContentLength(resource.contentLength());
        headers.setContentDisposition(ContentDisposition.attachment().filename(file.fileName()).build());
        return ResponseEntity.ok().headers(headers).body(resource);
    }

    @PostMapping("/{runId}/heartbeat")
    public ResponseEntity<ActionDto.HeartbeatResponse> heartbeat(
            @PathVariable String runId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody ActionDto.HeartbeatRequest request) {
        return ResponseEntity.ok(actionRunService.heartbeat(runId, authorizationHeader, request));
    }

    @PostMapping(path = "/{runId}/results", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ActionDto.RunResponse> receiveResults(
            @PathVariable String runId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestPart("manifest") ActionDto.ResultManifest manifest,
            @RequestParam MultiValueMap<String, MultipartFile> files) throws IOException {
        try (MDC.MDCCloseable ignoredRun = MDC.putCloseable("actionRunId", runId);
             MDC.MDCCloseable ignoredPage = MDC.putCloseable(
                     "actionPageId", manifest.pageId() == null ? "" : manifest.pageId())) {
            return ResponseEntity.ok(actionRunService.receiveResults(runId, authorizationHeader, manifest, files));
        }
    }

    private MediaType resolveContentType(String mimeType) {
        try {
            return mimeType == null || mimeType.isBlank()
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(mimeType);
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

}

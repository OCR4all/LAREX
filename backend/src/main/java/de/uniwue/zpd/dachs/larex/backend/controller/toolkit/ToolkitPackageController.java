package de.uniwue.zpd.dachs.larex.backend.controller.toolkit;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.uniwue.zpd.dachs.larex.backend.dto.ToolkitPackageDto;
import de.uniwue.zpd.dachs.larex.backend.service.toolkit.ToolkitPackageService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/workspaces/{workspaceId}/toolkit")
public class ToolkitPackageController {

    private final ToolkitPackageService toolkitPackageService;
    private final ObjectMapper objectMapper;

    public ToolkitPackageController(ToolkitPackageService toolkitPackageService,
                                    ObjectMapper objectMapper) {
        this.toolkitPackageService = toolkitPackageService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/export")
    public ResponseEntity<byte[]> exportToolkit(
            @PathVariable String workspaceId,
            @RequestBody(required = false) ToolkitPackageDto.ExportRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) throws IOException {

        byte[] jsonBytes = toolkitPackageService.exportToolkitPackage(workspaceId, userId, request);
        ToolkitPackageDto.ToolkitPackage toolkitPackage =
                objectMapper.readValue(jsonBytes, ToolkitPackageDto.ToolkitPackage.class);
        String filename = buildExportFileName(workspaceId, toolkitPackage);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());

        return ResponseEntity.ok()
                .headers(headers)
                .body(jsonBytes);
    }

    @PostMapping(value = "/import", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ToolkitPackageDto.ImportResult> importToolkitJson(
            @PathVariable String workspaceId,
            @RequestBody ToolkitPackageDto.ImportRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) throws IOException {

        ToolkitPackageDto.ImportResult result = toolkitPackageService.importToolkitPackageFromContent(
                workspaceId,
                userId,
                request.content()
        );
        return ResponseEntity.ok(result);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ToolkitPackageDto.ImportResult> importToolkitFile(
            @PathVariable String workspaceId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal(expression = "subject") String userId) throws IOException {

        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        ToolkitPackageDto.ImportResult result = toolkitPackageService.importToolkitPackageFromContent(
                workspaceId,
                userId,
                content
        );
        return ResponseEntity.ok(result);
    }

    private String sanitizeFileName(String value, String fallback) {
        if (value == null) {
            return fallback;
        }

        String sanitized = value.trim()
                .replaceAll("[\\\\/:*?\"<>|]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return sanitized.isBlank() ? fallback : sanitized;
    }

    private String buildExportFileName(String workspaceId, ToolkitPackageDto.ToolkitPackage toolkitPackage) {
        if (toolkitPackage != null && toolkitPackage.resources() != null && toolkitPackage.resources().size() == 1) {
            ToolkitPackageDto.ToolkitResource resource = toolkitPackage.resources().getFirst();
            String resourceName = sanitizeFileName(resource.name(), resource.type() == null ? "toolkit" : resource.type().name().toLowerCase());
            return resourceName + ".larex-toolkit.json";
        }

        String workspaceName = toolkitPackage != null && toolkitPackage.meta() != null
                ? toolkitPackage.meta().workspaceName()
                : null;
        return sanitizeFileName(workspaceName != null ? workspaceName : workspaceId, "workspace") + "-toolkit.larex-toolkit.json";
    }
}

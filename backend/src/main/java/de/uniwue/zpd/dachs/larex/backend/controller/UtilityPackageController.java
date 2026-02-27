package de.uniwue.zpd.dachs.larex.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.uniwue.zpd.dachs.larex.backend.dto.UtilityPackageDto;
import de.uniwue.zpd.dachs.larex.backend.service.UtilityPackageService;
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
@RequestMapping("/workspaces/{workspaceId}/utilities")
public class UtilityPackageController {

    private final UtilityPackageService utilityPackageService;
    private final ObjectMapper objectMapper;

    public UtilityPackageController(UtilityPackageService utilityPackageService,
                                    ObjectMapper objectMapper) {
        this.utilityPackageService = utilityPackageService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/export")
    public ResponseEntity<byte[]> exportUtilities(
            @PathVariable String workspaceId,
            @RequestBody(required = false) UtilityPackageDto.ExportRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) throws IOException {

        byte[] jsonBytes = utilityPackageService.exportUtilityPackage(workspaceId, userId, request);
        UtilityPackageDto.UtilityPackage utilityPackage =
                objectMapper.readValue(jsonBytes, UtilityPackageDto.UtilityPackage.class);
        String filename = buildExportFileName(workspaceId, utilityPackage);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());

        return ResponseEntity.ok()
                .headers(headers)
                .body(jsonBytes);
    }

    @PostMapping(value = "/import", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UtilityPackageDto.ImportResult> importUtilitiesJson(
            @PathVariable String workspaceId,
            @RequestBody UtilityPackageDto.ImportRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) throws IOException {

        UtilityPackageDto.ImportResult result = utilityPackageService.importUtilityPackageFromContent(
                workspaceId,
                userId,
                request.content()
        );
        return ResponseEntity.ok(result);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UtilityPackageDto.ImportResult> importUtilitiesFile(
            @PathVariable String workspaceId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal(expression = "subject") String userId) throws IOException {

        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        UtilityPackageDto.ImportResult result = utilityPackageService.importUtilityPackageFromContent(
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

    private String buildExportFileName(String workspaceId, UtilityPackageDto.UtilityPackage utilityPackage) {
        if (utilityPackage != null && utilityPackage.resources() != null && utilityPackage.resources().size() == 1) {
            UtilityPackageDto.UtilityResource resource = utilityPackage.resources().getFirst();
            String resourceName = sanitizeFileName(resource.name(), resource.type() == null ? "utility" : resource.type().name().toLowerCase());
            return resourceName + ".larex-utilities.json";
        }

        String workspaceName = utilityPackage != null && utilityPackage.meta() != null
                ? utilityPackage.meta().workspaceName()
                : null;
        return sanitizeFileName(workspaceName != null ? workspaceName : workspaceId, "workspace") + "-utilities.larex-utilities.json";
    }
}

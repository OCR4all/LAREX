package de.uniwue.zpd.dachs.larex.backend.controller.machine;

import de.uniwue.zpd.dachs.larex.backend.dto.PageXmlTextDto;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.service.machine.PatXmlAccessService;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaGuardService;
import de.uniwue.zpd.dachs.larex.backend.service.user.PrivateAccessTokenService;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlRawEditService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@RestController
@RequestMapping("/public/pat/projects")
public class PublicPatPageXmlController {

    private final PrivateAccessTokenService privateAccessTokenService;
    private final PatXmlAccessService patXmlAccessService;
    private final PageXmlRawEditService pageXmlRawEditService;
    private final PageService pageService;
    private final WorkspaceQuotaGuardService workspaceQuotaGuardService;

    @Value("${file.upload-dir}")
    private String uploadDir;

    public PublicPatPageXmlController(PrivateAccessTokenService privateAccessTokenService,
                                      PatXmlAccessService patXmlAccessService,
                                      PageXmlRawEditService pageXmlRawEditService,
                                      PageService pageService,
                                      WorkspaceQuotaGuardService workspaceQuotaGuardService) {
        this.privateAccessTokenService = privateAccessTokenService;
        this.patXmlAccessService = patXmlAccessService;
        this.pageXmlRawEditService = pageXmlRawEditService;
        this.pageService = pageService;
        this.workspaceQuotaGuardService = workspaceQuotaGuardService;
    }

    @GetMapping("/{projectId}/pages/{pageId}/xml/{xmlId}/text")
    public ResponseEntity<?> getXmlText(
            @PathVariable String projectId,
            @PathVariable String pageId,
            @PathVariable String xmlId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        Optional<PrivateAccessTokenService.PrivateAccessTokenAuthContext> authContextOpt =
                privateAccessTokenService.authenticateBearerToken(authorizationHeader);
        if (authContextOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        PrivateAccessTokenService.PrivateAccessTokenAuthContext authContext = authContextOpt.get();
        if (!authContext.hasScope(PrivateAccessTokenService.SCOPE_XML_READ)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (!resourceBelongsToTokenWorkspace(projectId, pageId, xmlId, authContext.workspaceId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        try {
            PageXmlTextDto.XmlTextResponse response =
                    pageXmlRawEditService.getXmlText(projectId, pageId, xmlId, authContext.ownerUserId());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (UnsupportedOperationException e) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{projectId}/pages/{pageId}/xml/{xmlId}/text")
    public ResponseEntity<?> saveXmlText(
            @PathVariable String projectId,
            @PathVariable String pageId,
            @PathVariable String xmlId,
            @RequestBody PageXmlTextDto.SaveRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        Optional<PrivateAccessTokenService.PrivateAccessTokenAuthContext> authContextOpt =
                privateAccessTokenService.authenticateBearerToken(authorizationHeader);
        if (authContextOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        PrivateAccessTokenService.PrivateAccessTokenAuthContext authContext = authContextOpt.get();
        if (!authContext.hasScope(PrivateAccessTokenService.SCOPE_XML_WRITE)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (!resourceBelongsToTokenWorkspace(projectId, pageId, xmlId, authContext.workspaceId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        try {
            PageXmlTextDto.XmlValidationResult validation = pageXmlRawEditService.saveXmlText(
                    projectId,
                    pageId,
                    xmlId,
                    request.xml(),
                    request.comment(),
                    authContext.ownerUserId()
            );
            if (!validation.valid()) {
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(validation);
            }
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (UnsupportedOperationException e) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{projectId}/pages/{pageId}/xml")
    public ResponseEntity<Void> uploadXmlFile(
            @PathVariable String projectId,
            @PathVariable String pageId,
            @RequestParam("file") MultipartFile xmlFile,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        Optional<PrivateAccessTokenService.PrivateAccessTokenAuthContext> authContextOpt =
                privateAccessTokenService.authenticateBearerToken(authorizationHeader);
        if (authContextOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        PrivateAccessTokenService.PrivateAccessTokenAuthContext authContext = authContextOpt.get();
        if (!authContext.hasScope(PrivateAccessTokenService.SCOPE_XML_WRITE)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (!patXmlAccessService.projectBelongsToWorkspace(projectId, authContext.workspaceId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        if (!patXmlAccessService.pageBelongsToProject(pageId, projectId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        long reservedBytes = 0L;
        try {
            reservedBytes = workspaceQuotaGuardService.reserveBytesOrThrow(
                    authContext.workspaceId(),
                    xmlFile == null ? 0L : xmlFile.getSize(),
                    "pat-page-xml-upload"
            );

            boolean uploaded = pageService.uploadXmlFile(pageId, xmlFile, authContext.ownerUserId());
            return uploaded ? ResponseEntity.ok().build() : ResponseEntity.badRequest().build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } finally {
            workspaceQuotaGuardService.syncUsageAndReleaseReservation(authContext.workspaceId(), reservedBytes);
        }
    }

    @GetMapping("/{projectId}/pages/xml/{xmlId}/export")
    public ResponseEntity<Resource> exportXml(
            @PathVariable String projectId,
            @PathVariable String xmlId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        Optional<PrivateAccessTokenService.PrivateAccessTokenAuthContext> authContextOpt =
                privateAccessTokenService.authenticateBearerToken(authorizationHeader);
        if (authContextOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        PrivateAccessTokenService.PrivateAccessTokenAuthContext authContext = authContextOpt.get();
        if (!authContext.hasScope(PrivateAccessTokenService.SCOPE_XML_READ)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (!patXmlAccessService.xmlBelongsToProjectAndWorkspace(xmlId, projectId, authContext.workspaceId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        try {
            PageXml xml = pageService.getXmlById(xmlId, authContext.ownerUserId());
            if (xml == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            Path filePath = Paths.get(uploadDir).resolve(xml.getFilePath());
            if (!Files.exists(filePath)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            FileSystemResource resource = new FileSystemResource(filePath);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(resolveXmlContentType(xml));
            headers.setContentLength(resource.contentLength());
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename(sanitizeFileName(xml.getFileName(), "annotation.xml"))
                    .build());

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{projectId}/pages/images/{imageId}/export")
    public ResponseEntity<Resource> exportImage(
            @PathVariable String projectId,
            @PathVariable String imageId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        Optional<PrivateAccessTokenService.PrivateAccessTokenAuthContext> authContextOpt =
                privateAccessTokenService.authenticateBearerToken(authorizationHeader);
        if (authContextOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        PrivateAccessTokenService.PrivateAccessTokenAuthContext authContext = authContextOpt.get();
        if (!authContext.hasScope(PrivateAccessTokenService.SCOPE_XML_READ)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (!patXmlAccessService.imageBelongsToProjectAndWorkspace(imageId, projectId, authContext.workspaceId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        try {
            PageImage image = pageService.getImageById(imageId, authContext.ownerUserId());
            if (image == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            Path filePath = Paths.get(uploadDir).resolve(image.getFilePath());
            if (!Files.exists(filePath)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            FileSystemResource resource = new FileSystemResource(filePath);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(resolveImageContentType(image));
            headers.setContentLength(resource.contentLength());
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename(sanitizeFileName(image.getFileName(), "image"))
                    .build());

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private boolean resourceBelongsToTokenWorkspace(String projectId, String pageId, String xmlId, String workspaceId) {
        return patXmlAccessService.xmlBelongsToPageInWorkspace(xmlId, pageId, projectId, workspaceId);
    }

    private MediaType resolveXmlContentType(PageXml xml) {
        if (xml.getMimeType() == null || xml.getMimeType().isBlank()) {
            return MediaType.APPLICATION_XML;
        }
        try {
            return MediaType.parseMediaType(xml.getMimeType());
        } catch (Exception ignored) {
            return MediaType.APPLICATION_XML;
        }
    }

    private MediaType resolveImageContentType(PageImage image) {
        if (image.getMimeType() == null || image.getMimeType().isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(image.getMimeType());
        } catch (Exception ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private String sanitizeFileName(String original, String fallback) {
        String candidate = original == null ? "" : original.trim();
        if (candidate.isEmpty()) {
            candidate = fallback;
        }

        candidate = candidate.replaceAll("[\\\\/:*?\"<>|]", "_");
        return candidate.isEmpty() ? fallback : candidate;
    }
}

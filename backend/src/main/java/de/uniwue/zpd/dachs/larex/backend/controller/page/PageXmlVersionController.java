package de.uniwue.zpd.dachs.larex.backend.controller.page;

import de.uniwue.zpd.dachs.larex.backend.dto.PageXmlVersionDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.application.AnnotationProcessingService;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.collaboration.AnnotationLeaseService;
import de.uniwue.zpd.dachs.larex.backend.service.version.PageXmlVersionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/projects/{projectId}/pages/{pageId}/annotations/{xmlId}/versions")
public class PageXmlVersionController {

    private static final Logger log = LoggerFactory.getLogger(PageXmlVersionController.class);

    private final PageXmlVersionService versionService;
    private final AnnotationLeaseService annotationLeaseService;
    private final AnnotationProcessingService annotationProcessingService;

    public PageXmlVersionController(PageXmlVersionService versionService,
                                    AnnotationLeaseService annotationLeaseService,
                                    AnnotationProcessingService annotationProcessingService) {
        this.versionService = versionService;
        this.annotationLeaseService = annotationLeaseService;
        this.annotationProcessingService = annotationProcessingService;
    }

    @GetMapping
    public ResponseEntity<List<PageXmlVersionDto>> listVersions(
            @PathVariable String projectId,
            @PathVariable String pageId,
            @PathVariable String xmlId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        try {
            annotationLeaseService.resolveRoomAccess(projectId, pageId, xmlId, userId);
            List<PageXmlVersionDto> versions = versionService.listVersions(xmlId);
            return ResponseEntity.ok(versions);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{versionId}")
    public ResponseEntity<String> getVersionContent(
            @PathVariable String projectId,
            @PathVariable String pageId,
            @PathVariable String xmlId,
            @PathVariable String versionId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        try {
            annotationLeaseService.resolveRoomAccess(projectId, pageId, xmlId, userId);
            String content = versionService.getVersionContent(versionId, xmlId);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_XML)
                    .body(content);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            log.error("Failed to read version content {}: {}", versionId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{versionId}/annotation")
    public ResponseEntity<PageDto> getVersionAnnotation(
            @PathVariable String projectId,
            @PathVariable String pageId,
            @PathVariable String xmlId,
            @PathVariable String versionId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        try {
            annotationLeaseService.resolveRoomAccess(projectId, pageId, xmlId, userId);
            PageDto pageDto = annotationProcessingService.parseXmlVersionToAnnotation(xmlId, versionId);
            return ResponseEntity.ok(pageDto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (UnsupportedOperationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (IOException e) {
            log.error("Failed to parse version annotation {}: {}", versionId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{versionId}/restore")
    public ResponseEntity<Void> restoreVersion(
            @PathVariable String projectId,
            @PathVariable String pageId,
            @PathVariable String xmlId,
            @PathVariable String versionId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        try {
            annotationLeaseService.assertWriteAccess(projectId, pageId, xmlId, userId);
            versionService.restoreVersion(versionId, xmlId, userId);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            log.error("Failed to restore version {}: {}", versionId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

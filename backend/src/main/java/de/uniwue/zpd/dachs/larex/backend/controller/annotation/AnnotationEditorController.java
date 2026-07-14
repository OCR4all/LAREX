package de.uniwue.zpd.dachs.larex.backend.controller.annotation;

import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.exception.AnnotationAlreadyExistsException;
import de.uniwue.zpd.dachs.larex.backend.exception.AnnotationLeaseLockedException;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.application.AnnotationProcessingService;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.collaboration.AnnotationLeaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

/**
 * Controller for the annotation editor functionality.
 * Handles loading, editing, and saving of annotation documents.
 * Uses page4j-aligned PageDto for all operations.
 */
@RestController
@RequestMapping("/projects/{projectId}/pages/{pageId}/annotations")
public class AnnotationEditorController {

    private static final Logger log = LoggerFactory.getLogger(AnnotationEditorController.class);

    private final AnnotationProcessingService annotationProcessingService;
    private final AnnotationLeaseService annotationLeaseService;

    public AnnotationEditorController(AnnotationProcessingService annotationProcessingService,
                                      AnnotationLeaseService annotationLeaseService) {
        this.annotationProcessingService = annotationProcessingService;
        this.annotationLeaseService = annotationLeaseService;
    }

    /**
     * Create the first PAGE XML annotation file for a page that currently has none.
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> createAnnotation(
            @PathVariable String projectId,
            @PathVariable String pageId,
            @RequestBody PageDto pageDto,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        try {
            log.info("Creating initial annotations for page {}", pageId);
            annotationLeaseService.assertPageWriteAccess(projectId, pageId, userId);
            PageXml xml = annotationProcessingService.createInitialAnnotationXml(projectId, pageId, pageDto, userId);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "xmlId", xml.getId(),
                    "fileName", xml.getFileName(),
                    "schema", xml.getSchema().name(),
                    "schemaVersion", xml.getSchemaVersion() == null ? "" : xml.getSchemaVersion()
            ));
        } catch (AnnotationLeaseLockedException e) {
            throw e;
        } catch (AnnotationAlreadyExistsException e) {
            log.warn("Initial annotation already exists for page {} as XML {}", pageId, e.getXmlId());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", e.getMessage(),
                    "xmlId", e.getXmlId()
            ));
        } catch (IllegalArgumentException e) {
            log.warn("Not found when creating annotations: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            log.error("IO error creating annotations for page {}: {}", pageId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("Unexpected error creating annotations for page {}: {}", pageId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Load annotation document from XML file for editing.
     * Returns a PageDto containing all parsed PAGE XML data.
     */
    @GetMapping("/{xmlId}")
    public ResponseEntity<PageDto> loadAnnotation(
            @PathVariable String projectId,
            @PathVariable String pageId,
            @PathVariable String xmlId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        try {
            annotationLeaseService.resolveRoomAccess(projectId, pageId, xmlId, userId);
            PageDto pageDto = annotationProcessingService.parseXmlToAnnotation(xmlId);
            return ResponseEntity.ok(pageDto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (UnsupportedOperationException e) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Load merged annotation document from all XML files for a page.
     */
    @GetMapping("/merged")
    public ResponseEntity<PageDto> loadMergedAnnotations(
            @PathVariable String projectId,
            @PathVariable String pageId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        try {
            annotationLeaseService.resolvePageAccess(projectId, pageId, userId);
            PageDto pageDto = annotationProcessingService.parseMultipleXmlToAnnotation(pageId);
            return ResponseEntity.ok(pageDto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Save annotation document back to original XML file.
     */
    @PutMapping("/{xmlId}")
    public ResponseEntity<Void> saveAnnotation(
            @PathVariable String projectId,
            @PathVariable String pageId,
            @PathVariable String xmlId,
            @RequestBody PageDto pageDto,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        try {
            log.info("Saving annotations for page {} with xmlId {}", pageId, xmlId);
            annotationLeaseService.assertWriteAccess(projectId, pageId, xmlId, userId);
            annotationProcessingService.saveAnnotationToXml(xmlId, pageDto, userId);
            log.info("Successfully saved annotations for page {}", pageId);
            return ResponseEntity.ok().build();
        } catch (AnnotationLeaseLockedException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            log.warn("Not found when saving annotations: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (UnsupportedOperationException e) {
            log.warn("Unsupported operation when saving annotations: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        } catch (IOException e) {
            log.error("IO error saving annotations for page {}: {}", pageId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("Unexpected error saving annotations for page {}: {}", pageId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Export annotation document to different XML format.
     */
    @PostMapping("/{xmlId}/export")
    public ResponseEntity<String> exportAnnotation(
            @PathVariable String projectId,
            @PathVariable String pageId,
            @PathVariable String xmlId,
            @RequestParam XmlSchema targetSchema,
            @RequestBody PageDto pageDto,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        try {
            annotationLeaseService.resolveRoomAccess(projectId, pageId, xmlId, userId);
            String xmlContent = annotationProcessingService.exportAnnotationToXml(
                pageDto, targetSchema, xmlId);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_XML)
                    .header("Content-Disposition",
                           "attachment; filename=\"exported." + targetSchema.name().toLowerCase() + ".xml\"")
                    .body(xmlContent);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (UnsupportedOperationException e) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get available export schemas.
     */
    @GetMapping("/export-schemas")
    public ResponseEntity<Map<XmlSchema, String>> getAvailableExportSchemas(
            @PathVariable String projectId,
            @PathVariable String pageId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        Map<XmlSchema, String> schemas = annotationProcessingService.getAvailableExportSchemas();
        return ResponseEntity.ok(schemas);
    }

    /**
     * Check if a schema is supported for parsing.
     */
    @GetMapping("/schema/{schema}/supported")
    public ResponseEntity<Map<String, Boolean>> checkSchemaSupport(
            @PathVariable String projectId,
            @PathVariable String pageId,
            @PathVariable XmlSchema schema,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        boolean parseSupported = annotationProcessingService.isSchemaSupported(schema);
        boolean exportSupported = annotationProcessingService.isExportSupported(schema);

        return ResponseEntity.ok(Map.of(
            "parseSupported", parseSupported,
            "exportSupported", exportSupported
        ));
    }
}

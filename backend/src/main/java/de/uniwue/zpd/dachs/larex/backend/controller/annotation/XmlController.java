package de.uniwue.zpd.dachs.larex.backend.controller.annotation;

import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageService;
import de.uniwue.zpd.dachs.larex.backend.service.upload.UploadPathService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/xml")
public class XmlController {

    private final PageService pageService;
    private final UploadPathService uploadPathService;

    public XmlController(PageService pageService, UploadPathService uploadPathService) {
        this.pageService = pageService;
        this.uploadPathService = uploadPathService;
    }

    @GetMapping("/{xmlId}/blob")
    public ResponseEntity<Resource> getXmlBlob(
            @PathVariable String xmlId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        try {
            PageXml xml = pageService.getXmlById(xmlId, userId);
            if (xml == null) {
                return ResponseEntity.notFound().build();
            }

            Path filePath = uploadPathService.resolve(xml.getFilePath());
            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }

            FileSystemResource resource = new FileSystemResource(filePath);
            MediaType contentType = MediaType.APPLICATION_XML;
            if (xml.getMimeType() != null && !xml.getMimeType().isBlank()) {
                contentType = MediaType.parseMediaType(xml.getMimeType());
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(contentType);
            headers.setContentLength(resource.contentLength());
            headers.setContentDisposition(ContentDisposition.inline()
                    .filename(sanitizeFileName(xml.getFileName(), "document.xml"))
                    .build());

            return ResponseEntity.ok().headers(headers).body(resource);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
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
}

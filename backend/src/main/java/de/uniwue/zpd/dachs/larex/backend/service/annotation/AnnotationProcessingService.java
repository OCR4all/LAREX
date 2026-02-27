package de.uniwue.zpd.dachs.larex.backend.service.annotation;

import de.uniwue.zpd.dachs.larex.backend.dto.page.PageDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.repository.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.service.PageFilterIndexService;
import de.uniwue.zpd.dachs.larex.backend.service.PageXmlVersionService;
import de.uniwue.zpd.dachs.larex.backend.service.UserService;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.parser.PageXmlToAnnotationParser;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.parser.AltoXmlToAnnotationParser;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.exporter.AnnotationToPageXmlExporter;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.exporter.AnnotationToAltoXmlExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Main service for processing XML annotations.
 * Handles conversion between XML formats and PageDto format using page4j.
 */
@Service
public class AnnotationProcessingService {

    private static final Logger log = LoggerFactory.getLogger(AnnotationProcessingService.class);
    private static final DateTimeFormatter PAGE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final PageXmlRepository pageXmlRepository;
    private final PageXmlToAnnotationParser pageXmlParser;
    private final AltoXmlToAnnotationParser altoXmlParser;
    private final AnnotationToPageXmlExporter pageXmlExporter;
    private final AnnotationToAltoXmlExporter altoXmlExporter;
    private final PageFilterIndexService pageFilterIndexService;
    private final PageXmlVersionService pageXmlVersionService;
    private final UserService userService;

    @Value("${file.upload-dir}")
    private String uploadDir;

    public AnnotationProcessingService(
            PageXmlRepository pageXmlRepository,
            PageXmlToAnnotationParser pageXmlParser,
            AltoXmlToAnnotationParser altoXmlParser,
            AnnotationToPageXmlExporter pageXmlExporter,
            AnnotationToAltoXmlExporter altoXmlExporter,
            @Lazy PageFilterIndexService pageFilterIndexService,
            PageXmlVersionService pageXmlVersionService,
            UserService userService) {

        this.pageXmlRepository = pageXmlRepository;
        this.pageXmlParser = pageXmlParser;
        this.altoXmlParser = altoXmlParser;
        this.pageXmlExporter = pageXmlExporter;
        this.altoXmlExporter = altoXmlExporter;
        this.pageFilterIndexService = pageFilterIndexService;
        this.pageXmlVersionService = pageXmlVersionService;
        this.userService = userService;
    }

    /**
     * Parse XML file to PageDto format.
     */
    public PageDto parseXmlToAnnotation(String xmlId) throws IOException {
        Optional<PageXml> xmlOpt = pageXmlRepository.findById(xmlId);
        if (xmlOpt.isEmpty()) {
            throw new IllegalArgumentException("XML file not found: " + xmlId);
        }

        PageXml xml = xmlOpt.get();
        Path xmlPath = Paths.get(uploadDir, xml.getFilePath());

        if (!Files.exists(xmlPath)) {
            throw new IOException("XML file not found on disk: " + xmlPath);
        }

        if (xml.getSchema() == XmlSchema.PAGE_XML) {
            return pageXmlParser.parse(xmlPath, xml);
        } else if (xml.getSchema() == XmlSchema.ALTO_XML) {
            // TODO(larex): Migrate ALTO parser to PageDto via page4j ALTO support.
            throw new UnsupportedOperationException("ALTO XML parsing not yet migrated to PageDto");
        } else {
            throw new UnsupportedOperationException("No parser available for schema: " + xml.getSchema());
        }
    }

    /**
     * Parse multiple XML files and merge them into a single PageDto.
     * This is useful when a page has multiple XML variants (e.g., PAGE + ALTO).
     */
    public PageDto parseMultipleXmlToAnnotation(String pageId) throws IOException {
        List<PageXml> xmlFiles = pageXmlRepository.findByPage_Id(pageId);

        if (xmlFiles.isEmpty()) {
            throw new IllegalArgumentException("No XML files found for page: " + pageId);
        }

        // For now, just use the first XML file
        // In the future, we could implement merging logic
        PageXml primaryXml = xmlFiles.get(0);
        return parseXmlToAnnotation(primaryXml.getId());
    }

    /**
     * Export PageDto to XML file.
     */
    public String exportAnnotationToXml(PageDto pageDto, XmlSchema targetSchema,
                                       String originalXmlId) throws IOException {

        // Get original XML for data preservation
        PageXml originalXml = null;
        if (originalXmlId != null) {
            Optional<PageXml> xmlOpt = pageXmlRepository.findById(originalXmlId);
            if (xmlOpt.isPresent()) {
                originalXml = xmlOpt.get();
            }
        }

        if (targetSchema == XmlSchema.PAGE_XML) {
            return pageXmlExporter.export(pageDto, originalXml);
        } else if (targetSchema == XmlSchema.ALTO_XML) {
            // TODO(larex): Migrate ALTO exporter to PageDto via page4j ALTO support.
            throw new UnsupportedOperationException("ALTO XML export not yet migrated to PageDto");
        } else {
            throw new UnsupportedOperationException("No exporter available for schema: " + targetSchema);
        }
    }

    /**
     * Save PageDto back to original XML file, preserving unsupported data.
     * Creates a version snapshot before overwriting, and updates the search index after saving.
     */
    public void saveAnnotationToXml(String xmlId, PageDto pageDto, String userId) throws IOException {
        Optional<PageXml> xmlOpt = pageXmlRepository.findById(xmlId);
        if (xmlOpt.isEmpty()) {
            throw new IllegalArgumentException("XML file not found: " + xmlId);
        }

        PageXml xml = xmlOpt.get();
        Path xmlPath = Paths.get(uploadDir, xml.getFilePath());
        XmlSchema schema = xml.getSchema();

        // Create a version snapshot of the current file before overwriting
        try {
            pageXmlVersionService.createVersion(xmlId, userId, "Manual save");
        } catch (Exception e) {
            log.warn("Failed to create version before save for XML {}: {}", xmlId, e.getMessage());
        }

        PageDto saveReadyPageDto = enrichMetadataForSave(pageDto, userId);
        String xmlContent = exportAnnotationToXml(saveReadyPageDto, schema, xmlId);
        Files.writeString(xmlPath, xmlContent);

        // Update search index after save
        Page page = xml.getPage();
        if (page != null && pageFilterIndexService != null) {
            try {
                pageFilterIndexService.indexPage(page, pageDto);
                log.debug("Updated search index for page {}", page.getId());
            } catch (Exception e) {
                log.warn("Failed to update search index for page {}: {}", page.getId(), e.getMessage());
            }
        }
    }

    /**
     * Get available schemas for export.
     */
    public Map<XmlSchema, String> getAvailableExportSchemas() {
        Map<XmlSchema, String> schemas = new HashMap<>();
        schemas.put(XmlSchema.PAGE_XML, XmlSchema.PAGE_XML.getDisplayName());
        // TODO(larex): Enable ALTO schema once ALTO parse/export migration is complete.
        // schemas.put(XmlSchema.ALTO_XML, XmlSchema.ALTO_XML.getDisplayName());
        return schemas;
    }

    /**
     * Check if a schema is supported for parsing.
     */
    public boolean isSchemaSupported(XmlSchema schema) {
        // TODO(larex): Add ALTO_XML once ALTO parse support is migrated.
        return schema == XmlSchema.PAGE_XML;
    }

    /**
     * Check if a schema is supported for export.
     */
    public boolean isExportSupported(XmlSchema schema) {
        // TODO(larex): Add ALTO_XML once ALTO export support is migrated.
        return schema == XmlSchema.PAGE_XML;
    }

    private PageDto enrichMetadataForSave(PageDto dto, String userId) {
        if (dto == null) {
            return null;
        }

        String now = LocalDateTime.now().format(PAGE_TIME_FORMAT);
        String fallbackCreator = resolveFallbackCreator(userId);

        var metadata = dto.metadata();
        String creator = normalize(metadata != null ? metadata.creator() : null);
        String created = normalize(metadata != null ? metadata.created() : null);
        String comments = normalize(metadata != null ? metadata.comments() : null);
        String externalRef = normalize(metadata != null ? metadata.externalRef() : null);

        var saveMetadata = new de.uniwue.zpd.dachs.larex.backend.dto.page.MetadataDto(
            creator != null ? creator : fallbackCreator,
            created != null ? created : now,
            now,
            comments,
            externalRef
        );

        return new PageDto(
            dto.imageFilename(),
            dto.imageWidth(),
            dto.imageHeight(),
            dto.imageXResolution(),
            dto.imageYResolution(),
            dto.imageResolutionUnit(),
            saveMetadata,
            dto.pcGtsId(),
            dto.type(),
            dto.custom(),
            dto.orientation(),
            dto.primaryLanguage(),
            dto.secondaryLanguage(),
            dto.primaryScript(),
            dto.secondaryScript(),
            dto.readingDirection(),
            dto.textLineOrder(),
            dto.confidence(),
            dto.border(),
            dto.printSpace(),
            dto.regions(),
            dto.readingOrder(),
            dto.formatVersion(),
            dto.labelIds()
        );
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String resolveFallbackCreator(String userId) {
        String normalizedUserId = normalize(userId);
        if (normalizedUserId == null) {
            return "unknown";
        }

        String username = userService.getUserById(normalizedUserId)
            .map(user -> normalize(user.username()))
            .orElse(null);

        return username != null ? username : normalizedUserId;
    }
}

package de.uniwue.zpd.dachs.larex.backend.service.annotation.application;

import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.StoredFile.StoredFileType;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.service.version.PageXmlVersionService;
import de.uniwue.zpd.dachs.larex.backend.service.user.UserService;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.cache.AnnotationReadCache;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.events.AnnotationSavedEvent;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.exporter.AnnotationToPageXmlExporter;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.exporter.AnnotationToAltoXmlExporter;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.exporter.PageXmlWriteResult;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.parser.AltoXmlToAnnotationParser;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.parser.PageXmlToAnnotationParser;
import de.uniwue.zpd.dachs.larex.backend.service.storage.HierarchicalFileStorageService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaRefreshService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    private final PageRepository pageRepository;
    private final PageXmlRepository pageXmlRepository;
    private final PageXmlToAnnotationParser pageXmlParser;
    private final AltoXmlToAnnotationParser altoXmlParser;
    private final AnnotationToPageXmlExporter pageXmlExporter;
    private final AnnotationToAltoXmlExporter altoXmlExporter;
    private final PageXmlVersionService pageXmlVersionService;
    private final HierarchicalFileStorageService hierarchicalFileStorageService;
    private final UserService userService;
    private final AnnotationReadCache annotationReadCache;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final WorkspaceQuotaRefreshService workspaceQuotaRefreshService;

    @Value("${file.upload-dir}")
    private String uploadDir;

    public AnnotationProcessingService(
            PageRepository pageRepository,
            PageXmlRepository pageXmlRepository,
            PageXmlToAnnotationParser pageXmlParser,
            AltoXmlToAnnotationParser altoXmlParser,
            AnnotationToPageXmlExporter pageXmlExporter,
            AnnotationToAltoXmlExporter altoXmlExporter,
            PageXmlVersionService pageXmlVersionService,
            HierarchicalFileStorageService hierarchicalFileStorageService,
            UserService userService,
            AnnotationReadCache annotationReadCache,
            ApplicationEventPublisher applicationEventPublisher,
            WorkspaceQuotaRefreshService workspaceQuotaRefreshService) {

        this.pageRepository = pageRepository;
        this.pageXmlRepository = pageXmlRepository;
        this.pageXmlParser = pageXmlParser;
        this.altoXmlParser = altoXmlParser;
        this.pageXmlExporter = pageXmlExporter;
        this.altoXmlExporter = altoXmlExporter;
        this.pageXmlVersionService = pageXmlVersionService;
        this.hierarchicalFileStorageService = hierarchicalFileStorageService;
        this.userService = userService;
        this.annotationReadCache = annotationReadCache;
        this.applicationEventPublisher = applicationEventPublisher;
        this.workspaceQuotaRefreshService = workspaceQuotaRefreshService;
    }

    /**
     * Parse XML file to PageDto format.
     */
    public PageDto parseXmlToAnnotation(String xmlId) throws IOException {
        long lookupStartedAt = System.nanoTime();
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
            PageDto cached = annotationReadCache.getIfFresh(xmlId, xmlPath);
            if (cached != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Loaded PAGE XML {} from annotation read cache in {} ms",
                            xmlId, (System.nanoTime() - lookupStartedAt) / 1_000_000);
                }
                return cached;
            }

            PageDto parsed = pageXmlParser.parse(xmlPath, xml);
            annotationReadCache.put(xmlId, xmlPath, parsed);
            if (log.isDebugEnabled()) {
                log.debug("Loaded PAGE XML {} from disk in {} ms",
                        xmlId, (System.nanoTime() - lookupStartedAt) / 1_000_000);
            }
            return parsed;
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
            return altoXmlExporter.toXmlString(pageDto);
        } else {
            throw new UnsupportedOperationException("No exporter available for schema: " + targetSchema);
        }
    }

    /**
     * Save PageDto back to original XML file, preserving unsupported data.
     * Creates a version snapshot before overwriting, and updates the search index after saving.
     */
    @Transactional
    public void saveAnnotationToXml(String xmlId, PageDto pageDto, String userId) throws IOException {
        long saveStartedAt = System.nanoTime();
        Optional<PageXml> xmlOpt = pageXmlRepository.findById(xmlId);
        if (xmlOpt.isEmpty()) {
            throw new IllegalArgumentException("XML file not found: " + xmlId);
        }

        PageXml xml = xmlOpt.get();
        Path xmlPath = Paths.get(uploadDir, xml.getFilePath());
        XmlSchema schema = xml.getSchema();

        // Create a version snapshot of the current file before overwriting
        long versionStartedAt = System.nanoTime();
        try {
            pageXmlVersionService.createVersion(xmlId, userId, "Saved from annotation editor");
        } catch (Exception e) {
            throw new IOException("Failed to create version before save for XML " + xmlId, e);
        }
        long versionMs = (System.nanoTime() - versionStartedAt) / 1_000_000;

        PageDto saveReadyPageDto = enrichMetadataForSave(pageDto, userId);
        annotationReadCache.evict(xmlId);

        if (schema == XmlSchema.PAGE_XML) {
            long writeStartedAt = System.nanoTime();
            Path tempPath = Files.createTempFile(xmlPath.getParent(), xmlPath.getFileName().toString(), ".tmp");
            try {
                PageXmlWriteResult writeResult = pageXmlExporter.writeValidated(saveReadyPageDto, xml, tempPath);
                replaceAtomically(tempPath, xmlPath);

                long newSize = Files.size(xmlPath);
                xml.setFileSize(newSize);
                pageXmlRepository.save(xml);
                try {
                    annotationReadCache.put(xmlId, xmlPath, saveReadyPageDto);
                } catch (IOException e) {
                    log.warn("Saved XML {} but failed to warm annotation cache: {}", xmlId, e.getMessage());
                }

                if (xml.getPage() != null) {
                    applicationEventPublisher.publishEvent(new AnnotationSavedEvent(
                            xmlId,
                            xml.getPage().getId(),
                            xml.getPage().getProject() != null ? xml.getPage().getProject().getId() : null,
                            saveReadyPageDto
                    ));
                    if (xml.getPage().getProject() != null && xml.getPage().getProject().getLibrary() != null) {
                        workspaceQuotaRefreshService.scheduleUsageRefresh(xml.getPage().getProject().getLibrary().getWorkspaceId());
                    }
                }

                if (log.isDebugEnabled()) {
                    log.debug("Saved PAGE XML {} in {} ms (version={} ms, write+replace={} ms, bytes={}, warnings={})",
                            xmlId,
                            (System.nanoTime() - saveStartedAt) / 1_000_000,
                            versionMs,
                            (System.nanoTime() - writeStartedAt) / 1_000_000,
                            writeResult.bytesWritten(),
                            writeResult.warnings().size());
                }
            } catch (Exception e) {
                Files.deleteIfExists(tempPath);
                throw e;
            }
        } else if (schema == XmlSchema.ALTO_XML) {
            throw new UnsupportedOperationException("ALTO XML export not yet migrated to PageDto");
        } else {
            throw new UnsupportedOperationException("No exporter available for schema: " + schema);
        }
    }

    @Transactional
    public PageXml createInitialAnnotationXml(String projectId, String pageId, PageDto pageDto, String userId) throws IOException {
        Page page = pageRepository.findByIdAndProjectId(pageId, projectId)
                .orElseThrow(() -> new IllegalArgumentException("Page not found: " + pageId));

        Optional<PageXml> existingPageXml = pageXmlRepository.findByPage_Id(pageId).stream()
                .filter(xml -> xml.getSchema() == XmlSchema.PAGE_XML)
                .findFirst();
        if (existingPageXml.isPresent()) {
            saveAnnotationToXml(existingPageXml.get().getId(), pageDto, userId);
            return existingPageXml.get();
        }

        String workspaceId = page.getProject().getLibrary().getWorkspaceId();
        PageDto saveReadyPageDto = enrichMetadataForSave(pageDto, userId);

        Path tempPath = Files.createTempFile("larex-initial-annotation-", ".xml");
        try {
            PageXmlWriteResult writeResult = pageXmlExporter.writeValidated(saveReadyPageDto, null, tempPath);
            String fileName = buildInitialXmlFilename(page.getName());
            var storedXml = hierarchicalFileStorageService.storeFromPath(
                    tempPath,
                    fileName,
                    pageXmlExporter.getMimeType(),
                    workspaceId,
                    projectId,
                    StoredFileType.XML,
                    userId,
                    true
            );

            String baseName = fileName.endsWith(".xml") ? fileName.substring(0, fileName.length() - 4) : fileName;
            PageXml createdXml = new PageXml(
                    storedXml.originalFilename(),
                    storedXml.storagePath(),
                    storedXml.mimeType(),
                    storedXml.sizeBytes(),
                    "original",
                    baseName,
                    XmlSchema.PAGE_XML,
                    writeResult.schemaVersion(),
                    page
            );
            createdXml = pageXmlRepository.save(createdXml);

            Path xmlPath = Paths.get(uploadDir, createdXml.getFilePath());
            annotationReadCache.put(createdXml.getId(), xmlPath, saveReadyPageDto);
            applicationEventPublisher.publishEvent(new AnnotationSavedEvent(
                    createdXml.getId(),
                    page.getId(),
                    projectId,
                    saveReadyPageDto
            ));
            workspaceQuotaRefreshService.scheduleUsageRefresh(workspaceId);
            return createdXml;
        } catch (Exception e) {
            Files.deleteIfExists(tempPath);
            if (e instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Failed to create initial PAGE XML for page " + pageId, e);
        }
    }

    /**
     * Get available schemas for export.
     */
    public Map<XmlSchema, String> getAvailableExportSchemas() {
        Map<XmlSchema, String> schemas = new HashMap<>();
        schemas.put(XmlSchema.PAGE_XML, XmlSchema.PAGE_XML.getDisplayName());
        schemas.put(XmlSchema.ALTO_XML, XmlSchema.ALTO_XML.getDisplayName());
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
        return schema == XmlSchema.PAGE_XML || schema == XmlSchema.ALTO_XML;
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
        var userDefined = metadata != null ? metadata.userDefined() : null;
        var items = metadata != null ? metadata.items() : null;

        var saveMetadata = new de.uniwue.zpd.dachs.larex.backend.dto.page.metadata.MetadataDto(
            creator != null ? creator : fallbackCreator,
            created != null ? created : now,
            now,
            comments,
            externalRef,
            userDefined,
            items
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
            dto.alternativeImages(),
            dto.labels(),
            dto.userDefined(),
            dto.textStyle(),
            dto.layers(),
            dto.relations(),
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

    private String buildInitialXmlFilename(String pageName) {
        String safeStem = pageName == null ? "page" : pageName
                .replaceAll("[\\\\/:*?\"<>|]+", " ")
                .replaceAll("\\p{Cntrl}+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (safeStem.isBlank()) {
            safeStem = "page";
        }
        return safeStem + ".xml";
    }

    private void replaceAtomically(Path tempPath, Path xmlPath) throws IOException {
        try {
            Files.move(tempPath, xmlPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempPath, xmlPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}

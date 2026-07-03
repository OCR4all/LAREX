package de.uniwue.zpd.dachs.larex.backend.service.dataset;

import de.uniwue.zpd.dachs.larex.backend.dto.PageXmlTextDto;
import de.uniwue.zpd.dachs.larex.backend.dto.PageXmlVersionDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Dataset;
import de.uniwue.zpd.dachs.larex.backend.entity.DatasetItem;
import de.uniwue.zpd.dachs.larex.backend.entity.DatasetItemCopyFile;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.repository.dataset.DatasetItemRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.dataset.DatasetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.cache.AnnotationReadCache;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.exporter.AnnotationToPageXmlExporter;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.parser.PageXmlToAnnotationParser;
import de.uniwue.zpd.dachs.larex.backend.service.security.AuthorizationPolicyService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaRefreshService;
import de.uniwue.zpd.dachs.larex.backend.service.version.DatasetItemCopyXmlVersionService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlValidationService;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class DatasetCopyAnnotationService {

    private static final Logger log = LoggerFactory.getLogger(DatasetCopyAnnotationService.class);

    private final DatasetRepository datasetRepository;
    private final DatasetItemRepository datasetItemRepository;
    private final PageXmlRepository pageXmlRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final AuthorizationPolicyService authorizationPolicyService;
    private final PageXmlToAnnotationParser pageXmlParser;
    private final AnnotationToPageXmlExporter pageXmlExporter;
    private final AnnotationReadCache annotationReadCache;
    private final DatasetItemCopyXmlVersionService copyXmlVersionService;
    private final PageXmlValidationService pageXmlValidationService;
    private final WorkspaceQuotaRefreshService workspaceQuotaRefreshService;

    @Value("${file.upload-dir}")
    private String uploadDir;

    public DatasetCopyAnnotationService(DatasetRepository datasetRepository,
                                        DatasetItemRepository datasetItemRepository,
                                        PageXmlRepository pageXmlRepository,
                                        WorkspaceAccessService workspaceAccessService,
                                        AuthorizationPolicyService authorizationPolicyService,
                                        PageXmlToAnnotationParser pageXmlParser,
                                        AnnotationToPageXmlExporter pageXmlExporter,
                                        AnnotationReadCache annotationReadCache,
                                        DatasetItemCopyXmlVersionService copyXmlVersionService,
                                        PageXmlValidationService pageXmlValidationService,
                                        WorkspaceQuotaRefreshService workspaceQuotaRefreshService) {
        this.datasetRepository = datasetRepository;
        this.datasetItemRepository = datasetItemRepository;
        this.pageXmlRepository = pageXmlRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.authorizationPolicyService = authorizationPolicyService;
        this.pageXmlParser = pageXmlParser;
        this.pageXmlExporter = pageXmlExporter;
        this.annotationReadCache = annotationReadCache;
        this.copyXmlVersionService = copyXmlVersionService;
        this.pageXmlValidationService = pageXmlValidationService;
        this.workspaceQuotaRefreshService = workspaceQuotaRefreshService;
    }

    public DatasetCopyXmlAccessContext resolveAccessContext(String workspaceId,
                                                            String datasetId,
                                                            String itemId,
                                                            String xmlId,
                                                            String userId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        Dataset dataset = datasetRepository.findByIdAndWorkspaceId(datasetId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Dataset not found: " + datasetId));

        DatasetItem item = datasetItemRepository.findByIdAndDatasetId(itemId, datasetId)
                .orElseThrow(() -> new IllegalArgumentException("Dataset item not found: " + itemId));

        if (item.getMode() != DatasetItem.Mode.COPY) {
            throw new IllegalArgumentException("Dataset item is not a frozen copy: " + itemId);
        }

        DatasetItemCopyFile copyXml = (item.getCopyFiles() == null ? List.<DatasetItemCopyFile>of() : item.getCopyFiles()).stream()
                .filter(file -> file.getKind() == DatasetItemCopyFile.Kind.XML)
                .filter(file -> xmlId.equals(file.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("COPY XML file not found for dataset item: " + xmlId));

        String workspace = dataset.getWorkspaceId();
        boolean canEdit = authorizationPolicyService.canAccessWorkspace(workspace, userId);
        boolean canForceTakeover = authorizationPolicyService.canManageProjects(workspace, userId);

        LocalDateTime updatedAt = copyXml.getUpdated() != null ? copyXml.getUpdated() : copyXml.getCreated();
        String persistedRevision = updatedAt != null ? updatedAt.toString() : copyXml.getId();

        return new DatasetCopyXmlAccessContext(
                dataset,
                item,
                copyXml,
                resolvePath(copyXml.getFilePath()),
                canEdit,
                canForceTakeover,
                item.getSourceProjectId(),
                item.getSourcePageId(),
                item.getSourceProjectName(),
                item.getSourcePageName(),
                persistedRevision,
                updatedAt,
                datasetId + ":" + itemId + ":" + xmlId
        );
    }

    public DatasetCopyImageAccessContext resolveImageAccessContext(String workspaceId,
                                                                   String datasetId,
                                                                   String itemId,
                                                                   String imageId,
                                                                   String userId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        Dataset dataset = datasetRepository.findByIdAndWorkspaceId(datasetId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Dataset not found: " + datasetId));

        DatasetItem item = datasetItemRepository.findByIdAndDatasetId(itemId, datasetId)
                .orElseThrow(() -> new IllegalArgumentException("Dataset item not found: " + itemId));

        if (item.getMode() != DatasetItem.Mode.COPY) {
            throw new IllegalArgumentException("Dataset item is not a frozen copy: " + itemId);
        }

        DatasetItemCopyFile copyImage = (item.getCopyFiles() == null ? List.<DatasetItemCopyFile>of() : item.getCopyFiles()).stream()
                .filter(file -> file.getKind() == DatasetItemCopyFile.Kind.IMAGE)
                .filter(file -> imageId.equals(file.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("COPY image file not found for dataset item: " + imageId));

        return new DatasetCopyImageAccessContext(
                dataset,
                item,
                copyImage,
                resolvePath(copyImage.getFilePath())
        );
    }

    public PageDto loadAnnotation(String workspaceId,
                                  String datasetId,
                                  String itemId,
                                  String xmlId,
                                  String userId) throws IOException {
        DatasetCopyXmlAccessContext context = resolveAccessContext(workspaceId, datasetId, itemId, xmlId, userId);
        ensurePageXmlSchema(context.copyXml());

        if (!Files.exists(context.xmlPath())) {
            throw new IOException("COPY XML file not found on disk: " + context.xmlPath());
        }

        String cacheKey = annotationCacheKey(context);
        PageDto cached = annotationReadCache.getIfFresh(cacheKey, context.xmlPath());
        if (cached != null) {
            return cached;
        }

        PageDto parsed = pageXmlParser.parse(context.xmlPath(), toPseudoPageXml(context));
        annotationReadCache.put(cacheKey, context.xmlPath(), parsed);
        return parsed;
    }

    @Transactional
    public void saveAnnotation(String workspaceId,
                               String datasetId,
                               String itemId,
                               String xmlId,
                               PageDto pageDto,
                               String userId) throws IOException {
        DatasetCopyXmlAccessContext context = resolveAccessContext(workspaceId, datasetId, itemId, xmlId, userId);
        assertEditable(context);
        ensurePageXmlSchema(context.copyXml());

        if (!Files.exists(context.xmlPath())) {
            throw new IOException("COPY XML file not found on disk: " + context.xmlPath());
        }

        copyXmlVersionService.createVersion(context.copyXml(), userId, "Saved from annotation editor");
        copyXmlVersionService.pruneOldVersions(context.copyXml().getId());

        String cacheKey = annotationCacheKey(context);
        annotationReadCache.evict(cacheKey);

        Path tempPath = Files.createTempFile(context.xmlPath().getParent(), context.xmlPath().getFileName().toString(), ".tmp");
        try {
            pageXmlExporter.writeValidated(pageDto, toPseudoPageXml(context), tempPath);
            replaceAtomically(tempPath, context.xmlPath());

            long newSize = Files.size(context.xmlPath());
            context.copyXml().setFileSize(newSize);
            datasetItemRepository.save(context.item());

            try {
                annotationReadCache.put(cacheKey, context.xmlPath(), pageDto);
            } catch (IOException e) {
                log.warn("Saved COPY XML {} but failed to warm annotation cache: {}", context.copyXml().getId(), e.getMessage());
            }

            workspaceQuotaRefreshService.scheduleUsageRefresh(workspaceId);
        } catch (Exception e) {
            Files.deleteIfExists(tempPath);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<PageXmlVersionDto> listVersions(String workspaceId,
                                                String datasetId,
                                                String itemId,
                                                String xmlId,
                                                String userId) {
        DatasetCopyXmlAccessContext context = resolveAccessContext(workspaceId, datasetId, itemId, xmlId, userId);
        return copyXmlVersionService.listVersions(context.copyXml().getId());
    }

    @Transactional(readOnly = true)
    public String getVersionContent(String workspaceId,
                                    String datasetId,
                                    String itemId,
                                    String xmlId,
                                    String versionId,
                                    String userId) throws IOException {
        DatasetCopyXmlAccessContext context = resolveAccessContext(workspaceId, datasetId, itemId, xmlId, userId);
        return copyXmlVersionService.getVersionContent(versionId, context.copyXml().getId());
    }

    @Transactional(readOnly = true)
    public PageDto loadVersionAnnotation(String workspaceId,
                                         String datasetId,
                                         String itemId,
                                         String xmlId,
                                         String versionId,
                                         String userId) throws IOException {
        DatasetCopyXmlAccessContext context = resolveAccessContext(workspaceId, datasetId, itemId, xmlId, userId);
        ensurePageXmlSchema(context.copyXml());

        Path versionPath = copyXmlVersionService.resolveVersionPath(versionId, context.copyXml().getId());
        return pageXmlParser.parse(versionPath, toPseudoPageXml(context));
    }

    @Transactional
    public void restoreVersion(String workspaceId,
                               String datasetId,
                               String itemId,
                               String xmlId,
                               String versionId,
                               String userId) throws IOException {
        DatasetCopyXmlAccessContext context = resolveAccessContext(workspaceId, datasetId, itemId, xmlId, userId);
        assertEditable(context);
        copyXmlVersionService.restoreVersion(versionId, context.copyXml().getId(), userId);
        copyXmlVersionService.pruneOldVersions(context.copyXml().getId());
        annotationReadCache.evict(annotationCacheKey(context));
        workspaceQuotaRefreshService.scheduleUsageRefresh(workspaceId);
    }

    public PageXmlTextDto.XmlTextResponse getXmlText(String workspaceId,
                                                     String datasetId,
                                                     String itemId,
                                                     String xmlId,
                                                     String userId) throws IOException {
        DatasetCopyXmlAccessContext context = resolveAccessContext(workspaceId, datasetId, itemId, xmlId, userId);
        ensurePageXmlSchema(context.copyXml());

        if (!Files.exists(context.xmlPath())) {
            throw new IOException("COPY XML file not found on disk: " + context.xmlPath());
        }

        String xml = Files.readString(context.xmlPath(), StandardCharsets.UTF_8);
        PageXmlTextDto.XmlValidationResult validation = pageXmlValidationService.validatePageXml(xml);

        return new PageXmlTextDto.XmlTextResponse(
                context.copyXml().getId(),
                XmlSchema.PAGE_XML.name(),
                xml,
                validation
        );
    }

    public void assertXmlAccess(String workspaceId,
                                String datasetId,
                                String itemId,
                                String xmlId,
                                String userId) {
        resolveAccessContext(workspaceId, datasetId, itemId, xmlId, userId);
    }

    public PageXmlTextDto.XmlValidationResult validateXmlText(String xmlText) {
        return pageXmlValidationService.validatePageXml(xmlText);
    }

    @Transactional
    public PageXmlTextDto.XmlValidationResult saveXmlText(String workspaceId,
                                                          String datasetId,
                                                          String itemId,
                                                          String xmlId,
                                                          String xmlText,
                                                          String comment,
                                                          String userId) throws IOException {
        DatasetCopyXmlAccessContext context = resolveAccessContext(workspaceId, datasetId, itemId, xmlId, userId);
        assertEditable(context);
        ensurePageXmlSchema(context.copyXml());

        if (!Files.exists(context.xmlPath())) {
            throw new IOException("COPY XML file not found on disk: " + context.xmlPath());
        }

        PageXmlTextDto.XmlValidationResult validation = pageXmlValidationService.validatePageXml(xmlText);
        if (!validation.valid()) {
            return validation;
        }

        String versionComment = normalizeComment(comment);
        copyXmlVersionService.createVersion(context.copyXml(), userId, versionComment);
        copyXmlVersionService.pruneOldVersions(context.copyXml().getId());

        Path tempPath = Files.createTempFile(context.xmlPath().getParent(), context.xmlPath().getFileName().toString(), ".tmp");
        try {
            Files.writeString(tempPath, xmlText, StandardCharsets.UTF_8);
            replaceAtomically(tempPath, context.xmlPath());

            long newSize = Files.size(context.xmlPath());
            context.copyXml().setFileSize(newSize);
            datasetItemRepository.save(context.item());
        } catch (Exception e) {
            Files.deleteIfExists(tempPath);
            throw e;
        }

        annotationReadCache.evict(annotationCacheKey(context));
        workspaceQuotaRefreshService.scheduleUsageRefresh(workspaceId);
        return validation;
    }

    public void assertEditable(DatasetCopyXmlAccessContext context) {
        if (context == null || !context.canEdit()) {
            throw new AccessDeniedException("You do not have permission to edit this dataset copy XML");
        }
    }

    private void ensurePageXmlSchema(DatasetItemCopyFile copyXml) {
        PageXml sourceXml = pageXmlRepository.findById(copyXml.getSourceFileId()).orElse(null);
        if (sourceXml != null && sourceXml.getSchema() != XmlSchema.PAGE_XML) {
            throw new UnsupportedOperationException("Raw/editor annotation is only supported for PAGE XML");
        }
    }

    private PageXml toPseudoPageXml(DatasetCopyXmlAccessContext context) {
        PageXml pseudo = new PageXml();
        pseudo.setId(context.copyXml().getId());
        pseudo.setFileName(context.copyXml().getFileName());
        pseudo.setFilePath(context.copyXml().getFilePath());
        pseudo.setMimeType(context.copyXml().getMimeType());
        pseudo.setFileSize(context.copyXml().getFileSize());
        pseudo.setVariant(context.copyXml().getVariant());
        pseudo.setBaseName(context.copyXml().getBaseName());
        pseudo.setSchema(XmlSchema.PAGE_XML);

        PageXml sourceXml = pageXmlRepository.findById(context.copyXml().getSourceFileId()).orElse(null);
        if (sourceXml != null && sourceXml.getSchemaVersion() != null) {
            pseudo.setSchemaVersion(sourceXml.getSchemaVersion());
        }
        return pseudo;
    }

    private String annotationCacheKey(DatasetCopyXmlAccessContext context) {
        return "dataset-copy:" + context.dataset().getId() + ":" + context.item().getId() + ":" + context.copyXml().getId();
    }

    private String normalizeComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return "Saved from XML editor";
        }
        String trimmed = comment.trim();
        return trimmed.isEmpty() ? "Saved from XML editor" : trimmed;
    }

    private Path resolvePath(String relativePath) {
        Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path candidate = Paths.get(relativePath);
        Path resolved = candidate.isAbsolute()
                ? candidate.toAbsolutePath().normalize()
                : uploadRoot.resolve(relativePath).normalize();

        if (!resolved.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("Invalid dataset copy file path");
        }

        return resolved;
    }

    private void replaceAtomically(Path tempPath, Path xmlPath) throws IOException {
        try {
            Files.move(tempPath, xmlPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempPath, xmlPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public record DatasetCopyXmlAccessContext(
            Dataset dataset,
            DatasetItem item,
            DatasetItemCopyFile copyXml,
            Path xmlPath,
            boolean canEdit,
            boolean canForceTakeover,
            String sourceProjectId,
            String sourcePageId,
            String sourceProjectName,
            String sourcePageName,
            String persistedRevision,
            LocalDateTime updatedAt,
            String roomKey
    ) {}

    public record DatasetCopyImageAccessContext(
            Dataset dataset,
            DatasetItem item,
            DatasetItemCopyFile copyImage,
            Path imagePath
    ) {}
}

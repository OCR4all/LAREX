package de.uniwue.zpd.dachs.larex.backend.service.xml;

import de.uniwue.zpd.dachs.larex.backend.dto.PageXmlTextDto;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.cache.AnnotationReadCache;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageService;
import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageFilterIndexService;
import de.uniwue.zpd.dachs.larex.backend.service.security.AuthorizationPolicyService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaRefreshService;
import de.uniwue.zpd.dachs.larex.backend.service.upload.UploadPathService;
import de.uniwue.zpd.dachs.larex.backend.service.version.PageXmlVersionService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
public class PageXmlRawEditService {

    private final PageService pageService;
    private final PageXmlRepository pageXmlRepository;
    private final PageXmlVersionService pageXmlVersionService;
    private final AnnotationReadCache annotationReadCache;
    private final PageFilterIndexService pageFilterIndexService;
    private final PageXmlValidationService pageXmlValidationService;
    private final WorkspaceQuotaRefreshService workspaceQuotaRefreshService;
    private final AuthorizationPolicyService authorizationPolicyService;
    private final UploadPathService uploadPathService;

    public PageXmlRawEditService(
            PageService pageService,
            PageXmlRepository pageXmlRepository,
            PageXmlVersionService pageXmlVersionService,
            AnnotationReadCache annotationReadCache,
            PageFilterIndexService pageFilterIndexService,
            PageXmlValidationService pageXmlValidationService,
            WorkspaceQuotaRefreshService workspaceQuotaRefreshService,
            AuthorizationPolicyService authorizationPolicyService,
            UploadPathService uploadPathService) {
        this.pageService = pageService;
        this.pageXmlRepository = pageXmlRepository;
        this.pageXmlVersionService = pageXmlVersionService;
        this.annotationReadCache = annotationReadCache;
        this.pageFilterIndexService = pageFilterIndexService;
        this.pageXmlValidationService = pageXmlValidationService;
        this.workspaceQuotaRefreshService = workspaceQuotaRefreshService;
        this.authorizationPolicyService = authorizationPolicyService;
        this.uploadPathService = uploadPathService;
    }

    public PageXmlTextDto.XmlTextResponse getXmlText(String projectId, String pageId, String xmlId, String userId) throws IOException {
        PageXml pageXml = resolvePageXml(projectId, pageId, xmlId, userId);
        Path xmlPath = resolveXmlPath(pageXml);
        if (!Files.exists(xmlPath)) {
            throw new IOException("XML file not found on disk: " + xmlPath);
        }

        String xml = Files.readString(xmlPath, StandardCharsets.UTF_8);
        PageXmlTextDto.XmlValidationResult validation = pageXmlValidationService.validatePageXml(xml);

        return new PageXmlTextDto.XmlTextResponse(
                pageXml.getId(),
                pageXml.getSchema().name(),
                xml,
                validation
        );
    }

    public PageXmlTextDto.XmlValidationResult validateXmlText(String xmlText) {
        return pageXmlValidationService.validatePageXml(xmlText);
    }

    public void assertPageXmlAccess(String projectId, String pageId, String xmlId, String userId) {
        resolvePageXml(projectId, pageId, xmlId, userId);
    }

    @Transactional
    public PageXmlTextDto.XmlValidationResult saveXmlText(
            String projectId,
            String pageId,
            String xmlId,
            String xmlText,
            String comment,
            String userId) throws IOException {

        PageXml pageXml = resolvePageXml(projectId, pageId, xmlId, userId);
        assertPageXmlEditable(pageXml, userId);
        Path xmlPath = resolveXmlPath(pageXml);
        if (!Files.exists(xmlPath)) {
            throw new IOException("XML file not found on disk: " + xmlPath);
        }

        PageXmlTextDto.XmlValidationResult validation = pageXmlValidationService.validatePageXml(xmlText);
        if (!validation.valid()) {
            return validation;
        }

        String versionComment = normalizeComment(comment);
        pageXmlVersionService.createVersion(xmlId, userId, versionComment);

        Path tempPath = Files.createTempFile(xmlPath.getParent(), xmlPath.getFileName().toString(), ".tmp");
        try {
            Files.writeString(tempPath, xmlText, StandardCharsets.UTF_8);
            replaceAtomically(tempPath, xmlPath);
            long newSize = Files.size(xmlPath);
            pageXml.setFileSize(newSize);
            if (validation.pageVersion() != null && !validation.pageVersion().isBlank()) {
                pageXml.setSchemaVersion(validation.pageVersion());
            }
            pageXmlRepository.save(pageXml);
        } catch (Exception e) {
            Files.deleteIfExists(tempPath);
            throw e;
        }

        annotationReadCache.evict(xmlId);
        if (pageXml.getPage() != null) {
            pageFilterIndexService.indexPageFromXml(pageXml.getPage());
            if (pageXml.getPage().getProject() != null && pageXml.getPage().getProject().getLibrary() != null) {
                workspaceQuotaRefreshService.scheduleUsageRefresh(pageXml.getPage().getProject().getLibrary().getWorkspaceId());
            }
        }
        return validation;
    }

    private PageXml resolvePageXml(String projectId, String pageId, String xmlId, String userId) {
        PageXml pageXml = pageService.getXmlById(xmlId, userId);
        if (pageXml == null) {
            throw new IllegalArgumentException("XML file not found: " + xmlId);
        }

        if (pageXml.getSchema() != XmlSchema.PAGE_XML) {
            throw new UnsupportedOperationException("Raw XML editing is only supported for PAGE XML");
        }

        boolean pageMismatch = pageXml.getPage() == null || !pageId.equals(pageXml.getPage().getId());
        boolean projectMismatch = pageXml.getPage() == null
                || pageXml.getPage().getProject() == null
                || !projectId.equals(pageXml.getPage().getProject().getId());

        if (pageMismatch || projectMismatch) {
            throw new IllegalArgumentException("XML file not found for requested page");
        }

        return pageXml;
    }

    private void assertPageXmlEditable(PageXml pageXml, String userId) {
        if (pageXml.getPage() == null || pageXml.getPage().getProject() == null || pageXml.getPage().getProject().getLibrary() == null) {
            throw new AccessDeniedException("XML file is not attached to an editable page");
        }

        String workspaceId = pageXml.getPage().getProject().getLibrary().getWorkspaceId();
        boolean canEdit = authorizationPolicyService.canAccessWorkspace(workspaceId, userId)
                && !pageXml.getPage().getProject().isLocked()
                && !pageXml.getPage().isEffectivelyLocked();

        if (!canEdit) {
            throw new AccessDeniedException("You do not have permission to edit this XML");
        }
    }

    private Path resolveXmlPath(PageXml pageXml) {
        return uploadPathService.resolve(pageXml.getFilePath());
    }

    private String normalizeComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return "Saved from XML editor";
        }
        String trimmed = comment.trim();
        return trimmed.isEmpty() ? "Saved from XML editor" : trimmed;
    }

    private void replaceAtomically(Path tempPath, Path xmlPath) throws IOException {
        try {
            Files.move(tempPath, xmlPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempPath, xmlPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}

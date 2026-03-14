package de.uniwue.zpd.dachs.larex.backend.service.version;

import de.uniwue.zpd.dachs.larex.backend.dto.PageXmlVersionDto;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXmlVersion;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlVersionRepository;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaRefreshService;
import de.uniwue.zpd.dachs.larex.backend.service.version.events.PageXmlVersionCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class PageXmlVersionService {

    private static final Logger log = LoggerFactory.getLogger(PageXmlVersionService.class);

    private final PageXmlVersionRepository versionRepository;
    private final PageXmlRepository pageXmlRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final WorkspaceQuotaRefreshService workspaceQuotaRefreshService;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${larex.versioning.max-versions-per-xml:25}")
    private int maxVersionsPerXml;

    public PageXmlVersionService(PageXmlVersionRepository versionRepository,
                                 PageXmlRepository pageXmlRepository,
                                 ApplicationEventPublisher applicationEventPublisher,
                                 WorkspaceQuotaRefreshService workspaceQuotaRefreshService) {
        this.versionRepository = versionRepository;
        this.pageXmlRepository = pageXmlRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.workspaceQuotaRefreshService = workspaceQuotaRefreshService;
    }

    @Transactional
    public PageXmlVersion createVersion(String pageXmlId, String userId, String comment) throws IOException {
        Optional<PageXml> xmlOpt = pageXmlRepository.findById(pageXmlId);
        if (xmlOpt.isEmpty()) {
            throw new IllegalArgumentException("XML file not found: " + pageXmlId);
        }

        PageXml xml = xmlOpt.get();
        Path currentXmlPath = Paths.get(uploadDir, xml.getFilePath());

        if (!Files.exists(currentXmlPath)) {
            throw new IOException("XML file not found on disk: " + currentXmlPath);
        }

        int nextVersion = versionRepository.findMaxVersionNumber(pageXmlId) + 1;
        String versionRelativePath = "xml/versions/" + pageXmlId + "/" + nextVersion + ".xml";
        Path versionPath = Paths.get(uploadDir, versionRelativePath);

        Files.createDirectories(versionPath.getParent());
        Files.copy(currentXmlPath, versionPath, StandardCopyOption.REPLACE_EXISTING);

        long fileSize = Files.size(versionPath);

        PageXmlVersion version = new PageXmlVersion(xml, nextVersion, versionRelativePath, fileSize, userId, comment);
        version = versionRepository.save(version);

        log.info("Created version {} for XML {} ({})", nextVersion, pageXmlId, comment);
        applicationEventPublisher.publishEvent(new PageXmlVersionCreatedEvent(pageXmlId));

        return version;
    }

    @Transactional(readOnly = true)
    public List<PageXmlVersionDto> listVersions(String pageXmlId) {
        return versionRepository.findByPageXml_IdOrderByVersionNumberDesc(pageXmlId)
                .stream()
                .map(PageXmlVersionDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public String getVersionContent(String versionId) throws IOException {
        Optional<PageXmlVersion> versionOpt = versionRepository.findById(versionId);
        if (versionOpt.isEmpty()) {
            throw new IllegalArgumentException("Version not found: " + versionId);
        }

        Path versionPath = Paths.get(uploadDir, versionOpt.get().getFilePath());
        if (!Files.exists(versionPath)) {
            throw new IOException("Version file not found on disk: " + versionPath);
        }

        return Files.readString(versionPath);
    }

    @Transactional
    public void restoreVersion(String versionId, String userId) throws IOException {
        Optional<PageXmlVersion> versionOpt = versionRepository.findById(versionId);
        if (versionOpt.isEmpty()) {
            throw new IllegalArgumentException("Version not found: " + versionId);
        }

        PageXmlVersion version = versionOpt.get();
        PageXml xml = version.getPageXml();
        String pageXmlId = xml.getId();

        // Auto-save current state before restoring
        createVersion(pageXmlId, userId, "Auto-save before restore from version " + version.getVersionNumber());

        // Copy version file back to canonical path
        Path versionPath = Paths.get(uploadDir, version.getFilePath());
        Path canonicalPath = Paths.get(uploadDir, xml.getFilePath());

        Files.copy(versionPath, canonicalPath, StandardCopyOption.REPLACE_EXISTING);

        // Update file size on the PageXml entity
        long newSize = Files.size(canonicalPath);
        xml.setFileSize(newSize);
        pageXmlRepository.save(xml);
        scheduleWorkspaceUsageRefresh(xml);

        log.info("Restored XML {} to version {} by user {}", pageXmlId, version.getVersionNumber(), userId);
    }

    @Transactional
    public void pruneOldVersions(String pageXmlId) {
        long count = versionRepository.countByPageXml_Id(pageXmlId);
        if (count <= maxVersionsPerXml) {
            return;
        }

        int toDelete = (int) (count - maxVersionsPerXml);
        List<PageXmlVersion> oldest = versionRepository.findOldestVersions(pageXmlId, PageRequest.of(0, toDelete));

        for (PageXmlVersion version : oldest) {
            try {
                Path versionPath = Paths.get(uploadDir, version.getFilePath());
                Files.deleteIfExists(versionPath);
            } catch (IOException e) {
                log.warn("Failed to delete version file {}: {}", version.getFilePath(), e.getMessage());
            }
            versionRepository.delete(version);
        }

        oldest.stream()
                .map(PageXmlVersion::getPageXml)
                .findFirst()
                .ifPresent(this::scheduleWorkspaceUsageRefresh);

        log.info("Pruned {} old versions for XML {}", toDelete, pageXmlId);
    }

    public void deleteVersionDirectory(String pageXmlId) {
        deleteVersionDirectoryInternal(pageXmlId);
    }

    public int deleteVersionDirectories(Collection<String> pageXmlIds) {
        if (pageXmlIds == null || pageXmlIds.isEmpty()) {
            return 0;
        }

        List<String> normalizedIds = pageXmlIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
        if (normalizedIds.isEmpty()) {
            return 0;
        }

        if (normalizedIds.size() == 1) {
            return deleteVersionDirectoryInternal(normalizedIds.get(0)) ? 1 : 0;
        }

        return (int) normalizedIds.parallelStream()
                .map(this::deleteVersionDirectoryInternal)
                .filter(Boolean::booleanValue)
                .count();
    }

    private Boolean deleteVersionDirectoryInternal(String pageXmlId) {
        Path versionDir = Paths.get(uploadDir, "xml/versions/" + pageXmlId);
        if (!Files.exists(versionDir)) {
            return false;
        }

        try (Stream<Path> paths = Files.walk(versionDir)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            log.warn("Failed to delete version path {}: {}", path, e.getMessage());
                        }
                    });
            log.debug("Deleted version directory for XML {}", pageXmlId);
            return true;
        } catch (IOException e) {
            log.warn("Failed to walk version directory for XML {}: {}", pageXmlId, e.getMessage());
            return false;
        }
    }

    private void scheduleWorkspaceUsageRefresh(PageXml pageXml) {
        if (pageXml == null || pageXml.getPage() == null || pageXml.getPage().getProject() == null
                || pageXml.getPage().getProject().getLibrary() == null) {
            return;
        }

        workspaceQuotaRefreshService.scheduleUsageRefresh(pageXml.getPage().getProject().getLibrary().getWorkspaceId());
    }
}

package de.uniwue.zpd.dachs.larex.backend.service.version;

import de.uniwue.zpd.dachs.larex.backend.config.VersioningProperties;
import de.uniwue.zpd.dachs.larex.backend.dto.PageXmlVersionDto;
import de.uniwue.zpd.dachs.larex.backend.dto.UserDto;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXmlVersion;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlVersionRepository;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaRefreshService;
import de.uniwue.zpd.dachs.larex.backend.service.upload.UploadPathService;
import de.uniwue.zpd.dachs.larex.backend.service.user.UserService;
import de.uniwue.zpd.dachs.larex.backend.service.version.events.PageXmlVersionCreatedEvent;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlPrettyPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private final UserService userService;
    private final VersioningProperties versioningProperties;
    private final UploadPathService uploadPathService;

    public PageXmlVersionService(PageXmlVersionRepository versionRepository,
                                 PageXmlRepository pageXmlRepository,
                                 ApplicationEventPublisher applicationEventPublisher,
                                 WorkspaceQuotaRefreshService workspaceQuotaRefreshService,
                                 UserService userService,
                                 VersioningProperties versioningProperties,
                                 UploadPathService uploadPathService) {
        this.versionRepository = versionRepository;
        this.pageXmlRepository = pageXmlRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.workspaceQuotaRefreshService = workspaceQuotaRefreshService;
        this.userService = userService;
        this.versioningProperties = versioningProperties;
        this.uploadPathService = uploadPathService;
    }

    @Transactional
    public PageXmlVersion createVersion(String pageXmlId, String userId, String comment) throws IOException {
        Optional<PageXml> xmlOpt = pageXmlRepository.findById(pageXmlId);
        if (xmlOpt.isEmpty()) {
            throw new IllegalArgumentException("XML file not found: " + pageXmlId);
        }

        PageXml xml = xmlOpt.get();
        Path currentXmlPath = uploadPathService.resolve(xml.getFilePath());

        if (!Files.exists(currentXmlPath)) {
            throw new IOException("XML file not found on disk: " + currentXmlPath);
        }

        int nextVersion = versionRepository.findMaxVersionNumber(pageXmlId) + 1;
        String versionRelativePath = "xml/versions/" + pageXmlId + "/" + nextVersion + ".xml";
        Path versionPath = uploadPathService.resolve(versionRelativePath);

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
        List<PageXmlVersion> versions = versionRepository.findByPageXml_IdOrderByVersionNumberDesc(pageXmlId);
        List<String> userIds = versions.stream()
                .map(PageXmlVersion::getUserId)
                .filter(userId -> userId != null && !userId.isBlank())
                .distinct()
                .toList();
        java.util.Map<String, UserDto> usersById = userIds.isEmpty()
                ? java.util.Map.of()
                : userService.getUsersByIds(userIds);

        return versions.stream()
                .map(version -> PageXmlVersionDto.fromEntity(version, usersById.get(version.getUserId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public String getVersionContent(String versionId) throws IOException {
        return getVersionContent(versionId, null);
    }

    @Transactional(readOnly = true)
    public String getVersionContent(String versionId, String expectedPageXmlId) throws IOException {
        return Files.readString(resolveVersionPath(versionId, expectedPageXmlId));
    }

    @Transactional(readOnly = true)
    public PageXmlVersion requireVersion(String versionId, String expectedPageXmlId) {
        Optional<PageXmlVersion> versionOpt = versionRepository.findById(versionId);
        if (versionOpt.isEmpty()) {
            throw new IllegalArgumentException("Version not found: " + versionId);
        }

        PageXmlVersion version = versionOpt.get();
        if (expectedPageXmlId != null && (version.getPageXml() == null || !expectedPageXmlId.equals(version.getPageXml().getId()))) {
            throw new IllegalArgumentException("Version does not belong to requested XML file");
        }
        return version;
    }

    @Transactional(readOnly = true)
    public Path resolveVersionPath(String versionId, String expectedPageXmlId) throws IOException {
        PageXmlVersion version = requireVersion(versionId, expectedPageXmlId);
        Path versionPath = uploadPathService.resolve(version.getFilePath());
        if (!Files.exists(versionPath)) {
            throw new IOException("Version file not found on disk: " + versionPath);
        }
        return versionPath;
    }

    @Transactional
    public void restoreVersion(String versionId, String userId) throws IOException {
        restoreVersion(versionId, null, userId);
    }

    @Transactional
    public void restoreVersion(String versionId, String expectedPageXmlId, String userId) throws IOException {
        Optional<PageXmlVersion> versionOpt = versionRepository.findById(versionId);
        if (versionOpt.isEmpty()) {
            throw new IllegalArgumentException("Version not found: " + versionId);
        }

        PageXmlVersion version = versionOpt.get();
        if (expectedPageXmlId != null && (version.getPageXml() == null || !expectedPageXmlId.equals(version.getPageXml().getId()))) {
            throw new IllegalArgumentException("Version does not belong to requested XML file");
        }
        PageXml xml = version.getPageXml();
        String pageXmlId = xml.getId();

        // Auto-save current state before restoring
        createVersion(pageXmlId, userId, "Auto-save before restore from version " + version.getVersionNumber());

        // Copy version file back to canonical path
        Path versionPath = uploadPathService.resolve(version.getFilePath());
        Path canonicalPath = uploadPathService.resolve(xml.getFilePath());

        Files.copy(versionPath, canonicalPath, StandardCopyOption.REPLACE_EXISTING);
        PageXmlPrettyPrinter.prettyPrint(canonicalPath);

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
        if (count <= versioningProperties.getMaxVersionsPerXml()) {
            return;
        }

        int toDelete = (int) (count - versioningProperties.getMaxVersionsPerXml());
        List<PageXmlVersion> oldest = versionRepository.findOldestVersions(pageXmlId, PageRequest.of(0, toDelete));

        for (PageXmlVersion version : oldest) {
            try {
                Path versionPath = uploadPathService.resolve(version.getFilePath());
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
        Path versionDir = uploadPathService.resolve("xml", "versions", pageXmlId);
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

package de.uniwue.zpd.dachs.larex.backend.service.version;

import de.uniwue.zpd.dachs.larex.backend.config.VersioningProperties;
import de.uniwue.zpd.dachs.larex.backend.dto.PageXmlVersionDto;
import de.uniwue.zpd.dachs.larex.backend.dto.UserDto;
import de.uniwue.zpd.dachs.larex.backend.entity.DatasetItemCopyFile;
import de.uniwue.zpd.dachs.larex.backend.entity.DatasetItemCopyXmlVersion;
import de.uniwue.zpd.dachs.larex.backend.repository.dataset.DatasetItemCopyFileRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.dataset.DatasetItemCopyXmlVersionRepository;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaRefreshService;
import de.uniwue.zpd.dachs.larex.backend.service.upload.UploadPathService;
import de.uniwue.zpd.dachs.larex.backend.service.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

@Service
public class DatasetItemCopyXmlVersionService {

    private static final Logger log = LoggerFactory.getLogger(DatasetItemCopyXmlVersionService.class);

    private final DatasetItemCopyXmlVersionRepository versionRepository;
    private final DatasetItemCopyFileRepository copyFileRepository;
    private final WorkspaceQuotaRefreshService workspaceQuotaRefreshService;
    private final UserService userService;
    private final VersioningProperties versioningProperties;
    private final UploadPathService uploadPathService;

    public DatasetItemCopyXmlVersionService(DatasetItemCopyXmlVersionRepository versionRepository,
                                            DatasetItemCopyFileRepository copyFileRepository,
                                            WorkspaceQuotaRefreshService workspaceQuotaRefreshService,
                                            UserService userService,
                                            VersioningProperties versioningProperties,
                                            UploadPathService uploadPathService) {
        this.versionRepository = versionRepository;
        this.copyFileRepository = copyFileRepository;
        this.workspaceQuotaRefreshService = workspaceQuotaRefreshService;
        this.userService = userService;
        this.versioningProperties = versioningProperties;
        this.uploadPathService = uploadPathService;
    }

    @Transactional
    public DatasetItemCopyXmlVersion createVersion(DatasetItemCopyFile copyXmlFile, String userId, String comment) throws IOException {
        if (copyXmlFile == null || copyXmlFile.getId() == null) {
            throw new IllegalArgumentException("COPY XML file not found");
        }

        Path currentXmlPath = resolvePath(copyXmlFile.getFilePath());
        if (!Files.exists(currentXmlPath)) {
            throw new IOException("COPY XML file not found on disk: " + currentXmlPath);
        }

        int nextVersion = versionRepository.findMaxVersionNumber(copyXmlFile.getId()) + 1;
        String versionRelativePath = buildVersionRelativePath(copyXmlFile, nextVersion);
        Path versionPath = resolvePath(versionRelativePath);

        Files.createDirectories(versionPath.getParent());
        Files.copy(currentXmlPath, versionPath, StandardCopyOption.REPLACE_EXISTING);

        long fileSize = Files.size(versionPath);

        DatasetItemCopyXmlVersion version = new DatasetItemCopyXmlVersion(
                copyXmlFile,
                nextVersion,
                versionRelativePath,
                fileSize,
                userId,
                comment
        );
        version = versionRepository.save(version);

        log.info("Created COPY XML version {} for copy file {} ({})", nextVersion, copyXmlFile.getId(), comment);

        return version;
    }

    @Transactional(readOnly = true)
    public List<PageXmlVersionDto> listVersions(String copyXmlFileId) {
        List<DatasetItemCopyXmlVersion> versions = versionRepository.findByCopyFile_IdOrderByVersionNumberDesc(copyXmlFileId);
        List<String> userIds = versions.stream()
                .map(DatasetItemCopyXmlVersion::getUserId)
                .filter(userId -> userId != null && !userId.isBlank())
                .distinct()
                .toList();

        java.util.Map<String, UserDto> usersById = userIds.isEmpty()
                ? java.util.Map.of()
                : userService.getUsersByIds(userIds);

        return versions.stream()
                .map(version -> new PageXmlVersionDto(
                        version.getId(),
                        version.getVersionNumber(),
                        version.getFileSize(),
                        version.getUserId(),
                        usersById.get(version.getUserId()) == null ? null : usersById.get(version.getUserId()).username(),
                        buildDisplayName(usersById.get(version.getUserId()), version.getUserId()),
                        version.getComment(),
                        version.getCreated()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public String getVersionContent(String versionId, String copyXmlFileId) throws IOException {
        return Files.readString(resolveVersionPath(versionId, copyXmlFileId));
    }

    @Transactional(readOnly = true)
    public Path resolveVersionPath(String versionId, String copyXmlFileId) throws IOException {
        DatasetItemCopyXmlVersion version = requireVersionForCopyFile(versionId, copyXmlFileId);
        Path versionPath = resolvePath(version.getFilePath());
        if (!Files.exists(versionPath)) {
            throw new IOException("Version file not found on disk: " + versionPath);
        }
        return versionPath;
    }

    @Transactional
    public void restoreVersion(String versionId, String copyXmlFileId, String userId) throws IOException {
        DatasetItemCopyXmlVersion version = requireVersionForCopyFile(versionId, copyXmlFileId);
        DatasetItemCopyFile copyXmlFile = version.getCopyFile();

        createVersion(copyXmlFile, userId, "Auto-save before restore from version " + version.getVersionNumber());

        Path versionPath = resolvePath(version.getFilePath());
        Path canonicalPath = resolvePath(copyXmlFile.getFilePath());

        Files.copy(versionPath, canonicalPath, StandardCopyOption.REPLACE_EXISTING);

        long newSize = Files.size(canonicalPath);
        copyXmlFile.setFileSize(newSize);
        copyFileRepository.save(copyXmlFile);

        scheduleWorkspaceUsageRefresh(copyXmlFile);

        log.info("Restored COPY XML {} to version {} by user {}", copyXmlFileId, version.getVersionNumber(), userId);
    }

    @Transactional
    public void pruneOldVersions(String copyXmlFileId) {
        long count = versionRepository.countByCopyFile_Id(copyXmlFileId);
        if (count <= versioningProperties.getMaxVersionsPerXml()) {
            return;
        }

        int toDelete = (int) (count - versioningProperties.getMaxVersionsPerXml());
        List<DatasetItemCopyXmlVersion> oldest = versionRepository.findOldestVersions(copyXmlFileId, PageRequest.of(0, toDelete));

        for (DatasetItemCopyXmlVersion version : oldest) {
            try {
                Path versionPath = resolvePath(version.getFilePath());
                Files.deleteIfExists(versionPath);
            } catch (IOException e) {
                log.warn("Failed to delete COPY XML version file {}: {}", version.getFilePath(), e.getMessage());
            }
            versionRepository.delete(version);
        }

        oldest.stream()
                .map(DatasetItemCopyXmlVersion::getCopyFile)
                .findFirst()
                .ifPresent(this::scheduleWorkspaceUsageRefresh);

        log.info("Pruned {} old COPY XML version(s) for copy file {}", toDelete, copyXmlFileId);
    }

    private DatasetItemCopyXmlVersion requireVersionForCopyFile(String versionId, String copyXmlFileId) {
        Optional<DatasetItemCopyXmlVersion> versionOpt = versionRepository.findById(versionId);
        if (versionOpt.isEmpty()) {
            throw new IllegalArgumentException("Version not found: " + versionId);
        }

        DatasetItemCopyXmlVersion version = versionOpt.get();
        if (version.getCopyFile() == null || version.getCopyFile().getId() == null
                || !copyXmlFileId.equals(version.getCopyFile().getId())) {
            throw new IllegalArgumentException("Version does not belong to requested COPY XML file");
        }
        return version;
    }

    private String buildVersionRelativePath(DatasetItemCopyFile copyXmlFile, int versionNumber) {
        if (copyXmlFile.getDatasetItem() == null || copyXmlFile.getDatasetItem().getDataset() == null) {
            throw new IllegalArgumentException("COPY XML file is missing dataset references");
        }

        String workspaceId = sanitize(copyXmlFile.getDatasetItem().getDataset().getWorkspaceId());
        String datasetId = sanitize(copyXmlFile.getDatasetItem().getDataset().getId());
        String itemId = sanitize(copyXmlFile.getDatasetItem().getId());
        String copyFileId = sanitize(copyXmlFile.getId());

        return Paths.get("ws", workspaceId, "ds", datasetId, "items", itemId, "xml", "versions", copyFileId,
                        versionNumber + ".xml")
                .toString()
                .replace('\\', '/');
    }

    private Path resolvePath(String relativePath) {
        return uploadPathService.resolve(relativePath);
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String buildDisplayName(UserDto user, String fallbackUserId) {
        if (user == null) {
            return fallbackUserId;
        }
        if (user.firstName() != null && user.lastName() != null) {
            return user.firstName() + " " + user.lastName();
        }
        if (user.firstName() != null) {
            return user.firstName();
        }
        if (user.lastName() != null) {
            return user.lastName();
        }
        if (user.username() != null && !user.username().isBlank()) {
            return user.username();
        }
        return fallbackUserId;
    }

    private void scheduleWorkspaceUsageRefresh(DatasetItemCopyFile copyXmlFile) {
        if (copyXmlFile == null || copyXmlFile.getDatasetItem() == null || copyXmlFile.getDatasetItem().getDataset() == null) {
            return;
        }

        String workspaceId = copyXmlFile.getDatasetItem().getDataset().getWorkspaceId();
        if (workspaceId != null && !workspaceId.isBlank()) {
            workspaceQuotaRefreshService.scheduleUsageRefresh(workspaceId);
        }
    }
}

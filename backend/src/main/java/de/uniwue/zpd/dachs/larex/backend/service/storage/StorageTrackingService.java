package de.uniwue.zpd.dachs.larex.backend.service.storage;

import de.uniwue.zpd.dachs.larex.backend.config.StorageProperties;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceStorageQuotaService;
import java.io.File;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service responsible for tracking storage usage across workspaces.
 * Automatically updates storage quotas when files are added, modified, or deleted.
 */
@Service
public class StorageTrackingService {

    private static final Logger logger = LoggerFactory.getLogger(StorageTrackingService.class);

    private final WorkspaceStorageQuotaService quotaService;
    private final WorkspaceQuotaRefreshService quotaRefreshService;
    private final StorageProperties storageProperties;

    public StorageTrackingService(
            WorkspaceStorageQuotaService quotaService,
            WorkspaceQuotaRefreshService quotaRefreshService,
            StorageProperties storageProperties) {
        this.quotaService = quotaService;
        this.quotaRefreshService = quotaRefreshService;
        this.storageProperties = storageProperties;
    }

    /**
     * Check if file upload would exceed quota before processing
     */
    public boolean canUploadFiles(String workspaceId, List<MultipartFile> files) {
        if (!storageProperties.isQuotaEnforcementEnabled()) {
            return true;
        }

        long totalSize = files.stream()
                .mapToLong(MultipartFile::getSize)
                .sum();

        return quotaService.hasAvailableSpace(workspaceId, totalSize);
    }

    /**
     * Check if single file upload would exceed quota
     */
    public boolean canUploadFile(String workspaceId, MultipartFile file) {
        if (!storageProperties.isQuotaEnforcementEnabled()) {
            return true;
        }

        return quotaService.hasAvailableSpace(workspaceId, file.getSize());
    }

    /**
     * Check if file of specified size would exceed quota
     */
    public boolean canUploadFileSize(String workspaceId, long fileSize) {
        if (!storageProperties.isQuotaEnforcementEnabled()) {
            return true;
        }

        return quotaService.hasAvailableSpace(workspaceId, fileSize);
    }

    /**
     * Track file addition - call this after successful file upload
     */
    @Transactional
    public void trackFileAdded(String workspaceId, MultipartFile file) {
        if (storageProperties.isQuotaEnforcementEnabled() && file.getSize() > 0) {
            quotaRefreshService.scheduleUsageRefresh(workspaceId);
            logger.debug("Scheduled quota refresh after file addition in workspace {}", workspaceId);
        }
    }

    /**
     * Track file addition by size
     */
    @Transactional
    public void trackFileAdded(String workspaceId, long fileSize) {
        if (storageProperties.isQuotaEnforcementEnabled() && fileSize > 0) {
            quotaRefreshService.scheduleUsageRefresh(workspaceId);
            logger.debug("Scheduled quota refresh after file addition in workspace {}", workspaceId);
        }
    }

    /**
     * Track file removal - call this when file is deleted
     */
    @Transactional
    public void trackFileRemoved(String workspaceId, long fileSize) {
        if (storageProperties.isQuotaEnforcementEnabled() && fileSize > 0) {
            quotaRefreshService.scheduleUsageRefresh(workspaceId);
            logger.debug("Scheduled quota refresh after file removal in workspace {}", workspaceId);
        }
    }

    /**
     * Track file removal using PageImage entity
     */
    @Transactional
    public void trackFileRemoved(String workspaceId, PageImage pageImage) {
        if (pageImage.getFileSize() != null) {
            trackFileRemoved(workspaceId, pageImage.getFileSize());
        }
    }

    /**
     * Track multiple files added
     */
    @Transactional
    public void trackFilesAdded(String workspaceId, List<MultipartFile> files) {
        long totalSize = files.stream()
                .mapToLong(MultipartFile::getSize)
                .sum();
        
        if (storageProperties.isQuotaEnforcementEnabled() && totalSize > 0) {
            quotaRefreshService.scheduleUsageRefresh(workspaceId);
            logger.debug("Scheduled quota refresh after {} added file(s) in workspace {}", files.size(), workspaceId);
        }
    }

    /**
     * Track multiple files removed
     */
    @Transactional
    public void trackFilesRemoved(String workspaceId, List<PageImage> pageImages) {
        long totalSize = pageImages.stream()
                .filter(img -> img.getFileSize() != null)
                .mapToLong(PageImage::getFileSize)
                .sum();
        
        if (storageProperties.isQuotaEnforcementEnabled() && totalSize > 0) {
            quotaRefreshService.scheduleUsageRefresh(workspaceId);
            logger.debug("Scheduled quota refresh after {} removed file(s) in workspace {}", pageImages.size(), workspaceId);
        }
    }

    /**
     * Calculate actual file size from file path (for verification)
     */
    public long getActualFileSize(String filePath) {
        try {
            File file = new File(filePath);
            return file.exists() ? file.length() : 0L;
        } catch (Exception e) {
            logger.warn("Could not determine file size for path: {}", filePath, e);
            return 0L;
        }
    }

    /**
     * Validate that stored file size matches actual file size
     */
    public boolean validateFileSize(PageImage pageImage) {
        if (pageImage.getFilePath() == null || pageImage.getFileSize() == null) {
            return false;
        }
        
        long actualSize = getActualFileSize(pageImage.getFilePath());
        return actualSize == pageImage.getFileSize();
    }

    /**
     * Get quota enforcement status
     */
    public boolean isQuotaEnforcementEnabled() {
        return storageProperties.isQuotaEnforcementEnabled();
    }

    /**
     * Recalculate workspace usage and sync with actual files
     */
    @Transactional
    public void syncWorkspaceUsage(String workspaceId) {
        quotaRefreshService.scheduleUsageRefresh(workspaceId);
        logger.info("Scheduled storage usage synchronization for workspace {}", workspaceId);
    }
}

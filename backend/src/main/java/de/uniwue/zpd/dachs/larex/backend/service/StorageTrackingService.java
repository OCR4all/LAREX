package de.uniwue.zpd.dachs.larex.backend.service;

import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.List;

/**
 * Service responsible for tracking storage usage across workspaces.
 * Automatically updates storage quotas when files are added, modified, or deleted.
 */
@Service
public class StorageTrackingService {

    private static final Logger logger = LoggerFactory.getLogger(StorageTrackingService.class);

    private final WorkspaceStorageQuotaService quotaService;

    @Value("${larex.storage.quota-enforcement-enabled:true}")
    private boolean quotaEnforcementEnabled;

    public StorageTrackingService(WorkspaceStorageQuotaService quotaService) {
        this.quotaService = quotaService;
    }

    /**
     * Check if file upload would exceed quota before processing
     */
    public boolean canUploadFiles(String workspaceId, List<MultipartFile> files) {
        if (!quotaEnforcementEnabled) {
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
        if (!quotaEnforcementEnabled) {
            return true;
        }

        return quotaService.hasAvailableSpace(workspaceId, file.getSize());
    }

    /**
     * Check if file of specified size would exceed quota
     */
    public boolean canUploadFileSize(String workspaceId, long fileSize) {
        if (!quotaEnforcementEnabled) {
            return true;
        }

        return quotaService.hasAvailableSpace(workspaceId, fileSize);
    }

    /**
     * Track file addition - call this after successful file upload
     */
    @Transactional
    public void trackFileAdded(String workspaceId, MultipartFile file) {
        if (quotaEnforcementEnabled && file.getSize() > 0) {
            quotaService.addUsage(workspaceId, file.getSize());
            logger.debug("Tracked file addition: {} bytes added to workspace {}", 
                        file.getSize(), workspaceId);
        }
    }

    /**
     * Track file addition by size
     */
    @Transactional
    public void trackFileAdded(String workspaceId, long fileSize) {
        if (quotaEnforcementEnabled && fileSize > 0) {
            quotaService.addUsage(workspaceId, fileSize);
            logger.debug("Tracked file addition: {} bytes added to workspace {}", 
                        fileSize, workspaceId);
        }
    }

    /**
     * Track file removal - call this when file is deleted
     */
    @Transactional
    public void trackFileRemoved(String workspaceId, long fileSize) {
        if (quotaEnforcementEnabled && fileSize > 0) {
            quotaService.subtractUsage(workspaceId, fileSize);
            logger.debug("Tracked file removal: {} bytes removed from workspace {}", 
                        fileSize, workspaceId);
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
        
        if (quotaEnforcementEnabled && totalSize > 0) {
            quotaService.addUsage(workspaceId, totalSize);
            logger.debug("Tracked {} files addition: {} bytes added to workspace {}", 
                        files.size(), totalSize, workspaceId);
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
        
        if (quotaEnforcementEnabled && totalSize > 0) {
            quotaService.subtractUsage(workspaceId, totalSize);
            logger.debug("Tracked {} files removal: {} bytes removed from workspace {}", 
                        pageImages.size(), totalSize, workspaceId);
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
        return quotaEnforcementEnabled;
    }

    /**
     * Recalculate workspace usage and sync with actual files
     */
    @Transactional
    public void syncWorkspaceUsage(String workspaceId) {
        quotaService.recalculateUsage(workspaceId);
        logger.info("Synchronized storage usage for workspace {}", workspaceId);
    }
}

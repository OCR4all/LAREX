package de.uniwue.zpd.dachs.larex.backend.service.workspace;

import de.uniwue.zpd.dachs.larex.backend.entity.WorkspaceStorageQuota;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceStorageQuotaRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageImageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlVersionRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.PersonalWorkspaceRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.TeamWorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service for managing workspace storage quotas.
 * Handles quota creation, updates, enforcement, and usage tracking.
 */
@Service
public class WorkspaceStorageQuotaService {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceStorageQuotaService.class);

    private final WorkspaceStorageQuotaRepository quotaRepository;
    private final PageImageRepository pageImageRepository;
    private final PageXmlRepository pageXmlRepository;
    private final PageXmlVersionRepository pageXmlVersionRepository;
    private final PersonalWorkspaceRepository personalWorkspaceRepository;
    private final TeamWorkspaceRepository teamWorkspaceRepository;

    // Default quota in bytes (configurable via environment variable)
    @Value("${larex.storage.default-quota-bytes:1073741824}") // Default: 1GB
    private Long defaultQuotaBytes;

    public WorkspaceStorageQuotaService(
            WorkspaceStorageQuotaRepository quotaRepository,
            PageImageRepository pageImageRepository,
            PageXmlRepository pageXmlRepository,
            PageXmlVersionRepository pageXmlVersionRepository,
            PersonalWorkspaceRepository personalWorkspaceRepository,
            TeamWorkspaceRepository teamWorkspaceRepository) {
        this.quotaRepository = quotaRepository;
        this.pageImageRepository = pageImageRepository;
        this.pageXmlRepository = pageXmlRepository;
        this.pageXmlVersionRepository = pageXmlVersionRepository;
        this.personalWorkspaceRepository = personalWorkspaceRepository;
        this.teamWorkspaceRepository = teamWorkspaceRepository;
    }

    /**
     * Get or create storage quota for a workspace
     */
    @Transactional
    public WorkspaceStorageQuota getOrCreateQuota(String workspaceId) {
        Optional<WorkspaceStorageQuota> existingQuota = quotaRepository.findByWorkspaceId(workspaceId);
        
        if (existingQuota.isPresent()) {
            WorkspaceStorageQuota quota = existingQuota.get();
            Long currentUsageBytes = quota.getCurrentUsageBytes();
            if (currentUsageBytes == null || currentUsageBytes == 0L) {
                Long actualUsageBytes = calculateWorkspaceUsage(workspaceId);
                if (actualUsageBytes > 0) {
                    quota.setCurrentUsageBytes(actualUsageBytes);
                    WorkspaceStorageQuota saved = quotaRepository.save(quota);
                    logger.info("Synced storage usage for workspace {}: {}MB",
                            workspaceId, bytesToMB(actualUsageBytes));
                    return saved;
                }
            }
            return quota;
        }

        // Create new quota with default values
        WorkspaceStorageQuota newQuota = new WorkspaceStorageQuota(workspaceId, defaultQuotaBytes);
        
        // Calculate current usage for existing workspace
        Long currentUsage = calculateWorkspaceUsage(workspaceId);
        newQuota.setCurrentUsageBytes(currentUsage);
        
        WorkspaceStorageQuota saved = quotaRepository.save(newQuota);
        logger.info("Created new storage quota for workspace {}: limit={}MB, current={}MB", 
                   workspaceId, bytesToMB(saved.getQuotaLimitBytes()), bytesToMB(currentUsage));
        
        return saved;
    }

    /**
     * Update storage quota limit for a workspace
     */
    @Transactional
    public WorkspaceStorageQuota updateQuotaLimit(String workspaceId, Long newLimitBytes, boolean isCustom) {
        WorkspaceStorageQuota quota = getOrCreateQuota(workspaceId);
        quota.setQuotaLimitBytes(newLimitBytes);
        quota.setIsCustom(isCustom);
        
        WorkspaceStorageQuota saved = quotaRepository.save(quota);
        logger.info("Updated quota limit for workspace {}: {}MB (custom: {})", 
                   workspaceId, bytesToMB(newLimitBytes), isCustom);
        
        return saved;
    }

    /**
     * Add storage usage to a workspace
     */
    @Transactional
    public void addUsage(String workspaceId, Long bytes) {
        if (bytes <= 0) return;
        
        WorkspaceStorageQuota quota = getOrCreateQuota(workspaceId);
        quota.addUsage(bytes);
        quotaRepository.save(quota);
        
        logger.debug("Added {}MB storage usage to workspace {}, new total: {}MB", 
                    bytesToMB(bytes), workspaceId, bytesToMB(quota.getCurrentUsageBytes()));
    }

    /**
     * Subtract storage usage from a workspace
     */
    @Transactional
    public void subtractUsage(String workspaceId, Long bytes) {
        if (bytes <= 0) return;
        
        WorkspaceStorageQuota quota = getOrCreateQuota(workspaceId);
        quota.subtractUsage(bytes);
        quotaRepository.save(quota);
        
        logger.debug("Subtracted {}MB storage usage from workspace {}, new total: {}MB", 
                    bytesToMB(bytes), workspaceId, bytesToMB(quota.getCurrentUsageBytes()));
    }

    /**
     * Check if workspace has available space for the required bytes
     */
    public boolean hasAvailableSpace(String workspaceId, Long requiredBytes) {
        WorkspaceStorageQuota quota = getOrCreateQuota(workspaceId);
        return quota.hasAvailableSpace(requiredBytes);
    }

    /**
     * Get storage quota by workspace ID
     */
    public Optional<WorkspaceStorageQuota> getQuotaByWorkspaceId(String workspaceId) {
        return quotaRepository.findByWorkspaceId(workspaceId);
    }

    /**
     * Get all storage quotas (create quotas for existing workspaces if they don't exist)
     */
    @Transactional
    public List<WorkspaceStorageQuota> getAllQuotas() {
        // First, ensure quotas exist for all workspaces
        initializeQuotasForAllWorkspaces();
        
        return quotaRepository.findAll();
    }

    /**
     * Initialize quotas for all existing workspaces that don't have quotas yet
     */
    @Transactional
    public void initializeQuotasForAllWorkspaces() {
        Set<String> existingQuotaWorkspaceIds = new HashSet<>(quotaRepository.findAllWorkspaceIds());

        // Get all personal workspace IDs
        personalWorkspaceRepository.findAll().forEach(workspace -> {
            if (!existingQuotaWorkspaceIds.contains(workspace.getId())) {
                getOrCreateQuota(workspace.getId());
                existingQuotaWorkspaceIds.add(workspace.getId());
            }
        });

        // Get all team workspace IDs  
        teamWorkspaceRepository.findAll().forEach(workspace -> {
            if (!existingQuotaWorkspaceIds.contains(workspace.getId())) {
                getOrCreateQuota(workspace.getId());
                existingQuotaWorkspaceIds.add(workspace.getId());
            }
        });
    }

    /**
     * Get quotas that exceed their limits
     */
    public List<WorkspaceStorageQuota> getExceededQuotas() {
        return quotaRepository.findQuotasExceeded();
    }

    /**
     * Get quotas with usage above specified percentage
     */
    public List<WorkspaceStorageQuota> getQuotasAbovePercentage(Double percentage) {
        return quotaRepository.findQuotasAbovePercentage(percentage);
    }

    /**
     * Recalculate and sync storage usage for a workspace
     */
    @Transactional
    public WorkspaceStorageQuota recalculateUsage(String workspaceId) {
        WorkspaceStorageQuota quota = getOrCreateQuota(workspaceId);
        Long actualUsage = calculateWorkspaceUsage(workspaceId);
        
        quota.setCurrentUsageBytes(actualUsage);
        WorkspaceStorageQuota saved = quotaRepository.save(quota);
        
        logger.info("Recalculated storage usage for workspace {}: {}MB", 
                   workspaceId, bytesToMB(actualUsage));
        
        return saved;
    }

    /**
     * Reset all quotas to default values (except custom ones)
     */
    @Transactional
    public void resetToDefaults() {
        List<WorkspaceStorageQuota> nonCustomQuotas = quotaRepository.findByIsCustomTrue();
        
        for (WorkspaceStorageQuota quota : nonCustomQuotas) {
            if (!quota.getIsCustom()) {
                quota.setQuotaLimitBytes(defaultQuotaBytes);
                quotaRepository.save(quota);
            }
        }
        
        logger.info("Reset {} quotas to default limit: {}MB", 
                   nonCustomQuotas.size(), bytesToMB(defaultQuotaBytes));
    }

    /**
     * Delete quota for a workspace
     */
    @Transactional
    public void deleteQuota(String workspaceId) {
        quotaRepository.deleteByWorkspaceId(workspaceId);
        logger.info("Deleted storage quota for workspace {}", workspaceId);
    }

    /**
     * Calculate actual storage usage for a workspace by summing all file sizes
     * This method assumes workspace -> library -> project -> page -> pageImage hierarchy
     */
    private Long calculateWorkspaceUsage(String workspaceId) {
        Long imageBytes = pageImageRepository.sumFileSizeByWorkspaceId(workspaceId);
        Long xmlBytes = pageXmlRepository.sumFileSizeByWorkspaceId(workspaceId);
        Long versionBytes = pageXmlVersionRepository.sumFileSizeByWorkspaceId(workspaceId);
        if (imageBytes == null) imageBytes = 0L;
        if (xmlBytes == null) xmlBytes = 0L;
        if (versionBytes == null) versionBytes = 0L;
        return imageBytes + xmlBytes + versionBytes;
    }

    /**
     * Convert bytes to megabytes for logging
     */
    private double bytesToMB(Long bytes) {
        return bytes / 1024.0 / 1024.0;
    }

    /**
     * Get default quota in bytes
     */
    public Long getDefaultQuotaBytes() {
        return defaultQuotaBytes;
    }

    /**
     * Check if a file upload would exceed quota
     */
    public boolean wouldExceedQuota(String workspaceId, Long fileSize) {
        return !hasAvailableSpace(workspaceId, fileSize);
    }

    /**
     * Get quota information for a workspace
     */
    public QuotaInfo getQuotaInfo(String workspaceId) {
        WorkspaceStorageQuota quota = getOrCreateQuota(workspaceId);
        long remainingBytes = quota.getQuotaLimitBytes() - quota.getCurrentUsageBytes();
        if (remainingBytes < 0) remainingBytes = 0;

        return new QuotaInfo(
                quota.getQuotaLimitBytes(),
                quota.getCurrentUsageBytes(),
                remainingBytes,
                quota.getUsagePercentage()
        );
    }

    /**
     * Record for quota information
     */
    public record QuotaInfo(
            long limitBytes,
            long usedBytes,
            long remainingBytes,
            double usagePercentage
    ) {}
}

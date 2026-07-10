package de.uniwue.zpd.dachs.larex.backend.service.storage;

import de.uniwue.zpd.dachs.larex.backend.config.StorageProperties;
import de.uniwue.zpd.dachs.larex.backend.entity.WorkspaceStorageQuota;
import de.uniwue.zpd.dachs.larex.backend.exception.StorageQuotaExceededException;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceStorageQuotaService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class WorkspaceQuotaGuardService {

    private final WorkspaceStorageQuotaService quotaService;
    private final StorageProperties storageProperties;

    public WorkspaceQuotaGuardService(WorkspaceStorageQuotaService quotaService,
                                      StorageProperties storageProperties) {
        this.quotaService = quotaService;
        this.storageProperties = storageProperties;
    }

    public long reserveBytesOrThrow(String workspaceId, long requiredBytes, String blockedOperation) {
        if (!storageProperties.isQuotaEnforcementEnabled() || requiredBytes <= 0) {
            return 0L;
        }

        boolean reserved = quotaService.reserveBytes(workspaceId, requiredBytes);
        if (reserved) {
            return requiredBytes;
        }

        throwQuotaExceeded(workspaceId, blockedOperation, requiredBytes);
        return 0L;
    }

    public void releaseReservation(String workspaceId, long reservedBytes) {
        if (!storageProperties.isQuotaEnforcementEnabled() || reservedBytes <= 0) {
            return;
        }
        quotaService.releaseReservedBytes(workspaceId, reservedBytes);
    }

    public void syncUsageAndReleaseReservation(String workspaceId, long reservedBytes) {
        if (!storageProperties.isQuotaEnforcementEnabled() || reservedBytes <= 0) {
            return;
        }

        quotaService.syncUsageAndReleaseReservation(workspaceId, reservedBytes);
    }

    public boolean isQuotaEnforcementEnabled() {
        return storageProperties.isQuotaEnforcementEnabled();
    }

    public long getAvailableBytes(String workspaceId) {
        if (!storageProperties.isQuotaEnforcementEnabled()) {
            return Long.MAX_VALUE;
        }
        return quotaService.getOrCreateQuota(workspaceId).getAvailableBytes();
    }

    public long totalMultipartBytes(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return 0L;
        }

        return files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .mapToLong(MultipartFile::getSize)
                .sum();
    }

    public void throwQuotaExceeded(String workspaceId, String blockedOperation, long requiredBytes) {
        WorkspaceStorageQuota quota = quotaService.getOrCreateQuota(workspaceId);
        throw new StorageQuotaExceededException(
                workspaceId,
                blockedOperation,
                requiredBytes,
                quota.getAvailableBytes(),
                quota.getQuotaLimitBytes(),
                quota.getCurrentUsageBytes(),
                quota.getReservedBytes(),
                quota.getUsagePercentage()
        );
    }
}

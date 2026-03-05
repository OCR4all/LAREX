package de.uniwue.zpd.dachs.larex.backend.repository.workspace;

import de.uniwue.zpd.dachs.larex.backend.entity.WorkspaceStorageQuota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspaceStorageQuotaRepository extends JpaRepository<WorkspaceStorageQuota, String> {

    /**
     * Find storage quota by workspace ID
     */
    Optional<WorkspaceStorageQuota> findByWorkspaceId(String workspaceId);

    /**
     * Find all custom quotas (non-default)
     */
    List<WorkspaceStorageQuota> findByIsCustomTrue();

    /**
     * Find all quotas that exceed their limit
     */
    @Query("SELECT q FROM WorkspaceStorageQuota q WHERE q.currentUsageBytes > q.quotaLimitBytes")
    List<WorkspaceStorageQuota> findQuotasExceeded();

    /**
     * Find quotas with usage above a certain percentage
     */
    @Query("SELECT q FROM WorkspaceStorageQuota q WHERE (q.currentUsageBytes * 100.0 / q.quotaLimitBytes) >= :percentage")
    List<WorkspaceStorageQuota> findQuotasAbovePercentage(@Param("percentage") Double percentage);

    /**
     * Update usage for a workspace (atomic operation)
     */
    @Modifying
    @Transactional
    @Query("UPDATE WorkspaceStorageQuota q SET q.currentUsageBytes = q.currentUsageBytes + :deltaBytes WHERE q.workspaceId = :workspaceId")
    int updateUsage(@Param("workspaceId") String workspaceId, @Param("deltaBytes") Long deltaBytes);

    /**
     * Reset usage for a workspace to a specific value
     */
    @Modifying
    @Transactional
    @Query("UPDATE WorkspaceStorageQuota q SET q.currentUsageBytes = :usageBytes WHERE q.workspaceId = :workspaceId")
    int resetUsage(@Param("workspaceId") String workspaceId, @Param("usageBytes") Long usageBytes);

    /**
     * Check if workspace exists in quotas
     */
    boolean existsByWorkspaceId(String workspaceId);

    /**
     * Delete quota by workspace ID
     */
    void deleteByWorkspaceId(String workspaceId);

    /**
     * Calculate total usage across all workspaces
     */
    @Query("SELECT COALESCE(SUM(q.currentUsageBytes), 0) FROM WorkspaceStorageQuota q")
    Long getTotalUsageBytes();

    @Query("SELECT q.workspaceId FROM WorkspaceStorageQuota q")
    List<String> findAllWorkspaceIds();

    /**
     * Get workspace IDs that have available space for the specified bytes
     */
    @Query("SELECT q.workspaceId FROM WorkspaceStorageQuota q WHERE (q.quotaLimitBytes - q.currentUsageBytes) >= :requiredBytes")
    List<String> findWorkspacesWithAvailableSpace(@Param("requiredBytes") Long requiredBytes);
}

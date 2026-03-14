package de.uniwue.zpd.dachs.larex.backend.entity;

import de.uniwue.zpd.dachs.larex.backend.entity.workspace.AbstractWorkspace;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Entity representing storage quota information for a workspace.
 * Tracks both the quota limit and current usage.
 */
@Entity
@Table(name = "workspace_storage_quotas", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"workspace_id"}, name = "uk_workspace_storage_quota")
})
@EntityListeners(AuditingEntityListener.class)
public class WorkspaceStorageQuota {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "workspace_id", nullable = false)
    private String workspaceId;

    /**
     * Storage quota limit in bytes
     */
    @Column(nullable = false)
    private Long quotaLimitBytes;

    /**
     * Current storage usage in bytes
     */
    @Column(nullable = false)
    private Long currentUsageBytes = 0L;

    /**
     * Bytes reserved for in-flight ingest operations.
     */
    @Column(nullable = false)
    private Long reservedBytes = 0L;

    /**
     * Whether this quota is using the system default or has been customized
     */
    @Column(nullable = false)
    private Boolean isCustom = false;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updated;

    // Constructors
    public WorkspaceStorageQuota() {}

    public WorkspaceStorageQuota(String workspaceId, Long quotaLimitBytes) {
        this.workspaceId = workspaceId;
        this.quotaLimitBytes = quotaLimitBytes;
        this.currentUsageBytes = 0L;
        this.isCustom = false;
    }

    public WorkspaceStorageQuota(String workspaceId, Long quotaLimitBytes, Boolean isCustom) {
        this.workspaceId = workspaceId;
        this.quotaLimitBytes = quotaLimitBytes;
        this.currentUsageBytes = 0L;
        this.isCustom = isCustom;
    }

    // Business methods
    public boolean isQuotaExceeded() {
        return currentUsageBytes > quotaLimitBytes;
    }

    public boolean hasAvailableSpace(Long requiredBytes) {
        return (currentUsageBytes + reservedBytes + requiredBytes) <= quotaLimitBytes;
    }

    public Long getAvailableBytes() {
        return Math.max(0L, quotaLimitBytes - currentUsageBytes - reservedBytes);
    }

    public Double getUsagePercentage() {
        if (quotaLimitBytes == 0) return 0.0;
        return (currentUsageBytes.doubleValue() / quotaLimitBytes.doubleValue()) * 100.0;
    }

    public void addUsage(Long bytes) {
        this.currentUsageBytes += bytes;
    }

    public void subtractUsage(Long bytes) {
        this.currentUsageBytes = Math.max(0L, this.currentUsageBytes - bytes);
    }

    public void addReservedBytes(Long bytes) {
        this.reservedBytes += bytes;
    }

    public void releaseReservedBytes(Long bytes) {
        this.reservedBytes = Math.max(0L, this.reservedBytes - bytes);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public Long getQuotaLimitBytes() {
        return quotaLimitBytes;
    }

    public void setQuotaLimitBytes(Long quotaLimitBytes) {
        this.quotaLimitBytes = quotaLimitBytes;
    }

    public Long getCurrentUsageBytes() {
        return currentUsageBytes;
    }

    public void setCurrentUsageBytes(Long currentUsageBytes) {
        this.currentUsageBytes = currentUsageBytes;
    }

    public Long getReservedBytes() {
        return reservedBytes;
    }

    public void setReservedBytes(Long reservedBytes) {
        this.reservedBytes = reservedBytes;
    }

    public Boolean getIsCustom() {
        return isCustom;
    }

    public void setIsCustom(Boolean isCustom) {
        this.isCustom = isCustom;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public void setCreated(LocalDateTime created) {
        this.created = created;
    }

    public LocalDateTime getUpdated() {
        return updated;
    }

    public void setUpdated(LocalDateTime updated) {
        this.updated = updated;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WorkspaceStorageQuota)) return false;
        WorkspaceStorageQuota that = (WorkspaceStorageQuota) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "WorkspaceStorageQuota{" +
                "id='" + id + '\'' +
                ", workspaceId='" + workspaceId + '\'' +
                ", quotaLimitBytes=" + quotaLimitBytes +
                ", currentUsageBytes=" + currentUsageBytes +
                ", reservedBytes=" + reservedBytes +
                ", isCustom=" + isCustom +
                '}';
    }
}

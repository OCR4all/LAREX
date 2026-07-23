package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "action_outputs")
@EntityListeners(AuditingEntityListener.class)
public class ActionOutput {

    public enum Status {
        DRAFT,
        READY,
        DELETING
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "workspace_id", nullable = false)
    private String workspaceId;

    @Column(name = "source_run_id", nullable = false, unique = true)
    private String sourceRunId;

    @Column(name = "processor_definition_id", nullable = false)
    private String processorDefinitionId;

    @Column(name = "processor_key", nullable = false, length = 128)
    private String processorKey;

    @Column(name = "processor_name", nullable = false)
    private String processorName;

    @Column(name = "created_by_user_id", nullable = false)
    private String createdByUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status = Status.DRAFT;

    @Column(name = "retention_days")
    private Integer retentionDays;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "total_size_bytes", nullable = false)
    private long totalSizeBytes;

    @Column(name = "file_count", nullable = false)
    private int fileCount;

    @Column(name = "share_public_id", length = 64, unique = true)
    private String sharePublicId;

    @Column(name = "share_secret_hash", length = 64)
    private String shareSecretHash;

    @Column(name = "share_secret_prefix", length = 16)
    private String shareSecretPrefix;

    @Column(name = "share_created_by_user_id")
    private String shareCreatedByUserId;

    @Column(name = "share_created_at")
    private LocalDateTime shareCreatedAt;

    @Column(name = "share_expires_at")
    private LocalDateTime shareExpiresAt;

    @Column(name = "share_revoked_at")
    private LocalDateTime shareRevokedAt;

    @Column(name = "share_last_used_at")
    private LocalDateTime shareLastUsedAt;

    @Column(name = "share_download_count", nullable = false)
    private long shareDownloadCount;

    @OneToMany(mappedBy = "output", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("created ASC, id ASC")
    private List<ActionOutputFile> files = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updated;

    public String getId() { return id; }
    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }
    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
    public String getSourceRunId() { return sourceRunId; }
    public void setSourceRunId(String sourceRunId) { this.sourceRunId = sourceRunId; }
    public String getProcessorDefinitionId() { return processorDefinitionId; }
    public void setProcessorDefinitionId(String processorDefinitionId) { this.processorDefinitionId = processorDefinitionId; }
    public String getProcessorKey() { return processorKey; }
    public void setProcessorKey(String processorKey) { this.processorKey = processorKey; }
    public String getProcessorName() { return processorName; }
    public void setProcessorName(String processorName) { this.processorName = processorName; }
    public String getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(String createdByUserId) { this.createdByUserId = createdByUserId; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Integer getRetentionDays() { return retentionDays; }
    public void setRetentionDays(Integer retentionDays) { this.retentionDays = retentionDays; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public long getTotalSizeBytes() { return totalSizeBytes; }
    public void setTotalSizeBytes(long totalSizeBytes) { this.totalSizeBytes = totalSizeBytes; }
    public int getFileCount() { return fileCount; }
    public void setFileCount(int fileCount) { this.fileCount = fileCount; }
    public String getSharePublicId() { return sharePublicId; }
    public void setSharePublicId(String sharePublicId) { this.sharePublicId = sharePublicId; }
    public String getShareSecretHash() { return shareSecretHash; }
    public void setShareSecretHash(String shareSecretHash) { this.shareSecretHash = shareSecretHash; }
    public String getShareSecretPrefix() { return shareSecretPrefix; }
    public void setShareSecretPrefix(String shareSecretPrefix) { this.shareSecretPrefix = shareSecretPrefix; }
    public String getShareCreatedByUserId() { return shareCreatedByUserId; }
    public void setShareCreatedByUserId(String shareCreatedByUserId) { this.shareCreatedByUserId = shareCreatedByUserId; }
    public LocalDateTime getShareCreatedAt() { return shareCreatedAt; }
    public void setShareCreatedAt(LocalDateTime shareCreatedAt) { this.shareCreatedAt = shareCreatedAt; }
    public LocalDateTime getShareExpiresAt() { return shareExpiresAt; }
    public void setShareExpiresAt(LocalDateTime shareExpiresAt) { this.shareExpiresAt = shareExpiresAt; }
    public LocalDateTime getShareRevokedAt() { return shareRevokedAt; }
    public void setShareRevokedAt(LocalDateTime shareRevokedAt) { this.shareRevokedAt = shareRevokedAt; }
    public LocalDateTime getShareLastUsedAt() { return shareLastUsedAt; }
    public void setShareLastUsedAt(LocalDateTime shareLastUsedAt) { this.shareLastUsedAt = shareLastUsedAt; }
    public long getShareDownloadCount() { return shareDownloadCount; }
    public void setShareDownloadCount(long shareDownloadCount) { this.shareDownloadCount = shareDownloadCount; }
    public List<ActionOutputFile> getFiles() { return files; }
    public LocalDateTime getCreated() { return created; }
    public LocalDateTime getUpdated() { return updated; }
}

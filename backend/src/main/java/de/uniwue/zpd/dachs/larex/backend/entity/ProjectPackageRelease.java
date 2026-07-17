package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "project_package_releases", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"project_id", "version_number"}, name = "uk_project_package_release_version_number"),
        @UniqueConstraint(columnNames = {"project_id", "version_tag"}, name = "uk_project_package_release_version_tag")
})
@EntityListeners(AuditingEntityListener.class)
public class ProjectPackageRelease {

    public enum Status {
        CREATING,
        READY,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne
    @JoinColumn(name = "project_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Project project;

    @Column(nullable = false, name = "version_number")
    private Integer versionNumber;

    @Column(nullable = false, name = "version_tag", length = 128)
    private String versionTag;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false, name = "created_by_user_id")
    private String createdByUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status = Status.CREATING;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(nullable = false, name = "page_count")
    private Long pageCount = 0L;

    @Column(name = "target_page_xml_version", length = 32)
    private String targetPageXmlVersion;

    @Column(nullable = false, name = "include_xml_history")
    private boolean includeXmlHistory = true;

    @Column(name = "embedded_outputs_json", columnDefinition = "TEXT")
    private String embeddedOutputsJson;

    @Column(name = "source_project_updated_at")
    private LocalDateTime sourceProjectUpdatedAt;

    @Column(name = "package_file_name")
    private String packageFileName;

    @Column(name = "package_file_path")
    private String packageFilePath;

    @Column(name = "package_file_size")
    private Long packageFileSize;

    @Column(name = "package_checksum_sha256", length = 64)
    private String packageChecksumSha256;

    @Column(name = "manifest_checksum_sha256", length = 64)
    private String manifestChecksumSha256;

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

    @Column(name = "share_download_count")
    private Long shareDownloadCount = 0L;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updated;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }

    public String getVersionTag() {
        return versionTag;
    }

    public void setVersionTag(String versionTag) {
        this.versionTag = versionTag;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(String createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Long getPageCount() {
        return pageCount;
    }

    public void setPageCount(Long pageCount) {
        this.pageCount = pageCount;
    }

    public String getTargetPageXmlVersion() {
        return targetPageXmlVersion;
    }

    public void setTargetPageXmlVersion(String targetPageXmlVersion) {
        this.targetPageXmlVersion = targetPageXmlVersion;
    }

    public boolean isIncludeXmlHistory() {
        return includeXmlHistory;
    }

    public void setIncludeXmlHistory(boolean includeXmlHistory) {
        this.includeXmlHistory = includeXmlHistory;
    }

    public String getEmbeddedOutputsJson() {
        return embeddedOutputsJson;
    }

    public void setEmbeddedOutputsJson(String embeddedOutputsJson) {
        this.embeddedOutputsJson = embeddedOutputsJson;
    }

    public LocalDateTime getSourceProjectUpdatedAt() {
        return sourceProjectUpdatedAt;
    }

    public void setSourceProjectUpdatedAt(LocalDateTime sourceProjectUpdatedAt) {
        this.sourceProjectUpdatedAt = sourceProjectUpdatedAt;
    }

    public String getPackageFileName() {
        return packageFileName;
    }

    public void setPackageFileName(String packageFileName) {
        this.packageFileName = packageFileName;
    }

    public String getPackageFilePath() {
        return packageFilePath;
    }

    public void setPackageFilePath(String packageFilePath) {
        this.packageFilePath = packageFilePath;
    }

    public Long getPackageFileSize() {
        return packageFileSize;
    }

    public void setPackageFileSize(Long packageFileSize) {
        this.packageFileSize = packageFileSize;
    }

    public String getPackageChecksumSha256() {
        return packageChecksumSha256;
    }

    public void setPackageChecksumSha256(String packageChecksumSha256) {
        this.packageChecksumSha256 = packageChecksumSha256;
    }

    public String getManifestChecksumSha256() {
        return manifestChecksumSha256;
    }

    public void setManifestChecksumSha256(String manifestChecksumSha256) {
        this.manifestChecksumSha256 = manifestChecksumSha256;
    }

    public String getSharePublicId() {
        return sharePublicId;
    }

    public void setSharePublicId(String sharePublicId) {
        this.sharePublicId = sharePublicId;
    }

    public String getShareSecretHash() {
        return shareSecretHash;
    }

    public void setShareSecretHash(String shareSecretHash) {
        this.shareSecretHash = shareSecretHash;
    }

    public String getShareSecretPrefix() {
        return shareSecretPrefix;
    }

    public void setShareSecretPrefix(String shareSecretPrefix) {
        this.shareSecretPrefix = shareSecretPrefix;
    }

    public String getShareCreatedByUserId() {
        return shareCreatedByUserId;
    }

    public void setShareCreatedByUserId(String shareCreatedByUserId) {
        this.shareCreatedByUserId = shareCreatedByUserId;
    }

    public LocalDateTime getShareCreatedAt() {
        return shareCreatedAt;
    }

    public void setShareCreatedAt(LocalDateTime shareCreatedAt) {
        this.shareCreatedAt = shareCreatedAt;
    }

    public LocalDateTime getShareExpiresAt() {
        return shareExpiresAt;
    }

    public void setShareExpiresAt(LocalDateTime shareExpiresAt) {
        this.shareExpiresAt = shareExpiresAt;
    }

    public LocalDateTime getShareRevokedAt() {
        return shareRevokedAt;
    }

    public void setShareRevokedAt(LocalDateTime shareRevokedAt) {
        this.shareRevokedAt = shareRevokedAt;
    }

    public LocalDateTime getShareLastUsedAt() {
        return shareLastUsedAt;
    }

    public void setShareLastUsedAt(LocalDateTime shareLastUsedAt) {
        this.shareLastUsedAt = shareLastUsedAt;
    }

    public Long getShareDownloadCount() {
        return shareDownloadCount;
    }

    public void setShareDownloadCount(Long shareDownloadCount) {
        this.shareDownloadCount = shareDownloadCount;
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
}

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
@Table(name = "dataset_releases", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"dataset_id", "version_number"}, name = "uk_dataset_release_version_number"),
        @UniqueConstraint(columnNames = {"dataset_id", "version_tag"}, name = "uk_dataset_release_version_tag")
})
@EntityListeners(AuditingEntityListener.class)
public class DatasetRelease {

    public enum Status {
        CREATING,
        READY,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne
    @JoinColumn(name = "dataset_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Dataset dataset;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "validation_status", length = 32)
    private Dataset.ValidationStatus validationStatus = Dataset.ValidationStatus.NOT_VALIDATED;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(nullable = false, name = "item_count")
    private Long itemCount = 0L;

    @Column(name = "source_dataset_updated_at")
    private LocalDateTime sourceDatasetUpdatedAt;

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

    @Column(name = "manifest_json", columnDefinition = "TEXT")
    private String manifestJson;

    @Column(name = "stats_json", columnDefinition = "TEXT")
    private String statsJson;

    @Column(name = "warnings_json", columnDefinition = "TEXT")
    private String warningsJson;

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

    public Dataset getDataset() {
        return dataset;
    }

    public void setDataset(Dataset dataset) {
        this.dataset = dataset;
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

    public Dataset.ValidationStatus getValidationStatus() {
        return validationStatus;
    }

    public void setValidationStatus(Dataset.ValidationStatus validationStatus) {
        this.validationStatus = validationStatus;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Long getItemCount() {
        return itemCount;
    }

    public void setItemCount(Long itemCount) {
        this.itemCount = itemCount;
    }

    public LocalDateTime getSourceDatasetUpdatedAt() {
        return sourceDatasetUpdatedAt;
    }

    public void setSourceDatasetUpdatedAt(LocalDateTime sourceDatasetUpdatedAt) {
        this.sourceDatasetUpdatedAt = sourceDatasetUpdatedAt;
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

    public String getManifestJson() {
        return manifestJson;
    }

    public void setManifestJson(String manifestJson) {
        this.manifestJson = manifestJson;
    }

    public String getStatsJson() {
        return statsJson;
    }

    public void setStatsJson(String statsJson) {
        this.statsJson = statsJson;
    }

    public String getWarningsJson() {
        return warningsJson;
    }

    public void setWarningsJson(String warningsJson) {
        this.warningsJson = warningsJson;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public LocalDateTime getUpdated() {
        return updated;
    }
}

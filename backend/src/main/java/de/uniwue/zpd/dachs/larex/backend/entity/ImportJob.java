package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "import_jobs")
@EntityListeners(AuditingEntityListener.class)
public class ImportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, name = "project_id")
    private String projectId;

    @Column(nullable = false, name = "workspace_id")
    private String workspaceId;

    @Column(nullable = false, name = "created_by_user_id")
    private String createdByUserId;

    @Column(nullable = false, name = "source_path")
    private String sourcePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImportJobStatus status;

    @Column(nullable = false, name = "total_files")
    private int totalFiles = 0;

    @Column(nullable = false, name = "processed_files")
    private int processedFiles = 0;

    @Column(nullable = false, name = "skipped_files")
    private int skippedFiles = 0;

    @Column(nullable = false, name = "failed_files")
    private int failedFiles = 0;

    @Column(nullable = false, name = "total_bytes")
    private long totalBytes = 0;

    @Column(nullable = false, name = "reserved_bytes")
    private long reservedBytes = 0;

    @Column(nullable = false, name = "quota_reservation_released")
    private boolean quotaReservationReleased = false;

    @Column(columnDefinition = "TEXT", name = "scan_results")
    private String scanResults;

    @Column(columnDefinition = "TEXT", name = "validation_errors")
    private String validationErrors;

    @Column(columnDefinition = "TEXT", name = "import_log")
    private String importLog;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "copy_mode")
    private CopyMode copyMode = CopyMode.COPY;

    @Column(nullable = false, name = "overwrite_existing")
    private boolean overwriteExisting = false;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updated;

    private LocalDateTime completedAt;

    public enum ImportJobStatus {
        PENDING,
        SCANNING,
        VALIDATING,
        IMPORTING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    public enum CopyMode {
        COPY
    }

    public ImportJob() {}

    public ImportJob(String projectId, String workspaceId, String createdByUserId, String sourcePath) {
        this.projectId = projectId;
        this.workspaceId = workspaceId;
        this.createdByUserId = createdByUserId;
        this.sourcePath = sourcePath;
        this.status = ImportJobStatus.PENDING;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(String createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public ImportJobStatus getStatus() {
        return status;
    }

    public void setStatus(ImportJobStatus status) {
        this.status = status;
    }

    public int getTotalFiles() {
        return totalFiles;
    }

    public void setTotalFiles(int totalFiles) {
        this.totalFiles = totalFiles;
    }

    public int getProcessedFiles() {
        return processedFiles;
    }

    public void setProcessedFiles(int processedFiles) {
        this.processedFiles = processedFiles;
    }

    public int getSkippedFiles() {
        return skippedFiles;
    }

    public void setSkippedFiles(int skippedFiles) {
        this.skippedFiles = skippedFiles;
    }

    public int getFailedFiles() {
        return failedFiles;
    }

    public void setFailedFiles(int failedFiles) {
        this.failedFiles = failedFiles;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public void setTotalBytes(long totalBytes) {
        this.totalBytes = totalBytes;
    }

    public long getReservedBytes() {
        return reservedBytes;
    }

    public void setReservedBytes(long reservedBytes) {
        this.reservedBytes = reservedBytes;
    }

    public boolean isQuotaReservationReleased() {
        return quotaReservationReleased;
    }

    public void setQuotaReservationReleased(boolean quotaReservationReleased) {
        this.quotaReservationReleased = quotaReservationReleased;
    }

    public String getScanResults() {
        return scanResults;
    }

    public void setScanResults(String scanResults) {
        this.scanResults = scanResults;
    }

    public String getValidationErrors() {
        return validationErrors;
    }

    public void setValidationErrors(String validationErrors) {
        this.validationErrors = validationErrors;
    }

    public String getImportLog() {
        return importLog;
    }

    public void setImportLog(String importLog) {
        this.importLog = importLog;
    }

    public CopyMode getCopyMode() {
        return copyMode;
    }

    public void setCopyMode(CopyMode copyMode) {
        this.copyMode = copyMode;
    }

    public boolean isOverwriteExisting() {
        return overwriteExisting;
    }

    public void setOverwriteExisting(boolean overwriteExisting) {
        this.overwriteExisting = overwriteExisting;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public void incrementProcessedFiles() {
        this.processedFiles++;
    }

    public void incrementSkippedFiles() {
        this.skippedFiles++;
    }

    public void incrementFailedFiles() {
        this.failedFiles++;
    }

    public int getProgressPercent() {
        if (totalFiles == 0) return 0;
        return (int) ((processedFiles * 100) / totalFiles);
    }

    public void appendToLog(String message) {
        if (this.importLog == null) {
            this.importLog = message;
        } else {
            this.importLog += "\n" + message;
        }
    }
}

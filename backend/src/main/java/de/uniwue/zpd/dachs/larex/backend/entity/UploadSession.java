package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "upload_sessions")
@EntityListeners(AuditingEntityListener.class)
public class UploadSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, name = "project_id")
    private String projectId;

    @Column(nullable = false, name = "workspace_id")
    private String workspaceId;

    @Column(nullable = false, name = "user_id")
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UploadSessionStatus status;

    @Column(nullable = false, name = "total_files")
    private int totalFiles;

    @Column(nullable = false, name = "processed_files")
    private int processedFiles = 0;

    @Column(nullable = false, name = "failed_files")
    private int failedFiles = 0;

    @Column(nullable = false, name = "total_bytes")
    private long totalBytes;

    @Column(nullable = false, name = "processed_bytes")
    private long processedBytes = 0;

    @Column(nullable = false, name = "reserved_bytes")
    private long reservedBytes = 0;

    @Column(nullable = false, name = "quota_reservation_released")
    private boolean quotaReservationReleased = false;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<UploadSessionFile> files = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updated;

    private LocalDateTime completedAt;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    public enum UploadSessionStatus {
        PENDING,
        UPLOADING,
        PROCESSING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    public UploadSession() {}

    public UploadSession(String projectId, String workspaceId, String userId, int totalFiles, long totalBytes) {
        this.projectId = projectId;
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.totalFiles = totalFiles;
        this.totalBytes = totalBytes;
        this.status = UploadSessionStatus.PENDING;
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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public UploadSessionStatus getStatus() {
        return status;
    }

    public void setStatus(UploadSessionStatus status) {
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

    public long getProcessedBytes() {
        return processedBytes;
    }

    public void setProcessedBytes(long processedBytes) {
        this.processedBytes = processedBytes;
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

    public List<UploadSessionFile> getFiles() {
        return files;
    }

    public void setFiles(List<UploadSessionFile> files) {
        this.files = files;
    }

    public void addFile(UploadSessionFile file) {
        files.add(file);
        file.setSession(this);
    }

    public void removeFile(UploadSessionFile file) {
        files.remove(file);
        file.setSession(null);
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

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public void incrementProcessedFiles() {
        this.processedFiles++;
    }

    public void incrementFailedFiles() {
        this.failedFiles++;
    }

    public void addProcessedBytes(long bytes) {
        this.processedBytes += bytes;
    }

    public int getProgressPercent() {
        if (totalBytes == 0) return 0;
        return (int) ((processedBytes * 100) / totalBytes);
    }
}

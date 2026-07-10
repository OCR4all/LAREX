package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "iiif_import_jobs")
@EntityListeners(AuditingEntityListener.class)
public class IiifImportJob {

    public enum SourceType {
        MANIFEST_URL,
        MANIFEST_FILE
    }

    public enum Status {
        PENDING,
        IMPORTING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, name = "project_id")
    private String projectId;

    @Column(nullable = false, name = "workspace_id")
    private String workspaceId;

    @Column(nullable = false, name = "created_by_user_id")
    private String createdByUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "source_type")
    private SourceType sourceType;

    @Column(nullable = false, name = "source_reference", columnDefinition = "TEXT")
    private String sourceReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(nullable = false, name = "total_canvases")
    private int totalCanvases = 0;

    @Column(nullable = false, name = "processed_canvases")
    private int processedCanvases = 0;

    @Column(nullable = false, name = "skipped_canvases")
    private int skippedCanvases = 0;

    @Column(nullable = false, name = "failed_canvases")
    private int failedCanvases = 0;

    @Column(nullable = false, name = "estimated_storage_bytes")
    private long estimatedStorageBytes = 0L;

    @Column(nullable = false, name = "reserved_bytes")
    private long reservedBytes = 0L;

    @Column(nullable = false, name = "quota_reservation_released")
    private boolean quotaReservationReleased = false;

    @Column(name = "manifest_summary_json", columnDefinition = "TEXT")
    private String manifestSummaryJson;

    @Column(name = "warnings_json", columnDefinition = "TEXT")
    private String warningsJson;

    @Column(name = "canvas_payload_json", columnDefinition = "TEXT")
    private String canvasPayloadJson;

    @Column(name = "results_json", columnDefinition = "TEXT")
    private String resultsJson;

    @Column(name = "import_log", columnDefinition = "TEXT")
    private String importLog;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updated;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(nullable = false)
    private boolean dismissed = false;

    @Column(name = "lease_owner")
    private String leaseOwner;

    @Column(name = "lease_expires_at")
    private LocalDateTime leaseExpiresAt;

    @Column(name = "last_heartbeat_at")
    private LocalDateTime lastHeartbeatAt;

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

    public SourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(SourceType sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceReference() {
        return sourceReference;
    }

    public void setSourceReference(String sourceReference) {
        this.sourceReference = sourceReference;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public int getTotalCanvases() {
        return totalCanvases;
    }

    public void setTotalCanvases(int totalCanvases) {
        this.totalCanvases = totalCanvases;
    }

    public int getProcessedCanvases() {
        return processedCanvases;
    }

    public void setProcessedCanvases(int processedCanvases) {
        this.processedCanvases = processedCanvases;
    }

    public int getSkippedCanvases() {
        return skippedCanvases;
    }

    public void setSkippedCanvases(int skippedCanvases) {
        this.skippedCanvases = skippedCanvases;
    }

    public int getFailedCanvases() {
        return failedCanvases;
    }

    public void setFailedCanvases(int failedCanvases) {
        this.failedCanvases = failedCanvases;
    }

    public long getEstimatedStorageBytes() {
        return estimatedStorageBytes;
    }

    public void setEstimatedStorageBytes(long estimatedStorageBytes) {
        this.estimatedStorageBytes = estimatedStorageBytes;
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

    public String getManifestSummaryJson() {
        return manifestSummaryJson;
    }

    public void setManifestSummaryJson(String manifestSummaryJson) {
        this.manifestSummaryJson = manifestSummaryJson;
    }

    public String getWarningsJson() {
        return warningsJson;
    }

    public void setWarningsJson(String warningsJson) {
        this.warningsJson = warningsJson;
    }

    public String getCanvasPayloadJson() {
        return canvasPayloadJson;
    }

    public void setCanvasPayloadJson(String canvasPayloadJson) {
        this.canvasPayloadJson = canvasPayloadJson;
    }

    public String getResultsJson() {
        return resultsJson;
    }

    public void setResultsJson(String resultsJson) {
        this.resultsJson = resultsJson;
    }

    public String getImportLog() {
        return importLog;
    }

    public void setImportLog(String importLog) {
        this.importLog = importLog;
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

    public boolean isDismissed() {
        return dismissed;
    }

    public void setDismissed(boolean dismissed) {
        this.dismissed = dismissed;
    }

    public String getLeaseOwner() {
        return leaseOwner;
    }

    public void setLeaseOwner(String leaseOwner) {
        this.leaseOwner = leaseOwner;
    }

    public LocalDateTime getLeaseExpiresAt() {
        return leaseExpiresAt;
    }

    public void setLeaseExpiresAt(LocalDateTime leaseExpiresAt) {
        this.leaseExpiresAt = leaseExpiresAt;
    }

    public LocalDateTime getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(LocalDateTime lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
    }

    public void clearLease() {
        leaseOwner = null;
        leaseExpiresAt = null;
        lastHeartbeatAt = null;
    }

    public int getProgressPercent() {
        if (totalCanvases <= 0) {
            return 0;
        }
        return (int) (((long) (processedCanvases + skippedCanvases + failedCanvases) * 100) / totalCanvases);
    }

    public void appendToLog(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        if (importLog == null || importLog.isBlank()) {
            importLog = message;
        } else {
            importLog = importLog + "\n" + message;
        }
    }
}

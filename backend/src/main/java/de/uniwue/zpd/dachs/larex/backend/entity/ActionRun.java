package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "action_runs", indexes = {
        @Index(name = "idx_action_runs_project_status", columnList = "project_id,status"),
        @Index(name = "idx_action_runs_workspace_status", columnList = "workspace_id,status")
})
@EntityListeners(AuditingEntityListener.class)
public class ActionRun {

    public enum Status {
        PENDING,
        DISPATCHING,
        RUNNING,
        IMPORTING_RESULTS,
        COMPLETED,
        FAILED,
        CANCEL_REQUESTED,
        CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processor_definition_id", nullable = false)
    private ActionProcessorDefinition processorDefinition;

    @Column(nullable = false, name = "workspace_id")
    private String workspaceId;

    @Column(nullable = false, name = "project_id")
    private String projectId;

    @Column(nullable = false, name = "created_by_user_id")
    private String createdByUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "lock_mode", length = 32)
    private ActionProcessorDefinition.LockMode lockMode = ActionProcessorDefinition.LockMode.PAGES;

    @Column(nullable = false, name = "page_ids_json", columnDefinition = "TEXT")
    private String pageIdsJson;

    @Column(name = "parameters_json", columnDefinition = "TEXT")
    private String parametersJson;

    @Column(nullable = false, name = "secret_hash", length = 64)
    private String secretHash;

    @Column(nullable = false, name = "secret_prefix", length = 16)
    private String secretPrefix;

    @Column(nullable = false, name = "secret_expires_at")
    private LocalDateTime secretExpiresAt;

    @Column(nullable = false, name = "cancel_requested")
    private boolean cancelRequested = false;

    @Column(nullable = false, name = "progress_percent")
    private int progressPercent = 0;

    @Column(name = "status_message", columnDefinition = "TEXT")
    private String statusMessage;

    @Column(name = "result_summary_json", columnDefinition = "TEXT")
    private String resultSummaryJson;

    @Column(name = "log_text", columnDefinition = "TEXT")
    private String logText;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "last_heartbeat_at")
    private LocalDateTime lastHeartbeatAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updated;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ActionProcessorDefinition getProcessorDefinition() {
        return processorDefinition;
    }

    public void setProcessorDefinition(ActionProcessorDefinition processorDefinition) {
        this.processorDefinition = processorDefinition;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
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

    public ActionProcessorDefinition.LockMode getLockMode() {
        return lockMode;
    }

    public void setLockMode(ActionProcessorDefinition.LockMode lockMode) {
        this.lockMode = lockMode;
    }

    public String getPageIdsJson() {
        return pageIdsJson;
    }

    public void setPageIdsJson(String pageIdsJson) {
        this.pageIdsJson = pageIdsJson;
    }

    public String getParametersJson() {
        return parametersJson;
    }

    public void setParametersJson(String parametersJson) {
        this.parametersJson = parametersJson;
    }

    public String getSecretHash() {
        return secretHash;
    }

    public void setSecretHash(String secretHash) {
        this.secretHash = secretHash;
    }

    public String getSecretPrefix() {
        return secretPrefix;
    }

    public void setSecretPrefix(String secretPrefix) {
        this.secretPrefix = secretPrefix;
    }

    public LocalDateTime getSecretExpiresAt() {
        return secretExpiresAt;
    }

    public void setSecretExpiresAt(LocalDateTime secretExpiresAt) {
        this.secretExpiresAt = secretExpiresAt;
    }

    public boolean isCancelRequested() {
        return cancelRequested;
    }

    public void setCancelRequested(boolean cancelRequested) {
        this.cancelRequested = cancelRequested;
    }

    public int getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(int progressPercent) {
        this.progressPercent = progressPercent;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public String getResultSummaryJson() {
        return resultSummaryJson;
    }

    public void setResultSummaryJson(String resultSummaryJson) {
        this.resultSummaryJson = resultSummaryJson;
    }

    public String getLogText() {
        return logText;
    }

    public void setLogText(String logText) {
        this.logText = logText;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(LocalDateTime lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
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
}

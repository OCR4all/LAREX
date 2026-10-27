package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDateTime;

@Entity
@Table(name = "action_workflow_runs")
@EntityListeners(AuditingEntityListener.class)
public class ActionWorkflowRun {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    @Version
    private Long version;
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updated;
    @Column(name = "workspace_id", nullable = false)
    private String workspaceId;
    @Column(name = "project_id", nullable = false)
    private String projectId;
    @Column(name = "workflow_id", nullable = false)
    private String workflowId;
    @Column(name = "workflow_name", nullable = false, length = 120)
    private String workflowName;
    @Column(name = "created_by_user_id", nullable = false)
    private String createdByUserId;
    @Column(name = "status", nullable = false, length = 32)
    private String status = "RUNNING";
    @Column(name = "steps_json", nullable = false, columnDefinition = "TEXT")
    private String stepsJson;
    @Column(name = "page_ids_json", nullable = false, columnDefinition = "TEXT")
    private String pageIdsJson;
    @Column(name = "step_run_ids_json", nullable = false, columnDefinition = "TEXT")
    private String stepRunIdsJson = "[]";
    @Column(name = "public_api_base_url", nullable = false, columnDefinition = "TEXT")
    private String publicApiBaseUrl;
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    public String getId() { return id; }
    public void setId(String value) { id = value; }
    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }
    public LocalDateTime getCreated() { return created; }
    public void setCreated(LocalDateTime value) { created = value; }
    public LocalDateTime getUpdated() { return updated; }
    public void setUpdated(LocalDateTime value) { updated = value; }
    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String value) { workspaceId = value; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String value) { projectId = value; }
    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String value) { workflowId = value; }
    public String getWorkflowName() { return workflowName; }
    public void setWorkflowName(String value) { workflowName = value; }
    public String getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(String value) { createdByUserId = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getStepsJson() { return stepsJson; }
    public void setStepsJson(String value) { stepsJson = value; }
    public String getPageIdsJson() { return pageIdsJson; }
    public void setPageIdsJson(String value) { pageIdsJson = value; }
    public String getStepRunIdsJson() { return stepRunIdsJson; }
    public void setStepRunIdsJson(String value) { stepRunIdsJson = value; }
    public String getPublicApiBaseUrl() { return publicApiBaseUrl; }
    public void setPublicApiBaseUrl(String value) { publicApiBaseUrl = value; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String value) { errorMessage = value; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime value) { completedAt = value; }
}

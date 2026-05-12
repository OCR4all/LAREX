package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "action_audit_events", indexes = {
        @Index(name = "idx_action_audit_events_created", columnList = "created"),
        @Index(name = "idx_action_audit_events_definition", columnList = "processor_definition_id"),
        @Index(name = "idx_action_audit_events_workspace_project", columnList = "workspace_id,project_id")
})
@EntityListeners(AuditingEntityListener.class)
public class ActionAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(nullable = false, length = 32)
    private String outcome;

    @Column(name = "actor_user_id")
    private String actorUserId;

    @Column(name = "processor_definition_id")
    private String processorDefinitionId;

    @Column(name = "run_id")
    private String runId;

    @Column(name = "workspace_id")
    private String workspaceId;

    @Column(name = "project_id")
    private String projectId;

    @Column(name = "details_json", columnDefinition = "TEXT")
    private String detailsJson;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    public String getId() {
        return id;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public String getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(String actorUserId) {
        this.actorUserId = actorUserId;
    }

    public String getProcessorDefinitionId() {
        return processorDefinitionId;
    }

    public void setProcessorDefinitionId(String processorDefinitionId) {
        this.processorDefinitionId = processorDefinitionId;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
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

    public String getDetailsJson() {
        return detailsJson;
    }

    public void setDetailsJson(String detailsJson) {
        this.detailsJson = detailsJson;
    }

    public LocalDateTime getCreated() {
        return created;
    }
}

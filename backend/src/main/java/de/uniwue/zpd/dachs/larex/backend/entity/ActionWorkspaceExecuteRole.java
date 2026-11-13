package de.uniwue.zpd.dachs.larex.backend.entity;

import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition.ExecuteRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "action_workspace_execute_roles", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"processor_definition_id", "workspace_id"}, name = "uk_action_workspace_execute_role")
}, indexes = {
        @Index(name = "idx_action_workspace_execute_role_workspace", columnList = "workspace_id")
})
@EntityListeners(AuditingEntityListener.class)
public class ActionWorkspaceExecuteRole {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, name = "processor_definition_id")
    private String processorDefinitionId;

    @Column(nullable = false, name = "workspace_id")
    private String workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "execute_role", length = 32)
    private ExecuteRole executeRole;

    @Column(nullable = false, name = "created_by_user_id")
    private String createdByUserId;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updated;

    public String getId() {
        return id;
    }

    public String getProcessorDefinitionId() {
        return processorDefinitionId;
    }

    public void setProcessorDefinitionId(String processorDefinitionId) {
        this.processorDefinitionId = processorDefinitionId;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public ExecuteRole getExecuteRole() {
        return executeRole;
    }

    public void setExecuteRole(ExecuteRole executeRole) {
        this.executeRole = executeRole;
    }

    public String getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(String createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public LocalDateTime getUpdated() {
        return updated;
    }
}

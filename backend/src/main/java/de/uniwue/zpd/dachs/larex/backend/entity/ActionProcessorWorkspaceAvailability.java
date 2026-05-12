package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "action_processor_workspace_availability", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"processor_definition_id", "workspace_id"}, name = "uk_action_workspace_availability")
}, indexes = {
        @Index(name = "idx_action_availability_workspace", columnList = "workspace_id"),
        @Index(name = "idx_action_availability_definition", columnList = "processor_definition_id")
})
@EntityListeners(AuditingEntityListener.class)
public class ActionProcessorWorkspaceAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processor_definition_id", nullable = false)
    private ActionProcessorDefinition processorDefinition;

    @Column(nullable = false, name = "workspace_id")
    private String workspaceId;

    @Column(nullable = false)
    private boolean enabled = true;

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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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

package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDateTime;

@Entity
@Table(name = "action_workflows")
@EntityListeners(AuditingEntityListener.class)
public class ActionWorkflow {
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
    @Column(name = "workspace_id")
    private String workspaceId;
    @Column(name = "name", nullable = false, length = 120)
    private String name;
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    @Column(name = "steps_json", nullable = false, columnDefinition = "TEXT")
    private String stepsJson;
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;
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
    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public String getStepsJson() { return stepsJson; }
    public void setStepsJson(String value) { stepsJson = value; }
    public boolean getEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
}

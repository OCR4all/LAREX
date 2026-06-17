package de.uniwue.zpd.dachs.larex.backend.entity;

import tools.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "label_sets", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"workspace_id", "name"}, name = "uk_label_set_workspace_name")
})
@EntityListeners(AuditingEntityListener.class)
public class LabelSet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String workspaceId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ElementCollection
    @CollectionTable(name = "label_set_tags", joinColumns = @JoinColumn(name = "label_set_id"))
    @Column(name = "tag", nullable = false)
    private List<String> tags = new ArrayList<>();

    @Column(nullable = false)
    private boolean isSystem = false;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updated;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "definition", nullable = false, columnDefinition = "jsonb")
    private JsonNode definition;

    public LabelSet() {}

    public LabelSet(String workspaceId, String name, String description, JsonNode definition) {
        this.workspaceId = workspaceId;
        this.name = name;
        this.description = description;
        this.definition = definition;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags != null ? tags : new ArrayList<>();
    }

    public boolean isSystem() {
        return isSystem;
    }

    public void setSystem(boolean system) {
        isSystem = system;
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

    public JsonNode getDefinition() {
        return definition;
    }

    public void setDefinition(JsonNode definition) {
        this.definition = definition;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LabelSet labelSet = (LabelSet) o;
        return Objects.equals(id, labelSet.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
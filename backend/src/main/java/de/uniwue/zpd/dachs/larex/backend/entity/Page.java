package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "pages", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"name", "project_id"}, name = "uk_page_name_project")
})
@EntityListeners(AuditingEntityListener.class)
public class Page {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updated;

    @ElementCollection
    @CollectionTable(name = "page_tags", joinColumns = @JoinColumn(name = "page_id"))
    @Column(name = "tag")
    private List<String> tags = new ArrayList<>();

    @Column(nullable = false)
    private boolean locked = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_state", nullable = false, length = 32)
    private WorkflowState workflowState = WorkflowState.OPEN;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "locked_reason", columnDefinition = "TEXT")
    private String lockedReason;

    @Column(name = "locked_by_action_run_id")
    private String lockedByActionRunId;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "external_source_type", length = 64)
    private String externalSourceType;

    @Column(name = "external_source_id", columnDefinition = "TEXT")
    private String externalSourceId;

    @Column(name = "external_source_url", columnDefinition = "TEXT")
    private String externalSourceUrl;

    @Column(name = "external_source_metadata_json", columnDefinition = "TEXT")
    private String externalSourceMetadataJson;

    @OneToMany(mappedBy = "page", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<PageImage> images = new HashSet<>();

    @OneToMany(mappedBy = "page", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<PageXml> xmlFiles = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    public Page() {}

    public Page(String name, String description, Project project) {
        this.name = name;
        this.description = description;
        this.project = project;
    }

    public enum WorkflowState {
        OPEN,
        IN_PROGRESS,
        DONE
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public WorkflowState getWorkflowState() {
        return workflowState;
    }

    public void setWorkflowState(WorkflowState workflowState) {
        this.workflowState = workflowState == null ? WorkflowState.OPEN : workflowState;
    }

    public boolean isEffectivelyLocked() {
        return (project != null && project.isLocked()) || locked || workflowState == WorkflowState.DONE;
    }

    public String getEffectiveLockedReason() {
        if (project != null && project.isLocked()) {
            return project.getLockedReason() == null ? "Project is locked" : project.getLockedReason();
        }
        if (locked) {
            return lockedReason;
        }
        return workflowState == WorkflowState.DONE ? "Page workflow state is Done" : null;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getLockedReason() {
        return lockedReason;
    }

    public void setLockedReason(String lockedReason) {
        this.lockedReason = lockedReason;
    }

    public String getLockedByActionRunId() {
        return lockedByActionRunId;
    }

    public void setLockedByActionRunId(String lockedByActionRunId) {
        this.lockedByActionRunId = lockedByActionRunId;
    }

    public LocalDateTime getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(LocalDateTime lockedAt) {
        this.lockedAt = lockedAt;
    }

    public String getExternalSourceType() {
        return externalSourceType;
    }

    public void setExternalSourceType(String externalSourceType) {
        this.externalSourceType = externalSourceType;
    }

    public String getExternalSourceId() {
        return externalSourceId;
    }

    public void setExternalSourceId(String externalSourceId) {
        this.externalSourceId = externalSourceId;
    }

    public String getExternalSourceUrl() {
        return externalSourceUrl;
    }

    public void setExternalSourceUrl(String externalSourceUrl) {
        this.externalSourceUrl = externalSourceUrl;
    }

    public String getExternalSourceMetadataJson() {
        return externalSourceMetadataJson;
    }

    public void setExternalSourceMetadataJson(String externalSourceMetadataJson) {
        this.externalSourceMetadataJson = externalSourceMetadataJson;
    }

    public Set<PageImage> getImages() {
        return images;
    }

    public void setImages(Set<PageImage> images) {
        this.images = images;
    }

    public Set<PageXml> getXmlFiles() {
        return xmlFiles;
    }

    public void setXmlFiles(Set<PageXml> xmlFiles) {
        this.xmlFiles = xmlFiles;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }
}

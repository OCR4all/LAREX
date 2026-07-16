package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tasks")
@EntityListeners(AuditingEntityListener.class)
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, name = "created_by_user_id")
    private String createdByUserId;

    @ElementCollection
    @CollectionTable(name = "task_assignees", joinColumns = @JoinColumn(name = "task_id"))
    @Column(name = "user_id")
    private List<String> assignedUserIds = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskPriority priority;

    private LocalDateTime dueDate;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updated;

    private LocalDateTime completedAt;

    @Column(name = "completed_by_user_id")
    private String completedByUserId;

    @Column(name = "workspace_id", nullable = false)
    private String workspaceId;

    @Column(name = "sync_linked_page_states", nullable = false)
    private boolean syncLinkedPageStates = false;

    public enum TaskStatus {
        OPEN,
        IN_PROGRESS,
        COMPLETED,
        CANCELLED
    }

    public enum TaskPriority {
        LOW,
        MEDIUM,
        HIGH,
        URGENT
    }

    public Task() {}

    public Task(String title, String description, String createdByUserId, TaskPriority priority, String workspaceId) {
        this.title = title;
        this.description = description;
        this.createdByUserId = createdByUserId;
        this.priority = priority;
        this.status = TaskStatus.OPEN;
        this.workspaceId = workspaceId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(String createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public List<String> getAssignedUserIds() {
        return assignedUserIds;
    }

    public void setAssignedUserIds(List<String> assignedUserIds) {
        this.assignedUserIds = assignedUserIds;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public void setPriority(TaskPriority priority) {
        this.priority = priority;
    }

    public LocalDateTime getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDateTime dueDate) {
        this.dueDate = dueDate;
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

    public String getCompletedByUserId() {
        return completedByUserId;
    }

    public void setCompletedByUserId(String completedByUserId) {
        this.completedByUserId = completedByUserId;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public boolean isSyncLinkedPageStates() {
        return syncLinkedPageStates;
    }

    public void setSyncLinkedPageStates(boolean syncLinkedPageStates) {
        this.syncLinkedPageStates = syncLinkedPageStates;
    }
}

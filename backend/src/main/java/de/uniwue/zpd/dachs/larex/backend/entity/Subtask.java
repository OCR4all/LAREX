package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "subtasks")
@EntityListeners(AuditingEntityListener.class)
public class Subtask {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, name = "task_id")
    private String taskId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private boolean completed = false;

    @Column(nullable = false, name = "sort_order")
    private int sortOrder = 0;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "completed_by_user_id")
    private String completedByUserId;

    @Column(name = "page_id")
    private String pageId;

    @Column(name = "assigned_user_id")
    private String assignedUserId;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    public Subtask() {}

    public Subtask(String taskId, String title, int sortOrder) {
        this.taskId = taskId;
        this.title = title;
        this.sortOrder = sortOrder;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
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

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
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

    public LocalDateTime getCreated() {
        return created;
    }

    public void setCreated(LocalDateTime created) {
        this.created = created;
    }

    public String getPageId() {
        return pageId;
    }

    public void setPageId(String pageId) {
        this.pageId = pageId;
    }

    public String getAssignedUserId() {
        return assignedUserId;
    }

    public void setAssignedUserId(String assignedUserId) {
        this.assignedUserId = assignedUserId;
    }
}

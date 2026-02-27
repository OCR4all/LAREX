package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "task_activity_logs")
@EntityListeners(AuditingEntityListener.class)
public class TaskActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, name = "task_id")
    private String taskId;

    @Column(nullable = false, name = "user_id")
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivityType activityType;

    @Column(columnDefinition = "TEXT")
    private String oldValue;

    @Column(columnDefinition = "TEXT")
    private String newValue;

    @Column(columnDefinition = "TEXT")
    private String details;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    public enum ActivityType {
        CREATED,
        TITLE_CHANGED,
        DESCRIPTION_CHANGED,
        STATUS_CHANGED,
        PRIORITY_CHANGED,
        DUE_DATE_CHANGED,
        ASSIGNEES_CHANGED,
        COMMENT_ADDED,
        COMMENT_UPDATED,
        COMMENT_DELETED,
        SUBTASK_ADDED,
        SUBTASK_COMPLETED,
        SUBTASK_DELETED,
        LINK_ADDED,
        LINK_REMOVED
    }

    public TaskActivityLog() {}

    public TaskActivityLog(String taskId, String userId, ActivityType activityType) {
        this.taskId = taskId;
        this.userId = userId;
        this.activityType = activityType;
    }

    public TaskActivityLog(String taskId, String userId, ActivityType activityType, String oldValue, String newValue) {
        this.taskId = taskId;
        this.userId = userId;
        this.activityType = activityType;
        this.oldValue = oldValue;
        this.newValue = newValue;
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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public ActivityType getActivityType() {
        return activityType;
    }

    public void setActivityType(ActivityType activityType) {
        this.activityType = activityType;
    }

    public String getOldValue() {
        return oldValue;
    }

    public void setOldValue(String oldValue) {
        this.oldValue = oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public void setNewValue(String newValue) {
        this.newValue = newValue;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public void setCreated(LocalDateTime created) {
        this.created = created;
    }
}

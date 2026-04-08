package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@EntityListeners(AuditingEntityListener.class)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, name = "user_id")
    private String userId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false)
    private boolean read = false;

    private String relatedEntityId;

    private String relatedEntityType;

    @Column(columnDefinition = "TEXT")
    private String link;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updated;

    private LocalDateTime readAt;

    public enum NotificationType {
        WORKSPACE_INVITATION,
        TASK_ASSIGNED,
        TASK_COMPLETED,
        TASK_REMINDER,
        TASK_MENTIONED,
        TASK_UPDATED,
        TASK_DUE_SOON,
        TASK_OVERDUE,
        TASK_COMMENT_ADDED,
        PROJECT_CREATED,
        PROJECT_DELETED,
        PAGE_CREATED,
        PAGE_DELETED,
        WORKSPACE_WATCH,
        PROJECT_WATCH,
        UPLOAD_COMPLETED,
        UPLOAD_FAILED,
        IMPORT_COMPLETED,
        IMPORT_FAILED,
        COLLAB_TAKEOVER_REQUESTED,
        COLLAB_TAKEOVER_GRANTED,
        COLLAB_TAKEOVER_DECLINED,
        COLLAB_TAKEOVER_FORCED,
        COLLAB_LEASE_EXPIRED
    }

    public Notification() {}

    public Notification(String userId, String title, String message, NotificationType type) {
        this.userId = userId;
        this.title = title;
        this.message = message;
        this.type = type;
    }

    public Notification(String userId, String title, String message, NotificationType type, String relatedEntityId, String relatedEntityType) {
        this.userId = userId;
        this.title = title;
        this.message = message;
        this.type = type;
        this.relatedEntityId = relatedEntityId;
        this.relatedEntityType = relatedEntityType;
    }

    public Notification(String userId, String title, String message, NotificationType type, String relatedEntityId, String relatedEntityType, String link) {
        this.userId = userId;
        this.title = title;
        this.message = message;
        this.type = type;
        this.relatedEntityId = relatedEntityId;
        this.relatedEntityType = relatedEntityType;
        this.link = link;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public String getRelatedEntityId() {
        return relatedEntityId;
    }

    public void setRelatedEntityId(String relatedEntityId) {
        this.relatedEntityId = relatedEntityId;
    }

    public String getRelatedEntityType() {
        return relatedEntityType;
    }

    public void setRelatedEntityType(String relatedEntityType) {
        this.relatedEntityType = relatedEntityType;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
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

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public void setReadAt(LocalDateTime readAt) {
        this.readAt = readAt;
    }
}

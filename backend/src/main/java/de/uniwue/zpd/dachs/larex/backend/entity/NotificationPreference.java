package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Entity to store user notification preferences per notification type.
 * Users can enable/disable email and desktop notifications for each type.
 */
@Entity
@Table(name = "notification_preferences", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "notification_type"}))
@EntityListeners(AuditingEntityListener.class)
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, name = "user_id")
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "notification_type")
    private Notification.NotificationType notificationType;

    @Column(nullable = false, name = "email_enabled")
    private boolean emailEnabled = false;

    @Column(nullable = false, name = "desktop_enabled")
    private boolean desktopEnabled = false;

    @Column(nullable = false, name = "in_app_enabled")
    private boolean inAppEnabled = true;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updated;

    public NotificationPreference() {}

    public NotificationPreference(String userId, Notification.NotificationType notificationType) {
        this.userId = userId;
        this.notificationType = notificationType;
    }

    // Getters and Setters
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

    public Notification.NotificationType getNotificationType() {
        return notificationType;
    }

    public void setNotificationType(Notification.NotificationType notificationType) {
        this.notificationType = notificationType;
    }

    public boolean isEmailEnabled() {
        return emailEnabled;
    }

    public void setEmailEnabled(boolean emailEnabled) {
        this.emailEnabled = emailEnabled;
    }

    public boolean isDesktopEnabled() {
        return desktopEnabled;
    }

    public void setDesktopEnabled(boolean desktopEnabled) {
        this.desktopEnabled = desktopEnabled;
    }

    public boolean isInAppEnabled() {
        return inAppEnabled;
    }

    public void setInAppEnabled(boolean inAppEnabled) {
        this.inAppEnabled = inAppEnabled;
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

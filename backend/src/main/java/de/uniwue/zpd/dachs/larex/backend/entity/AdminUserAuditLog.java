package de.uniwue.zpd.dachs.larex.backend.entity;

import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserAuditAction;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserAuditOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_user_audit_logs")
@EntityListeners(AuditingEntityListener.class)
public class AdminUserAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "actor_user_id", nullable = false)
    private String actorUserId;

    @Column(name = "actor_username", nullable = false)
    private String actorUsername;

    @Column(name = "target_user_id")
    private String targetUserId;

    @Column(name = "target_username")
    private String targetUsername;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AdminUserAuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AdminUserAuditOutcome outcome;

    @Column(columnDefinition = "TEXT")
    private String details;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(String actorUserId) {
        this.actorUserId = actorUserId;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public void setActorUsername(String actorUsername) {
        this.actorUsername = actorUsername;
    }

    public String getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(String targetUserId) {
        this.targetUserId = targetUserId;
    }

    public String getTargetUsername() {
        return targetUsername;
    }

    public void setTargetUsername(String targetUsername) {
        this.targetUsername = targetUsername;
    }

    public AdminUserAuditAction getAction() {
        return action;
    }

    public void setAction(AdminUserAuditAction action) {
        this.action = action;
    }

    public AdminUserAuditOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(AdminUserAuditOutcome outcome) {
        this.outcome = outcome;
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

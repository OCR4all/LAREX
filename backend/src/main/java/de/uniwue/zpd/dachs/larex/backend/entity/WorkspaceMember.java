package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "workspace_members", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"workspace_id", "user_id"})
})
@EntityListeners(AuditingEntityListener.class)
public class WorkspaceMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, name = "user_id")
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvitationStatus invitationStatus;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updated;

    @Column(nullable = false, name = "workspace_id")
    private String workspaceId;

    public enum Role {
        CURATOR,
        EDITOR,
        /**
         * Legacy role name kept for backward compatibility with existing rows.
         */
        ADMINISTRATOR,
        /**
         * Legacy role name kept for backward compatibility with existing rows.
         */
        MEMBER;

        public Role toCanonicalRole() {
            return switch (this) {
                case CURATOR, ADMINISTRATOR -> CURATOR;
                case EDITOR, MEMBER -> EDITOR;
            };
        }

        public boolean isCuratorLike() {
            return toCanonicalRole() == CURATOR;
        }

        public boolean isEditorLike() {
            return toCanonicalRole() == EDITOR;
        }

        public static Role fromApiValue(String rawRole) {
            if (rawRole == null || rawRole.isBlank()) {
                throw new IllegalArgumentException("Role must not be blank");
            }

            return switch (rawRole.trim().toUpperCase()) {
                case "CURATOR", "ADMINISTRATOR" -> CURATOR;
                case "EDITOR", "MEMBER" -> EDITOR;
                default -> throw new IllegalArgumentException("Invalid role: " + rawRole + ". Valid roles are: CURATOR, EDITOR");
            };
        }
    }

    public enum InvitationStatus {
        PENDING,
        ACCEPTED,
        DECLINED
    }

    public WorkspaceMember() {}

    public WorkspaceMember(String userId, Role role, InvitationStatus invitationStatus, String workspaceId) {
        this.userId = userId;
        this.role = role;
        this.invitationStatus = invitationStatus;
        this.workspaceId = workspaceId;
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

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public InvitationStatus getInvitationStatus() {
        return invitationStatus;
    }

    public void setInvitationStatus(InvitationStatus invitationStatus) {
        this.invitationStatus = invitationStatus;
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

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }
}

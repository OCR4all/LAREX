package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "recent_projects", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "project_id"}, name = "uk_recent_user_project")
})
@EntityListeners(AuditingEntityListener.class)
public class RecentProject {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Project project;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime lastAccessed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccessType accessType;

    public enum AccessType {
        CREATED,
        VIEWED,
        EDITED
    }

    public RecentProject() {}

    public RecentProject(String userId, Project project, AccessType accessType) {
        this.userId = userId;
        this.project = project;
        this.accessType = accessType;
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

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public void setCreated(LocalDateTime created) {
        this.created = created;
    }

    public LocalDateTime getLastAccessed() {
        return lastAccessed;
    }

    public void setLastAccessed(LocalDateTime lastAccessed) {
        this.lastAccessed = lastAccessed;
    }

    public AccessType getAccessType() {
        return accessType;
    }

    public void setAccessType(AccessType accessType) {
        this.accessType = accessType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RecentProject)) return false;
        RecentProject that = (RecentProject) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "task_project_links", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"task_id", "project_id"})
})
@EntityListeners(AuditingEntityListener.class)
public class TaskProjectLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, name = "task_id")
    private String taskId;

    @Column(nullable = false, name = "project_id")
    private String projectId;

    @Column(name = "created_by_user_id")
    private String createdByUserId;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    public TaskProjectLink() {}

    public TaskProjectLink(String taskId, String projectId, String createdByUserId) {
        this.taskId = taskId;
        this.projectId = projectId;
        this.createdByUserId = createdByUserId;
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

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(String createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public void setCreated(LocalDateTime created) {
        this.created = created;
    }
}

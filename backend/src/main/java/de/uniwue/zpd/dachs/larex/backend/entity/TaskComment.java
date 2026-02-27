package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "task_comments")
@EntityListeners(AuditingEntityListener.class)
public class TaskComment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, name = "task_id")
    private String taskId;

    @Column(nullable = false, name = "user_id")
    private String userId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @ElementCollection
    @CollectionTable(name = "task_comment_mentions", joinColumns = @JoinColumn(name = "comment_id"))
    @Column(name = "user_id")
    private List<String> mentionedUserIds = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updated;

    public TaskComment() {}

    public TaskComment(String taskId, String userId, String content) {
        this.taskId = taskId;
        this.userId = userId;
        this.content = content;
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<String> getMentionedUserIds() {
        return mentionedUserIds;
    }

    public void setMentionedUserIds(List<String> mentionedUserIds) {
        this.mentionedUserIds = mentionedUserIds;
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

package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "task_reminders")
@EntityListeners(AuditingEntityListener.class)
public class TaskReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, name = "task_id")
    private String taskId;

    @Column(nullable = false, name = "user_id")
    private String userId;

    @Column(nullable = false, name = "reminder_time")
    private LocalDateTime reminderTime;

    @Column(nullable = false)
    private boolean sent = false;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    public TaskReminder() {}

    public TaskReminder(String taskId, String userId, LocalDateTime reminderTime) {
        this.taskId = taskId;
        this.userId = userId;
        this.reminderTime = reminderTime;
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

    public LocalDateTime getReminderTime() {
        return reminderTime;
    }

    public void setReminderTime(LocalDateTime reminderTime) {
        this.reminderTime = reminderTime;
    }

    public boolean isSent() {
        return sent;
    }

    public void setSent(boolean sent) {
        this.sent = sent;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public void setCreated(LocalDateTime created) {
        this.created = created;
    }
}

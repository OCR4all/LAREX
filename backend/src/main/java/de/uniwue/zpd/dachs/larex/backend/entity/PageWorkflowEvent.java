package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "page_workflow_events")
@EntityListeners(AuditingEntityListener.class)
public class PageWorkflowEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "page_id", nullable = false)
    private String pageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Action action;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_state", nullable = false, length = 32)
    private Page.WorkflowState fromState;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_state", nullable = false, length = 32)
    private Page.WorkflowState toState;

    @Column(name = "actor_user_id")
    private String actorUserId;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    public enum Action {
        SUBMIT,
        APPROVE,
        REQUEST_CHANGES,
        WITHDRAW,
        MARK_DONE,
        REOPEN,
        ASSIGNMENT_SYNC
    }

    protected PageWorkflowEvent() {}

    public PageWorkflowEvent(String pageId, Action action, Page.WorkflowState fromState, Page.WorkflowState toState,
                             String actorUserId, String comment) {
        this.pageId = pageId;
        this.action = action;
        this.fromState = fromState;
        this.toState = toState;
        this.actorUserId = actorUserId;
        this.comment = comment;
    }

    public String getId() {
        return id;
    }

    public String getPageId() {
        return pageId;
    }

    public Action getAction() {
        return action;
    }

    public Page.WorkflowState getFromState() {
        return fromState;
    }

    public Page.WorkflowState getToState() {
        return toState;
    }

    public String getActorUserId() {
        return actorUserId;
    }

    public String getComment() {
        return comment;
    }

    public LocalDateTime getCreated() {
        return created;
    }
}

package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "task_page_links", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"task_id", "page_id"})
})
@EntityListeners(AuditingEntityListener.class)
public class TaskPageLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, name = "task_id")
    private String taskId;

    @Column(nullable = false, name = "page_id")
    private String pageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LinkType linkType;

    @Column(name = "tag_filter")
    private String tagFilter;

    @Column(name = "created_by_user_id")
    private String createdByUserId;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    public enum LinkType {
        MANUAL,
        BY_TAG
    }

    public TaskPageLink() {}

    public TaskPageLink(String taskId, String pageId, LinkType linkType, String createdByUserId) {
        this.taskId = taskId;
        this.pageId = pageId;
        this.linkType = linkType;
        this.createdByUserId = createdByUserId;
    }

    public TaskPageLink(String taskId, String pageId, LinkType linkType, String tagFilter, String createdByUserId) {
        this.taskId = taskId;
        this.pageId = pageId;
        this.linkType = linkType;
        this.tagFilter = tagFilter;
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

    public String getPageId() {
        return pageId;
    }

    public void setPageId(String pageId) {
        this.pageId = pageId;
    }

    public LinkType getLinkType() {
        return linkType;
    }

    public void setLinkType(LinkType linkType) {
        this.linkType = linkType;
    }

    public String getTagFilter() {
        return tagFilter;
    }

    public void setTagFilter(String tagFilter) {
        this.tagFilter = tagFilter;
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

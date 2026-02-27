package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "project_transfer_requests")
@EntityListeners(AuditingEntityListener.class)
public class ProjectTransferRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, name = "project_id")
    private String projectId;

    @Column(nullable = false, name = "source_workspace_id")
    private String sourceWorkspaceId;

    @Column(nullable = false, name = "target_workspace_id")
    private String targetWorkspaceId;

    @Column(nullable = false, name = "requested_by_user_id")
    private String requestedByUserId;

    @Column(name = "approved_by_user_id")
    private String approvedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(columnDefinition = "TEXT")
    private String rejectionReason;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updated;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", insertable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Project project;

    // Note: Removed direct workspace entity references due to polymorphic workspace design
    // Workspace names are resolved via WorkspaceQueryService when needed

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED,
        COMPLETED,
        CANCELLED
    }

    public enum TransferType {
        MOVE,
        COPY
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferType transferType = TransferType.MOVE;

    public ProjectTransferRequest() {}

    public ProjectTransferRequest(String projectId, String sourceWorkspaceId, String targetWorkspaceId,
                                String requestedByUserId, String message, TransferType transferType) {
        this.projectId = projectId;
        this.sourceWorkspaceId = sourceWorkspaceId;
        this.targetWorkspaceId = targetWorkspaceId;
        this.requestedByUserId = requestedByUserId;
        this.message = message;
        this.transferType = transferType != null ? transferType : TransferType.MOVE;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getSourceWorkspaceId() {
        return sourceWorkspaceId;
    }

    public void setSourceWorkspaceId(String sourceWorkspaceId) {
        this.sourceWorkspaceId = sourceWorkspaceId;
    }

    public String getTargetWorkspaceId() {
        return targetWorkspaceId;
    }

    public void setTargetWorkspaceId(String targetWorkspaceId) {
        this.targetWorkspaceId = targetWorkspaceId;
    }

    public String getRequestedByUserId() {
        return requestedByUserId;
    }

    public void setRequestedByUserId(String requestedByUserId) {
        this.requestedByUserId = requestedByUserId;
    }

    public String getApprovedByUserId() {
        return approvedByUserId;
    }

    public void setApprovedByUserId(String approvedByUserId) {
        this.approvedByUserId = approvedByUserId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
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

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public TransferType getTransferType() {
        return transferType;
    }

    public void setTransferType(TransferType transferType) {
        this.transferType = transferType;
    }

    // Removed sourceWorkspace and targetWorkspace entity getters/setters
    // Use sourceWorkspaceId and targetWorkspaceId with WorkspaceQueryService for names
}

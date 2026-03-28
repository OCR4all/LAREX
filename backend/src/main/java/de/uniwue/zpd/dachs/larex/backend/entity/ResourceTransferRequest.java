package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "resource_transfer_requests")
@EntityListeners(AuditingEntityListener.class)
public class ResourceTransferRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String resourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResourceType resourceType;

    @Column(nullable = false)
    private String sourceWorkspaceId;

    @Column(nullable = false)
    private String targetWorkspaceId;

    @Column(nullable = false)
    private String requestedByUserId;

    private String approvedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferType transferType = TransferType.MOVE;

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

    public enum ResourceType {
        CODEC,
        DICTIONARY,
        VIRTUAL_KEYBOARD,
        LABEL_SET,
        NORMALIZATION_PROFILE,
        VALIDATION_RULESET
    }

    public enum Status {
        PENDING, APPROVED, REJECTED, COMPLETED, CANCELLED
    }

    public enum TransferType {
        MOVE, COPY
    }

    public ResourceTransferRequest() {}

    public ResourceTransferRequest(String resourceId, ResourceType resourceType, String sourceWorkspaceId,
                                   String targetWorkspaceId, String requestedByUserId, String message, TransferType transferType) {
        this.resourceId = resourceId;
        this.resourceType = resourceType;
        this.sourceWorkspaceId = sourceWorkspaceId;
        this.targetWorkspaceId = targetWorkspaceId;
        this.requestedByUserId = requestedByUserId;
        this.message = message;
        this.transferType = transferType != null ? transferType : TransferType.MOVE;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    public ResourceType getResourceType() { return resourceType; }
    public void setResourceType(ResourceType resourceType) { this.resourceType = resourceType; }
    public String getSourceWorkspaceId() { return sourceWorkspaceId; }
    public void setSourceWorkspaceId(String sourceWorkspaceId) { this.sourceWorkspaceId = sourceWorkspaceId; }
    public String getTargetWorkspaceId() { return targetWorkspaceId; }
    public void setTargetWorkspaceId(String targetWorkspaceId) { this.targetWorkspaceId = targetWorkspaceId; }
    public String getRequestedByUserId() { return requestedByUserId; }
    public void setRequestedByUserId(String requestedByUserId) { this.requestedByUserId = requestedByUserId; }
    public String getApprovedByUserId() { return approvedByUserId; }
    public void setApprovedByUserId(String approvedByUserId) { this.approvedByUserId = approvedByUserId; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public TransferType getTransferType() { return transferType; }
    public void setTransferType(TransferType transferType) { this.transferType = transferType; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public LocalDateTime getCreated() { return created; }
    public LocalDateTime getUpdated() { return updated; }
}

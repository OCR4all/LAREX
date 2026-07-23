package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "stored_files", indexes = {
        @Index(name = "idx_stored_files_ws_pr_status", columnList = "workspace_id,project_id,status"),
        @Index(name = "idx_stored_files_storage_path", columnList = "storage_path", unique = true),
        @Index(name = "idx_stored_files_checksum", columnList = "checksum_sha256")
})
@EntityListeners(AuditingEntityListener.class)
public class StoredFile {

    public enum StoredFileType {
        IMG("img"),
        XML("xml"),
        THUMB("thumb"),
        OUTPUT("out");

        private final String folderName;

        StoredFileType(String folderName) {
            this.folderName = folderName;
        }

        public String getFolderName() {
            return folderName;
        }
    }

    public enum StoredFileStatus {
        READY,
        DELETED,
        FAILED
    }

    @Id
    @Column(nullable = false, updatable = false, length = 32)
    private String uuid;

    @Column(nullable = false, name = "workspace_id")
    private String workspaceId;

    @Column(nullable = false, name = "project_id")
    private String projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "file_type", length = 16)
    private StoredFileType fileType;

    @Column(nullable = false, unique = true, name = "storage_path", length = 1024)
    private String storagePath;

    @Column(nullable = false, name = "original_filename", length = 512)
    private String originalFilename;

    @Column(nullable = false, name = "mime_type", length = 128)
    private String mimeType;

    @Column(nullable = false, length = 16)
    private String extension;

    @Column(nullable = false, name = "size_bytes")
    private long sizeBytes;

    @Column(nullable = false, name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @CreatedDate
    @Column(nullable = false, updatable = false, name = "created_at")
    private LocalDateTime createdAt;

    @Column(nullable = false, name = "created_by", length = 255)
    private String createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StoredFileStatus status;

    @LastModifiedDate
    @Column(nullable = false, name = "updated_at")
    private LocalDateTime updatedAt;

    public StoredFile() {
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public StoredFileType getFileType() {
        return fileType;
    }

    public void setFileType(StoredFileType fileType) {
        this.fileType = fileType;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public void setChecksumSha256(String checksumSha256) {
        this.checksumSha256 = checksumSha256;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public StoredFileStatus getStatus() {
        return status;
    }

    public void setStatus(StoredFileStatus status) {
        this.status = status;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

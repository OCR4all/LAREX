package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "upload_session_files")
@EntityListeners(AuditingEntityListener.class)
public class UploadSessionFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private UploadSession session;

    @Column(nullable = false, name = "original_file_name")
    private String originalFileName;

    @Column(name = "temp_file_path")
    private String tempFilePath;

    @Column(nullable = false, name = "file_size")
    private long fileSize;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "base_name")
    private String baseName;

    @Column(name = "variant")
    private String variant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UploadFileStatus status;

    @Column(name = "chunk_count")
    private int chunkCount;

    @Column(name = "chunks_received")
    private int chunksReceived = 0;

    @Column(columnDefinition = "TEXT", name = "error_message")
    private String errorMessage;

    @Column(name = "created_page_id")
    private String createdPageId;

    @Column(name = "created_page_image_id")
    private String createdPageImageId;

    @Column(name = "conflict_type")
    private String conflictType;

    @Column(name = "conflict_resolution")
    private String conflictResolution;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updated;

    public enum UploadFileStatus {
        PENDING,
        UPLOADING,
        UPLOADED,
        PROCESSING,
        COMPLETED,
        FAILED,
        CONFLICT,
        SKIPPED
    }

    public UploadSessionFile() {}

    public UploadSessionFile(String originalFileName, long fileSize, String mimeType, String baseName, String variant, int chunkCount) {
        this.originalFileName = originalFileName;
        this.fileSize = fileSize;
        this.mimeType = mimeType;
        this.baseName = baseName;
        this.variant = variant;
        this.chunkCount = chunkCount;
        this.status = UploadFileStatus.PENDING;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public UploadSession getSession() {
        return session;
    }

    public void setSession(UploadSession session) {
        this.session = session;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public String getTempFilePath() {
        return tempFilePath;
    }

    public void setTempFilePath(String tempFilePath) {
        this.tempFilePath = tempFilePath;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getBaseName() {
        return baseName;
    }

    public void setBaseName(String baseName) {
        this.baseName = baseName;
    }

    public String getVariant() {
        return variant;
    }

    public void setVariant(String variant) {
        this.variant = variant;
    }

    public UploadFileStatus getStatus() {
        return status;
    }

    public void setStatus(UploadFileStatus status) {
        this.status = status;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(int chunkCount) {
        this.chunkCount = chunkCount;
    }

    public int getChunksReceived() {
        return chunksReceived;
    }

    public void setChunksReceived(int chunksReceived) {
        this.chunksReceived = chunksReceived;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getCreatedPageId() {
        return createdPageId;
    }

    public void setCreatedPageId(String createdPageId) {
        this.createdPageId = createdPageId;
    }

    public String getCreatedPageImageId() {
        return createdPageImageId;
    }

    public void setCreatedPageImageId(String createdPageImageId) {
        this.createdPageImageId = createdPageImageId;
    }

    public String getConflictType() {
        return conflictType;
    }

    public void setConflictType(String conflictType) {
        this.conflictType = conflictType;
    }

    public String getConflictResolution() {
        return conflictResolution;
    }

    public void setConflictResolution(String conflictResolution) {
        this.conflictResolution = conflictResolution;
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

    public void incrementChunksReceived() {
        this.chunksReceived++;
    }

    public boolean isFullyUploaded() {
        return chunksReceived >= chunkCount;
    }
}

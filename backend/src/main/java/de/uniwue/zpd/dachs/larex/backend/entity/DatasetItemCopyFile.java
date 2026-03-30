package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "dataset_item_copy_files")
@EntityListeners(AuditingEntityListener.class)
public class DatasetItemCopyFile {

    public enum Kind {
        XML,
        IMAGE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_item_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private DatasetItem datasetItem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Kind kind;

    @Column(nullable = false, name = "source_file_id")
    private String sourceFileId;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String filePath;

    @Column(nullable = false)
    private String mimeType;

    @Column(nullable = false)
    private Long fileSize;

    @Column(nullable = false)
    private String checksumSha256;

    @Column(name = "variant_name")
    private String variant;

    @Column(name = "base_name")
    private String baseName;

    @Column(name = "source_updated_at")
    private LocalDateTime sourceUpdatedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updated;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public DatasetItem getDatasetItem() {
        return datasetItem;
    }

    public void setDatasetItem(DatasetItem datasetItem) {
        this.datasetItem = datasetItem;
    }

    public Kind getKind() {
        return kind;
    }

    public void setKind(Kind kind) {
        this.kind = kind;
    }

    public String getSourceFileId() {
        return sourceFileId;
    }

    public void setSourceFileId(String sourceFileId) {
        this.sourceFileId = sourceFileId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public void setChecksumSha256(String checksumSha256) {
        this.checksumSha256 = checksumSha256;
    }

    public String getVariant() {
        return variant;
    }

    public void setVariant(String variant) {
        this.variant = variant;
    }

    public String getBaseName() {
        return baseName;
    }

    public void setBaseName(String baseName) {
        this.baseName = baseName;
    }

    public LocalDateTime getSourceUpdatedAt() {
        return sourceUpdatedAt;
    }

    public void setSourceUpdatedAt(LocalDateTime sourceUpdatedAt) {
        this.sourceUpdatedAt = sourceUpdatedAt;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public LocalDateTime getUpdated() {
        return updated;
    }
}

package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "dataset_item_copy_xml_versions", indexes = {
        @Index(name = "idx_ds_copy_xml_ver_copy_file_id", columnList = "copy_file_id"),
        @Index(name = "idx_ds_copy_xml_ver_copy_file_id_version", columnList = "copy_file_id, version_number")
})
@EntityListeners(AuditingEntityListener.class)
public class DatasetItemCopyXmlVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "copy_file_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private DatasetItemCopyFile copyFile;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(nullable = false)
    private String filePath;

    private Long fileSize;

    @Column(nullable = false)
    private String userId;

    @Column(length = 500)
    private String comment;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    public DatasetItemCopyXmlVersion() {
    }

    public DatasetItemCopyXmlVersion(DatasetItemCopyFile copyFile,
                                     Integer versionNumber,
                                     String filePath,
                                     Long fileSize,
                                     String userId,
                                     String comment) {
        this.copyFile = copyFile;
        this.versionNumber = versionNumber;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.userId = userId;
        this.comment = comment;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public DatasetItemCopyFile getCopyFile() {
        return copyFile;
    }

    public void setCopyFile(DatasetItemCopyFile copyFile) {
        this.copyFile = copyFile;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public void setCreated(LocalDateTime created) {
        this.created = created;
    }
}

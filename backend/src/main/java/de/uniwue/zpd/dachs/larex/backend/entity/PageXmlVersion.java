package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "page_xml_versions", indexes = {
    @Index(name = "idx_page_xml_version_xml_id", columnList = "page_xml_id"),
    @Index(name = "idx_page_xml_version_xml_id_version", columnList = "page_xml_id, version_number")
})
@EntityListeners(AuditingEntityListener.class)
public class PageXmlVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "page_xml_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PageXml pageXml;

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

    public PageXmlVersion() {}

    public PageXmlVersion(PageXml pageXml, Integer versionNumber, String filePath, Long fileSize, String userId, String comment) {
        this.pageXml = pageXml;
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

    public PageXml getPageXml() {
        return pageXml;
    }

    public void setPageXml(PageXml pageXml) {
        this.pageXml = pageXml;
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

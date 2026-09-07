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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "action_training_inputs")
@EntityListeners(AuditingEntityListener.class)
public class ActionTrainingInput {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ActionRun run;

    @Column(nullable = false, name = "dataset_item_id")
    private String datasetItemId;

    @Column(nullable = false, name = "source_page_id")
    private String sourcePageId;

    @Column(nullable = false, name = "page_name")
    private String pageName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DatasetItem.Split split;

    @Column(nullable = false, name = "image_file_id")
    private String imageFileId;

    @Column(nullable = false, name = "image_file_name")
    private String imageFileName;

    @Column(name = "image_variant")
    private String imageVariant;

    @Column(nullable = false, name = "image_mime_type")
    private String imageMimeType;

    @Column(nullable = false, name = "image_file_size")
    private long imageFileSize;

    @Column(nullable = false, name = "image_checksum_sha256", length = 64)
    private String imageChecksumSha256;

    @Column(nullable = false, name = "image_snapshot_path", columnDefinition = "TEXT")
    private String imageSnapshotPath;

    @Column(nullable = false, name = "xml_file_id")
    private String xmlFileId;

    @Column(nullable = false, name = "xml_file_name")
    private String xmlFileName;

    @Column(nullable = false, name = "xml_mime_type")
    private String xmlMimeType;

    @Column(nullable = false, name = "xml_file_size")
    private long xmlFileSize;

    @Column(nullable = false, name = "xml_checksum_sha256", length = 64)
    private String xmlChecksumSha256;

    @Column(nullable = false, name = "xml_snapshot_path", columnDefinition = "TEXT")
    private String xmlSnapshotPath;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    public String getId() { return id; }
    public ActionRun getRun() { return run; }
    public void setRun(ActionRun run) { this.run = run; }
    public String getDatasetItemId() { return datasetItemId; }
    public void setDatasetItemId(String datasetItemId) { this.datasetItemId = datasetItemId; }
    public String getSourcePageId() { return sourcePageId; }
    public void setSourcePageId(String sourcePageId) { this.sourcePageId = sourcePageId; }
    public String getPageName() { return pageName; }
    public void setPageName(String pageName) { this.pageName = pageName; }
    public DatasetItem.Split getSplit() { return split; }
    public void setSplit(DatasetItem.Split split) { this.split = split; }
    public String getImageFileId() { return imageFileId; }
    public void setImageFileId(String imageFileId) { this.imageFileId = imageFileId; }
    public String getImageFileName() { return imageFileName; }
    public void setImageFileName(String imageFileName) { this.imageFileName = imageFileName; }
    public String getImageVariant() { return imageVariant; }
    public void setImageVariant(String imageVariant) { this.imageVariant = imageVariant; }
    public String getImageMimeType() { return imageMimeType; }
    public void setImageMimeType(String imageMimeType) { this.imageMimeType = imageMimeType; }
    public long getImageFileSize() { return imageFileSize; }
    public void setImageFileSize(long imageFileSize) { this.imageFileSize = imageFileSize; }
    public String getImageChecksumSha256() { return imageChecksumSha256; }
    public void setImageChecksumSha256(String imageChecksumSha256) { this.imageChecksumSha256 = imageChecksumSha256; }
    public String getImageSnapshotPath() { return imageSnapshotPath; }
    public void setImageSnapshotPath(String imageSnapshotPath) { this.imageSnapshotPath = imageSnapshotPath; }
    public String getXmlFileId() { return xmlFileId; }
    public void setXmlFileId(String xmlFileId) { this.xmlFileId = xmlFileId; }
    public String getXmlFileName() { return xmlFileName; }
    public void setXmlFileName(String xmlFileName) { this.xmlFileName = xmlFileName; }
    public String getXmlMimeType() { return xmlMimeType; }
    public void setXmlMimeType(String xmlMimeType) { this.xmlMimeType = xmlMimeType; }
    public long getXmlFileSize() { return xmlFileSize; }
    public void setXmlFileSize(long xmlFileSize) { this.xmlFileSize = xmlFileSize; }
    public String getXmlChecksumSha256() { return xmlChecksumSha256; }
    public void setXmlChecksumSha256(String xmlChecksumSha256) { this.xmlChecksumSha256 = xmlChecksumSha256; }
    public String getXmlSnapshotPath() { return xmlSnapshotPath; }
    public void setXmlSnapshotPath(String xmlSnapshotPath) { this.xmlSnapshotPath = xmlSnapshotPath; }
    public LocalDateTime getCreated() { return created; }
}

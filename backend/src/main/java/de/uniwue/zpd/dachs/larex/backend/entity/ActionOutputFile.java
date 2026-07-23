package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "action_output_files")
@EntityListeners(AuditingEntityListener.class)
public class ActionOutputFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "output_id", nullable = false)
    private ActionOutput output;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stored_file_uuid", nullable = false, unique = true)
    private StoredFile storedFile;

    @Column(name = "page_id")
    private String pageId;

    @Column(name = "file_name", nullable = false, length = 512)
    private String fileName;

    @Column(name = "mime_type", nullable = false, length = 128)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    public String getId() { return id; }
    public ActionOutput getOutput() { return output; }
    public void setOutput(ActionOutput output) { this.output = output; }
    public StoredFile getStoredFile() { return storedFile; }
    public void setStoredFile(StoredFile storedFile) { this.storedFile = storedFile; }
    public String getPageId() { return pageId; }
    public void setPageId(String pageId) { this.pageId = pageId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    public String getChecksumSha256() { return checksumSha256; }
    public void setChecksumSha256(String checksumSha256) { this.checksumSha256 = checksumSha256; }
    public LocalDateTime getCreated() { return created; }
}

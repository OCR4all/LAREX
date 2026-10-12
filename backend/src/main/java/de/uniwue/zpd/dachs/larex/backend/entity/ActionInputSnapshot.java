package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** Immutable per-file input metadata; snapshot bytes are removed when the run ends. */
@Entity
@Table(name = "action_input_snapshots")
public class ActionInputSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ActionRun run;

    @Column(name = "input_id", nullable = false)
    private String inputId;

    @Column(name = "page_name", nullable = false)
    private String pageName;

    @Column(name = "source_page_id", nullable = false)
    private String sourcePageId;

    @Column(name = "split", nullable = true)
    private String split;

    @Column(name = "file_type", nullable = false)
    private String fileType;

    @Column(name = "source_file_id", nullable = false)
    private String sourceFileId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "variant", nullable = true)
    private String variant;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "checksum_sha256", nullable = false)
    private String checksumSha256;

    @Column(name = "snapshot_path", nullable = false, columnDefinition = "TEXT")
    private String snapshotPath;

    @Column(name = "position", nullable = false)
    private int position;

    public String getId() { return id; }
    public ActionRun getRun() { return run; }
    public void setRun(ActionRun run) { this.run = run; }
    public String getInputId() { return inputId; }
    public void setInputId(String inputId) { this.inputId = inputId; }
    public String getPageName() { return pageName; }
    public void setPageName(String pageName) { this.pageName = pageName; }
    public String getSourcePageId() { return sourcePageId; }
    public void setSourcePageId(String sourcePageId) { this.sourcePageId = sourcePageId; }
    public String getSplit() { return split; }
    public void setSplit(String split) { this.split = split; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public String getSourceFileId() { return sourceFileId; }
    public void setSourceFileId(String sourceFileId) { this.sourceFileId = sourceFileId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getVariant() { return variant; }
    public void setVariant(String variant) { this.variant = variant; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public String getChecksumSha256() { return checksumSha256; }
    public void setChecksumSha256(String checksumSha256) { this.checksumSha256 = checksumSha256; }
    public String getSnapshotPath() { return snapshotPath; }
    public void setSnapshotPath(String snapshotPath) { this.snapshotPath = snapshotPath; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
}

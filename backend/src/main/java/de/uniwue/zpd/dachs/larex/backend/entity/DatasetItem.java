package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "dataset_items", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"dataset_id", "source_page_id"}, name = "uk_dataset_item_source_page")
})
@EntityListeners(AuditingEntityListener.class)
public class DatasetItem {

    public enum Mode {
        LINK,
        COPY
    }

    public enum Split {
        TRAIN,
        VAL,
        TEST
    }

    public enum Status {
        READY,
        BROKEN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Dataset dataset;

    @Column(nullable = false, name = "source_project_id")
    private String sourceProjectId;

    @Column(nullable = false, name = "source_page_id")
    private String sourcePageId;

    @Column(nullable = false, name = "source_project_name")
    private String sourceProjectName;

    @Column(nullable = false, name = "source_page_name")
    private String sourcePageName;

    @ElementCollection
    @CollectionTable(name = "dataset_item_tags", joinColumns = @JoinColumn(name = "dataset_item_id"))
    @Column(name = "tag")
    private List<String> sourcePageTags = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Mode mode;

    @Column(nullable = false, name = "selected_source_xml_id")
    private String selectedSourceXmlId;

    @Column(nullable = false, name = "selected_source_xml_file_name")
    private String selectedSourceXmlFileName;

    @Column(name = "selected_source_xml_updated_at")
    private LocalDateTime selectedSourceXmlUpdatedAt;

    @ElementCollection
    @CollectionTable(name = "dataset_item_source_images", joinColumns = @JoinColumn(name = "dataset_item_id"))
    @Column(name = "source_image_id")
    private List<String> selectedSourceImageIds = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "assigned_split", length = 16)
    private Split assignedSplit = Split.TRAIN;

    @Column(nullable = false, name = "manual_split")
    private boolean manualSplit = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.READY;

    @Column(name = "broken_reason", columnDefinition = "TEXT")
    private String brokenReason;

    @Column(name = "source_page_updated_at_snapshot")
    private LocalDateTime sourcePageUpdatedAtSnapshot;

    @Column(name = "copied_at")
    private LocalDateTime copiedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updated;

    @OneToMany(mappedBy = "datasetItem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<DatasetItemCopyFile> copyFiles = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Dataset getDataset() {
        return dataset;
    }

    public void setDataset(Dataset dataset) {
        this.dataset = dataset;
    }

    public String getSourceProjectId() {
        return sourceProjectId;
    }

    public void setSourceProjectId(String sourceProjectId) {
        this.sourceProjectId = sourceProjectId;
    }

    public String getSourcePageId() {
        return sourcePageId;
    }

    public void setSourcePageId(String sourcePageId) {
        this.sourcePageId = sourcePageId;
    }

    public String getSourceProjectName() {
        return sourceProjectName;
    }

    public void setSourceProjectName(String sourceProjectName) {
        this.sourceProjectName = sourceProjectName;
    }

    public String getSourcePageName() {
        return sourcePageName;
    }

    public void setSourcePageName(String sourcePageName) {
        this.sourcePageName = sourcePageName;
    }

    public List<String> getSourcePageTags() {
        return sourcePageTags;
    }

    public void setSourcePageTags(List<String> sourcePageTags) {
        this.sourcePageTags = sourcePageTags;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public String getSelectedSourceXmlId() {
        return selectedSourceXmlId;
    }

    public void setSelectedSourceXmlId(String selectedSourceXmlId) {
        this.selectedSourceXmlId = selectedSourceXmlId;
    }

    public String getSelectedSourceXmlFileName() {
        return selectedSourceXmlFileName;
    }

    public void setSelectedSourceXmlFileName(String selectedSourceXmlFileName) {
        this.selectedSourceXmlFileName = selectedSourceXmlFileName;
    }

    public LocalDateTime getSelectedSourceXmlUpdatedAt() {
        return selectedSourceXmlUpdatedAt;
    }

    public void setSelectedSourceXmlUpdatedAt(LocalDateTime selectedSourceXmlUpdatedAt) {
        this.selectedSourceXmlUpdatedAt = selectedSourceXmlUpdatedAt;
    }

    public List<String> getSelectedSourceImageIds() {
        return selectedSourceImageIds;
    }

    public void setSelectedSourceImageIds(List<String> selectedSourceImageIds) {
        this.selectedSourceImageIds = selectedSourceImageIds;
    }

    public Split getAssignedSplit() {
        return assignedSplit;
    }

    public void setAssignedSplit(Split assignedSplit) {
        this.assignedSplit = assignedSplit;
    }

    public boolean isManualSplit() {
        return manualSplit;
    }

    public void setManualSplit(boolean manualSplit) {
        this.manualSplit = manualSplit;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getBrokenReason() {
        return brokenReason;
    }

    public void setBrokenReason(String brokenReason) {
        this.brokenReason = brokenReason;
    }

    public LocalDateTime getSourcePageUpdatedAtSnapshot() {
        return sourcePageUpdatedAtSnapshot;
    }

    public void setSourcePageUpdatedAtSnapshot(LocalDateTime sourcePageUpdatedAtSnapshot) {
        this.sourcePageUpdatedAtSnapshot = sourcePageUpdatedAtSnapshot;
    }

    public LocalDateTime getCopiedAt() {
        return copiedAt;
    }

    public void setCopiedAt(LocalDateTime copiedAt) {
        this.copiedAt = copiedAt;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public LocalDateTime getUpdated() {
        return updated;
    }

    public List<DatasetItemCopyFile> getCopyFiles() {
        return copyFiles;
    }

    public void setCopyFiles(List<DatasetItemCopyFile> copyFiles) {
        this.copyFiles = copyFiles;
    }
}

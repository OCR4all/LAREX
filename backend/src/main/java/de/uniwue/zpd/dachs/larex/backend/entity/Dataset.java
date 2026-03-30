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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "datasets", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"workspace_id", "name"}, name = "uk_dataset_workspace_name")
})
@EntityListeners(AuditingEntityListener.class)
public class Dataset {

    public enum SplitTemplate {
        TRAIN_VAL,
        TRAIN_VAL_TEST
    }

    public enum SplitAlgorithm {
        RANDOM_SEEDED,
        GROUP_BY_SOURCE_PROJECT,
        MULTILABEL_STRATIFIED_BY_TAGS
    }

    public enum ValidationStatus {
        NOT_VALIDATED,
        VALID,
        INVALID
    }

    public enum ExportStatus {
        NEVER_EXPORTED,
        READY,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, name = "workspace_id")
    private String workspaceId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ElementCollection
    @CollectionTable(name = "dataset_tags", joinColumns = @JoinColumn(name = "dataset_id"))
    @Column(name = "tag")
    private List<String> tags = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "split_template", length = 32)
    private SplitTemplate splitTemplate = SplitTemplate.TRAIN_VAL_TEST;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "split_algorithm", length = 64)
    private SplitAlgorithm splitAlgorithm = SplitAlgorithm.RANDOM_SEEDED;

    @Column(nullable = false, name = "split_seed")
    private Long splitSeed = 42L;

    @Column(nullable = false, name = "train_percentage")
    private Integer trainPercentage = 70;

    @Column(nullable = false, name = "val_percentage")
    private Integer valPercentage = 15;

    @Column(nullable = false, name = "test_percentage")
    private Integer testPercentage = 15;

    @ElementCollection
    @CollectionTable(name = "dataset_stratify_tags", joinColumns = @JoinColumn(name = "dataset_id"))
    @Column(name = "tag_id")
    private List<String> stratifyTagIds = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "last_validation_status", length = 32)
    private ValidationStatus lastValidationStatus = ValidationStatus.NOT_VALIDATED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "last_export_status", length = 32)
    private ExportStatus lastExportStatus = ExportStatus.NEVER_EXPORTED;

    @Column(name = "last_validation_at")
    private LocalDateTime lastValidationAt;

    @Column(name = "last_exported_at")
    private LocalDateTime lastExportedAt;

    @Column(name = "last_validation_warnings_json", columnDefinition = "TEXT")
    private String lastValidationWarningsJson;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updated;

    @OneToMany(mappedBy = "dataset", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<DatasetItem> items = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public SplitTemplate getSplitTemplate() {
        return splitTemplate;
    }

    public void setSplitTemplate(SplitTemplate splitTemplate) {
        this.splitTemplate = splitTemplate;
    }

    public SplitAlgorithm getSplitAlgorithm() {
        return splitAlgorithm;
    }

    public void setSplitAlgorithm(SplitAlgorithm splitAlgorithm) {
        this.splitAlgorithm = splitAlgorithm;
    }

    public Long getSplitSeed() {
        return splitSeed;
    }

    public void setSplitSeed(Long splitSeed) {
        this.splitSeed = splitSeed;
    }

    public Integer getTrainPercentage() {
        return trainPercentage;
    }

    public void setTrainPercentage(Integer trainPercentage) {
        this.trainPercentage = trainPercentage;
    }

    public Integer getValPercentage() {
        return valPercentage;
    }

    public void setValPercentage(Integer valPercentage) {
        this.valPercentage = valPercentage;
    }

    public Integer getTestPercentage() {
        return testPercentage;
    }

    public void setTestPercentage(Integer testPercentage) {
        this.testPercentage = testPercentage;
    }

    public List<String> getStratifyTagIds() {
        return stratifyTagIds;
    }

    public void setStratifyTagIds(List<String> stratifyTagIds) {
        this.stratifyTagIds = stratifyTagIds;
    }

    public ValidationStatus getLastValidationStatus() {
        return lastValidationStatus;
    }

    public void setLastValidationStatus(ValidationStatus lastValidationStatus) {
        this.lastValidationStatus = lastValidationStatus;
    }

    public ExportStatus getLastExportStatus() {
        return lastExportStatus;
    }

    public void setLastExportStatus(ExportStatus lastExportStatus) {
        this.lastExportStatus = lastExportStatus;
    }

    public LocalDateTime getLastValidationAt() {
        return lastValidationAt;
    }

    public void setLastValidationAt(LocalDateTime lastValidationAt) {
        this.lastValidationAt = lastValidationAt;
    }

    public LocalDateTime getLastExportedAt() {
        return lastExportedAt;
    }

    public void setLastExportedAt(LocalDateTime lastExportedAt) {
        this.lastExportedAt = lastExportedAt;
    }

    public String getLastValidationWarningsJson() {
        return lastValidationWarningsJson;
    }

    public void setLastValidationWarningsJson(String lastValidationWarningsJson) {
        this.lastValidationWarningsJson = lastValidationWarningsJson;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public LocalDateTime getUpdated() {
        return updated;
    }

    public List<DatasetItem> getItems() {
        return items;
    }

    public void setItems(List<DatasetItem> items) {
        this.items = items;
    }
}

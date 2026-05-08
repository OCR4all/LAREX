package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.*;
import de.uniwue.zpd.dachs.larex.backend.util.TextIndexDefaultsUtil;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "projects", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"name", "library_id"}, name = "uk_project_name_library")
})
@EntityListeners(AuditingEntityListener.class)
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updated;

    @ElementCollection
    @CollectionTable(name = "project_tags", joinColumns = @JoinColumn(name = "project_id"))
    @Column(name = "tag")
    private List<String> tags = new ArrayList<>();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Page> pages = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "library_id", nullable = false)
    private Library library;

    @Column(nullable = false)
    private boolean locked = false;

    @Column(columnDefinition = "TEXT")
    private String lockedReason;

    @Column(name = "locked_by_action_run_id")
    private String lockedByActionRunId;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "codec_id")
    private Codec codec;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "label_set_id")
    private LabelSet labelSet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dictionary_id")
    private ControlledDictionary dictionary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_set_id")
    private TagSet tagSet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "normalization_profile_id")
    private NormalizationProfile normalizationProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validation_ruleset_id")
    private ValidationRuleset validationRuleset;

    @Column(name = "default_gt_index")
    private Integer defaultGtIndex;

    @Column(name = "default_recognition_indices", columnDefinition = "TEXT")
    private String defaultRecognitionIndices;

    public Project() {}

    public Project(String name, String description, Library library) {
        this.name = name;
        this.description = description;
        this.library = library;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public List<Page> getPages() {
        return pages;
    }

    public void setPages(List<Page> pages) {
        this.pages = pages;
    }

    public Library getLibrary() {
        return library;
    }

    public void setLibrary(Library library) {
        this.library = library;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public String getLockedReason() {
        return lockedReason;
    }

    public void setLockedReason(String lockedReason) {
        this.lockedReason = lockedReason;
    }

    public String getLockedByActionRunId() {
        return lockedByActionRunId;
    }

    public void setLockedByActionRunId(String lockedByActionRunId) {
        this.lockedByActionRunId = lockedByActionRunId;
    }

    public LocalDateTime getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(LocalDateTime lockedAt) {
        this.lockedAt = lockedAt;
    }

    public Codec getCodec() {
        return codec;
    }

    public void setCodec(Codec codec) {
        this.codec = codec;
    }

    public LabelSet getLabelSet() {
        return labelSet;
    }

    public void setLabelSet(LabelSet labelSet) {
        this.labelSet = labelSet;
    }

    public ControlledDictionary getDictionary() {
        return dictionary;
    }

    public void setDictionary(ControlledDictionary dictionary) {
        this.dictionary = dictionary;
    }

    public TagSet getTagSet() {
        return tagSet;
    }

    public void setTagSet(TagSet tagSet) {
        this.tagSet = tagSet;
    }

    public NormalizationProfile getNormalizationProfile() {
        return normalizationProfile;
    }

    public void setNormalizationProfile(NormalizationProfile normalizationProfile) {
        this.normalizationProfile = normalizationProfile;
    }

    public ValidationRuleset getValidationRuleset() {
        return validationRuleset;
    }

    public void setValidationRuleset(ValidationRuleset validationRuleset) {
        this.validationRuleset = validationRuleset;
    }

    public Integer getDefaultGtIndex() {
        return defaultGtIndex;
    }

    public void setDefaultGtIndex(Integer defaultGtIndex) {
        this.defaultGtIndex = defaultGtIndex;
    }

    public int getEffectiveDefaultGtIndex() {
        return TextIndexDefaultsUtil.effectiveGtIndex(defaultGtIndex);
    }

    public String getDefaultRecognitionIndices() {
        return defaultRecognitionIndices;
    }

    public void setDefaultRecognitionIndices(String defaultRecognitionIndices) {
        this.defaultRecognitionIndices = defaultRecognitionIndices;
    }

    public List<Integer> getDefaultRecognitionIndicesList() {
        return TextIndexDefaultsUtil.fromCsv(defaultRecognitionIndices);
    }

    public void setDefaultRecognitionIndicesList(List<Integer> indices) {
        this.defaultRecognitionIndices = TextIndexDefaultsUtil.toCsv(indices);
    }
}

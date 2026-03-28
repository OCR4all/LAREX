package de.uniwue.zpd.dachs.larex.backend.entity.workspace;

import de.uniwue.zpd.dachs.larex.backend.entity.Codec;
import de.uniwue.zpd.dachs.larex.backend.entity.ControlledDictionary;
import de.uniwue.zpd.dachs.larex.backend.entity.LabelSet;
import de.uniwue.zpd.dachs.larex.backend.entity.NormalizationProfile;
import de.uniwue.zpd.dachs.larex.backend.entity.TagSet;
import de.uniwue.zpd.dachs.larex.backend.entity.ValidationRuleset;
import de.uniwue.zpd.dachs.larex.backend.util.TextIndexDefaultsUtil;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public abstract class AbstractWorkspace {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String ownerUserId;

    @Column(nullable = true)
    private String description;

    @Column(nullable = true)
    private String avatar;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updated;

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

    protected AbstractWorkspace() {}

    protected AbstractWorkspace(String ownerUserId, String description) {
        this.ownerUserId = ownerUserId;
        this.description = description;
    }

    public abstract String getName();
    public abstract boolean isPersonal();
    public abstract boolean canInviteUsers();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(String ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public LocalDateTime getUpdated() {
        return updated;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AbstractWorkspace)) return false;
        AbstractWorkspace that = (AbstractWorkspace) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}

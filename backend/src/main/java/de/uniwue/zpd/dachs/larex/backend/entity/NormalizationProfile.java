package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(
        name = "normalization_profiles",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_normalization_profile_workspace_name", columnNames = {"workspace_id", "name"})
        }
)
@EntityListeners(AuditingEntityListener.class)
public class NormalizationProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(name = "workspace_id", nullable = false)
    private String workspaceId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ElementCollection
    @CollectionTable(name = "normalization_profile_tags", joinColumns = @JoinColumn(name = "normalization_profile_id"))
    @Column(name = "tag", nullable = false)
    private List<String> tags = new ArrayList<>();

    @Column(nullable = false, length = 32)
    private String unicodeNormalization = "NFC";

    @Column(nullable = false)
    private boolean collapseWhitespace = true;

    @Column(nullable = false)
    private boolean trimText = true;

    @Column(nullable = false)
    private boolean dehyphenateLineBreaks = false;

    @Column(nullable = false)
    private boolean mapLongSToS = false;

    @Column(nullable = false)
    private boolean expandCommonLigatures = false;

    @Column(nullable = false)
    private boolean normalizeQuotes = false;

    @Column(nullable = false)
    private boolean normalizeDashes = false;

    @Column(nullable = false)
    private boolean normalizeEllipsis = false;

    @ElementCollection
    @CollectionTable(name = "normalization_profile_replacement_rules", joinColumns = @JoinColumn(name = "normalization_profile_id"))
    @OrderColumn(name = "rule_order")
    private List<NormalizationReplacementRule> replacementRules = new ArrayList<>();

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
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
        this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
    }

    public String getUnicodeNormalization() {
        return unicodeNormalization;
    }

    public void setUnicodeNormalization(String unicodeNormalization) {
        this.unicodeNormalization = unicodeNormalization;
    }

    public boolean isCollapseWhitespace() {
        return collapseWhitespace;
    }

    public void setCollapseWhitespace(boolean collapseWhitespace) {
        this.collapseWhitespace = collapseWhitespace;
    }

    public boolean isTrimText() {
        return trimText;
    }

    public void setTrimText(boolean trimText) {
        this.trimText = trimText;
    }

    public boolean isDehyphenateLineBreaks() {
        return dehyphenateLineBreaks;
    }

    public void setDehyphenateLineBreaks(boolean dehyphenateLineBreaks) {
        this.dehyphenateLineBreaks = dehyphenateLineBreaks;
    }

    public boolean isMapLongSToS() {
        return mapLongSToS;
    }

    public void setMapLongSToS(boolean mapLongSToS) {
        this.mapLongSToS = mapLongSToS;
    }

    public boolean isExpandCommonLigatures() {
        return expandCommonLigatures;
    }

    public void setExpandCommonLigatures(boolean expandCommonLigatures) {
        this.expandCommonLigatures = expandCommonLigatures;
    }

    public boolean isNormalizeQuotes() {
        return normalizeQuotes;
    }

    public void setNormalizeQuotes(boolean normalizeQuotes) {
        this.normalizeQuotes = normalizeQuotes;
    }

    public boolean isNormalizeDashes() {
        return normalizeDashes;
    }

    public void setNormalizeDashes(boolean normalizeDashes) {
        this.normalizeDashes = normalizeDashes;
    }

    public boolean isNormalizeEllipsis() {
        return normalizeEllipsis;
    }

    public void setNormalizeEllipsis(boolean normalizeEllipsis) {
        this.normalizeEllipsis = normalizeEllipsis;
    }

    public List<NormalizationReplacementRule> getReplacementRules() {
        return replacementRules;
    }

    public void setReplacementRules(List<NormalizationReplacementRule> replacementRules) {
        this.replacementRules = replacementRules == null ? new ArrayList<>() : new ArrayList<>(replacementRules);
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public LocalDateTime getUpdated() {
        return updated;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NormalizationProfile that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

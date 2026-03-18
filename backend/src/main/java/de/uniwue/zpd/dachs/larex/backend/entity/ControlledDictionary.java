package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "controlled_dictionaries", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"name", "library_id"}, name = "uk_dictionary_name_library")
})
@EntityListeners(AuditingEntityListener.class)
public class ControlledDictionary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ElementCollection
    @CollectionTable(name = "controlled_dictionary_tags", joinColumns = @JoinColumn(name = "dictionary_id"))
    @Column(name = "tag_value", columnDefinition = "TEXT")
    private Set<String> tags = new HashSet<>();

    @Column(nullable = false)
    private boolean caseSensitive = false;

    @Column(nullable = false, length = 32)
    private String unicodeNormalization = "NFC";

    @Column(nullable = false)
    private boolean locked = false;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updated;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "library_id", nullable = false)
    private Library library;

    @OneToMany(mappedBy = "dictionary", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ControlledDictionaryEntry> entries = new ArrayList<>();

    public ControlledDictionary() {
    }

    public ControlledDictionary(String name, Library library) {
        this.name = name;
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

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags == null ? new HashSet<>() : new HashSet<>(tags);
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    public String getUnicodeNormalization() {
        return unicodeNormalization;
    }

    public void setUnicodeNormalization(String unicodeNormalization) {
        this.unicodeNormalization = unicodeNormalization;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
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

    public Library getLibrary() {
        return library;
    }

    public void setLibrary(Library library) {
        this.library = library;
    }

    public List<ControlledDictionaryEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<ControlledDictionaryEntry> entries) {
        this.entries = entries;
    }

    public void addEntry(ControlledDictionaryEntry entry) {
        if (entry == null) {
            return;
        }
        entry.setDictionary(this);
        this.entries.add(entry);
    }

    public void removeEntry(ControlledDictionaryEntry entry) {
        if (entry == null) {
            return;
        }
        this.entries.remove(entry);
        entry.setDictionary(null);
    }

    public int getEntryCount() {
        return entries == null ? 0 : entries.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ControlledDictionary that = (ControlledDictionary) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

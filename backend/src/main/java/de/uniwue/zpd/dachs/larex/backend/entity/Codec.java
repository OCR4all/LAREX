package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "codecs", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"name", "library_id"}, name = "uk_codec_name_library")
})
@EntityListeners(AuditingEntityListener.class)
public class Codec {

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
    @CollectionTable(name = "codec_characters", joinColumns = @JoinColumn(name = "codec_id"))
    @Column(name = "character_value", columnDefinition = "TEXT")
    private Set<String> characters = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "codec_tags", joinColumns = @JoinColumn(name = "codec_id"))
    @Column(name = "tag_value", columnDefinition = "TEXT")
    private Set<String> tags = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "library_id", nullable = false)
    private Library library;

    public Codec() {}

    public Codec(String name, Library library) {
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

    public Set<String> getCharacters() {
        return characters;
    }

    public void setCharacters(Set<String> characters) {
        this.characters = characters;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags;
    }

    public Library getLibrary() {
        return library;
    }

    public void setLibrary(Library library) {
        this.library = library;
    }

    public void addCharacter(String character) {
        if (character != null && !character.isEmpty()) {
            this.characters.add(character);
        }
    }

    public void removeCharacter(String character) {
        if (character != null) {
            this.characters.remove(character);
        }
    }

    public boolean hasCharacter(String character) {
        return character != null && this.characters.contains(character);
    }

    public int getCharacterCount() {
        return characters.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Codec codec = (Codec) o;
        return Objects.equals(id, codec.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

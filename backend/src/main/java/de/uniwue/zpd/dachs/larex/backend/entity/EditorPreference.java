package de.uniwue.zpd.dachs.larex.backend.entity;

import tools.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Per-user UI preferences stored as one JSON object. The backend treats the content as
 * opaque; the frontend owns the keys, their types and their defaults. Writes go through
 * {@link de.uniwue.zpd.dachs.larex.backend.repository.editor.EditorPreferenceRepository#mergePreferences}
 * so concurrent saves of different keys cannot overwrite each other.
 */
@Entity
@Table(name = "editor_preferences", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id"}))
public class EditorPreference {

    @Id
    private String id;

    @Column(nullable = false, name = "user_id", unique = true)
    private String userId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferences", columnDefinition = "jsonb", nullable = false)
    private JsonNode preferences;

    @Column(nullable = false, updatable = false)
    private LocalDateTime created;

    @Column(nullable = false)
    private LocalDateTime updated;

    protected EditorPreference() {}

    public String getId() { return id; }

    public String getUserId() { return userId; }

    public JsonNode getPreferences() { return preferences; }

    public LocalDateTime getCreated() { return created; }

    public LocalDateTime getUpdated() { return updated; }
}

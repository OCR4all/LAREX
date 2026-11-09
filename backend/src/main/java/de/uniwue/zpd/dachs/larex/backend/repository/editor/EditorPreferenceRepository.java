package de.uniwue.zpd.dachs.larex.backend.repository.editor;

import de.uniwue.zpd.dachs.larex.backend.entity.EditorPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface EditorPreferenceRepository extends JpaRepository<EditorPreference, String> {

    Optional<EditorPreference> findByUserId(String userId);

    /**
     * Shallow-merges {@code patch} (a JSON object) into the user's preferences in a single
     * statement, creating the row if needed. Top-level keys whose value is JSON {@code null}
     * are removed, which resets that setting to the frontend default. Nested objects are
     * replaced as a whole.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO editor_preferences (id, user_id, preferences, created, updated)
            VALUES (
                :id,
                :userId,
                (SELECT coalesce(jsonb_object_agg(entry.key, entry.value), '{}'::jsonb)
                 FROM jsonb_each(CAST(:patch AS jsonb)) AS entry
                 WHERE entry.value <> 'null'::jsonb),
                :now,
                :now
            )
            ON CONFLICT (user_id) DO UPDATE
            SET preferences = (
                    SELECT coalesce(jsonb_object_agg(entry.key, entry.value), '{}'::jsonb)
                    FROM jsonb_each(editor_preferences.preferences || CAST(:patch AS jsonb)) AS entry
                    WHERE entry.value <> 'null'::jsonb
                ),
                updated = :now
            """, nativeQuery = true)
    void mergePreferences(
            @Param("id") String id,
            @Param("userId") String userId,
            @Param("patch") String patch,
            @Param("now") LocalDateTime now
    );
}

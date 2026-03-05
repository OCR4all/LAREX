package de.uniwue.zpd.dachs.larex.backend.repository.editor;

import de.uniwue.zpd.dachs.larex.backend.entity.EditorPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EditorPreferenceRepository extends JpaRepository<EditorPreference, String> {
    Optional<EditorPreference> findByUserId(String userId);
}

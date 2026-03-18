package de.uniwue.zpd.dachs.larex.backend.repository.dictionary;

import de.uniwue.zpd.dachs.larex.backend.entity.ControlledDictionary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ControlledDictionaryRepository extends JpaRepository<ControlledDictionary, String> {

    List<ControlledDictionary> findByLibraryWorkspaceId(String workspaceId);

    Optional<ControlledDictionary> findByIdAndLibraryWorkspaceId(String dictionaryId, String workspaceId);

    Optional<ControlledDictionary> findByNameAndLibraryWorkspaceId(String name, String workspaceId);

    boolean existsByNameAndLibraryId(String name, String libraryId);

    @Query("""
            SELECT DISTINCT d FROM ControlledDictionary d
            LEFT JOIN d.tags tag
            LEFT JOIN d.entries entry
            WHERE d.library.workspaceId = :workspaceId
              AND (
                LOWER(d.name) LIKE %:query%
                OR LOWER(COALESCE(d.description, '')) LIKE %:query%
                OR LOWER(COALESCE(tag, '')) LIKE %:query%
                OR LOWER(COALESCE(entry.surfaceForm, '')) LIKE %:query%
              )
            """)
    List<ControlledDictionary> findDictionariesInWorkspaceBySearch(@Param("workspaceId") String workspaceId,
                                                                   @Param("query") String query);
}

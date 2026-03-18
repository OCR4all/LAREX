package de.uniwue.zpd.dachs.larex.backend.repository.dictionary;

import de.uniwue.zpd.dachs.larex.backend.entity.ControlledDictionaryEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ControlledDictionaryEntryRepository extends JpaRepository<ControlledDictionaryEntry, String> {

    Page<ControlledDictionaryEntry> findByDictionaryIdOrderBySurfaceFormAsc(String dictionaryId, Pageable pageable);

    @Query("""
            SELECT e
            FROM ControlledDictionaryEntry e
            WHERE e.dictionary.id = :dictionaryId
              AND (
                LOWER(e.surfaceForm) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(e.sourceEntryKey, '')) LIKE LOWER(CONCAT('%', :search, '%'))
              )
            ORDER BY e.surfaceForm ASC
            """)
    Page<ControlledDictionaryEntry> searchByDictionaryId(@Param("dictionaryId") String dictionaryId,
                                                         @Param("search") String search,
                                                         Pageable pageable);

    List<ControlledDictionaryEntry> findByDictionaryIdOrderBySurfaceFormAsc(String dictionaryId);

    Optional<ControlledDictionaryEntry> findByIdAndDictionaryId(String entryId, String dictionaryId);

    Optional<ControlledDictionaryEntry> findByDictionaryIdAndNormalizedValue(String dictionaryId, String normalizedValue);

    long countByDictionaryId(String dictionaryId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ControlledDictionaryEntry e WHERE e.dictionary.id = :dictionaryId")
    void deleteByDictionaryId(@Param("dictionaryId") String dictionaryId);
}

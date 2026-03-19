package de.uniwue.zpd.dachs.larex.backend.repository.search;

import de.uniwue.zpd.dachs.larex.backend.entity.SearchLexiconEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SearchLexiconEntryRepository extends JpaRepository<SearchLexiconEntry, String> {

    @Modifying
    @Query("DELETE FROM SearchLexiconEntry e WHERE e.projectId = :projectId")
    int deleteByProjectId(@Param("projectId") String projectId);

    long countByWorkspaceId(String workspaceId);

    @Query("SELECT e FROM SearchLexiconEntry e WHERE e.workspaceId = :workspaceId ORDER BY e.occurrenceCount DESC")
    List<SearchLexiconEntry> findByWorkspaceId(@Param("workspaceId") String workspaceId);
}

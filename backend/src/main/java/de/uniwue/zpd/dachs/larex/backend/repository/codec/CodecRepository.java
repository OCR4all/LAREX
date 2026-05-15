package de.uniwue.zpd.dachs.larex.backend.repository.codec;

import de.uniwue.zpd.dachs.larex.backend.entity.Codec;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CodecRepository extends JpaRepository<Codec, String> {

    List<Codec> findByLibraryId(String libraryId);

    List<Codec> findByLibraryWorkspaceId(String workspaceId);

    Optional<Codec> findByIdAndLibraryWorkspaceId(String codecId, String workspaceId);

    Optional<Codec> findByNameAndLibraryWorkspaceId(String name, String workspaceId);

    List<Codec> findByLibraryIdAndNameContainingIgnoreCase(String libraryId, String name);

    @Query("SELECT c FROM Codec c WHERE " +
           "c.library.workspaceId = :workspaceId AND (" +
           "LOWER(c.name) LIKE %:query% OR " +
           "EXISTS (SELECT 1 FROM c.characters ch WHERE ch LIKE %:query%))")
    List<Codec> findCodecsInWorkspaceBySearch(@Param("workspaceId") String workspaceId,
                                             @Param("query") String query);

    boolean existsByNameAndLibraryId(String name, String libraryId);
}

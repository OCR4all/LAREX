package de.uniwue.zpd.dachs.larex.backend.repository.library;

import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LibraryRepository extends JpaRepository<Library, String> {

    Optional<Library> findByWorkspaceId(String workspaceId);
}

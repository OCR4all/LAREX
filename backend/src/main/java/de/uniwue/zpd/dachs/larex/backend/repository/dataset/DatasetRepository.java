package de.uniwue.zpd.dachs.larex.backend.repository.dataset;

import de.uniwue.zpd.dachs.larex.backend.entity.Dataset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DatasetRepository extends JpaRepository<Dataset, String> {

    List<Dataset> findByWorkspaceIdOrderByUpdatedDesc(String workspaceId);

    Optional<Dataset> findByIdAndWorkspaceId(String id, String workspaceId);

    boolean existsByWorkspaceIdAndNameIgnoreCase(String workspaceId, String name);
}

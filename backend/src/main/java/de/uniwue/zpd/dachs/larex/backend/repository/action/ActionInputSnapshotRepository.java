package de.uniwue.zpd.dachs.larex.backend.repository.action;

import de.uniwue.zpd.dachs.larex.backend.entity.ActionInputSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface ActionInputSnapshotRepository extends JpaRepository<ActionInputSnapshot, String> {
    List<ActionInputSnapshot> findByRunIdOrderByPositionAsc(String runId);

    @Query("select coalesce(sum(s.fileSize), 0) from ActionInputSnapshot s where s.run.workspaceId = :workspaceId and s.run.snapshotCleanedAt is null")
    Long sumActiveFileSizeByWorkspaceId(String workspaceId);
}

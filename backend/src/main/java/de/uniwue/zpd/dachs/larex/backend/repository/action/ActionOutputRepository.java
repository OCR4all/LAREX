package de.uniwue.zpd.dachs.larex.backend.repository.action;

import de.uniwue.zpd.dachs.larex.backend.entity.ActionOutput;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ActionOutputRepository extends JpaRepository<ActionOutput, String> {
    @EntityGraph(attributePaths = {"files", "files.storedFile", "project"})
    List<ActionOutput> findByProjectIdAndStatusOrderByCompletedAtDesc(String projectId, ActionOutput.Status status);

    @EntityGraph(attributePaths = {"files", "files.storedFile", "project"})
    Optional<ActionOutput> findByIdAndProjectId(String id, String projectId);

    @EntityGraph(attributePaths = {"files", "files.storedFile", "project"})
    Optional<ActionOutput> findBySourceRunId(String sourceRunId);

    @EntityGraph(attributePaths = {"files", "files.storedFile", "project"})
    Optional<ActionOutput> findBySharePublicId(String sharePublicId);

    @EntityGraph(attributePaths = {"files", "files.storedFile", "project"})
    List<ActionOutput> findByStatusAndExpiresAtBefore(ActionOutput.Status status, LocalDateTime cutoff);

    @EntityGraph(attributePaths = {"files", "files.storedFile", "project"})
    List<ActionOutput> findByStatus(ActionOutput.Status status);
}

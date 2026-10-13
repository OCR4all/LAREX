package de.uniwue.zpd.dachs.larex.backend.repository.action;

import de.uniwue.zpd.dachs.larex.backend.entity.ActionOutput;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ActionOutputRepository extends JpaRepository<ActionOutput, String> {
    @EntityGraph(attributePaths = {"files", "files.storedFile", "dataset"})
    List<ActionOutput> findByDatasetId(String datasetId);

    @EntityGraph(attributePaths = {"files", "files.storedFile", "project", "dataset"})
    List<ActionOutput> findByProjectIdAndStatusOrderByCompletedAtDesc(String projectId, ActionOutput.Status status);

    @EntityGraph(attributePaths = {"files", "files.storedFile", "project", "dataset"})
    Optional<ActionOutput> findByIdAndProjectId(String id, String projectId);

    @EntityGraph(attributePaths = {"files", "files.storedFile", "project", "dataset"})
    Optional<ActionOutput> findBySourceRunId(String sourceRunId);

    @EntityGraph(attributePaths = {"files", "files.storedFile", "project", "dataset"})
    Optional<ActionOutput> findBySharePublicId(String sharePublicId);

    @EntityGraph(attributePaths = {"files", "files.storedFile", "project", "dataset"})
    List<ActionOutput> findByStatusAndExpiresAtBefore(ActionOutput.Status status, LocalDateTime cutoff);

    @EntityGraph(attributePaths = {"files", "files.storedFile", "project", "dataset"})
    List<ActionOutput> findByStatus(ActionOutput.Status status);

    @EntityGraph(attributePaths = {"project", "dataset"})
    Page<ActionOutput> findByStatus(ActionOutput.Status status, Pageable pageable);

    @EntityGraph(attributePaths = {"project", "dataset"})
    @Query("""
            select o from ActionOutput o
            left join o.project p
            left join o.dataset d
            where o.status = :status
              and (
                  lower(o.workspaceId) like lower(concat('%', :search, '%'))
                  or lower(p.name) like lower(concat('%', :search, '%'))
                  or lower(d.name) like lower(concat('%', :search, '%'))
                  or lower(o.processorName) like lower(concat('%', :search, '%'))
                  or lower(o.processorKey) like lower(concat('%', :search, '%'))
                  or lower(o.sourceRunId) like lower(concat('%', :search, '%'))
              )
            """)
    Page<ActionOutput> findByStatusAndAdminSearch(
            @Param("status") ActionOutput.Status status,
            @Param("search") String search,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"files", "files.storedFile", "project", "dataset"})
    Optional<ActionOutput> findByIdAndStatus(String id, ActionOutput.Status status);

    @EntityGraph(attributePaths = {"project", "dataset"})
    List<ActionOutput> findByStatusAndCreatedBefore(ActionOutput.Status status, LocalDateTime cutoff);
}

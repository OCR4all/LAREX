package de.uniwue.zpd.dachs.larex.backend.repository;

import de.uniwue.zpd.dachs.larex.backend.entity.ImportJob;
import de.uniwue.zpd.dachs.larex.backend.entity.ImportJob.ImportJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ImportJobRepository extends JpaRepository<ImportJob, String> {

    List<ImportJob> findByProjectId(String projectId);

    List<ImportJob> findByWorkspaceId(String workspaceId);

    List<ImportJob> findByCreatedByUserId(String createdByUserId);

    Optional<ImportJob> findByIdAndWorkspaceId(String jobId, String workspaceId);

    List<ImportJob> findByStatus(ImportJobStatus status);

    List<ImportJob> findByStatusIn(List<ImportJobStatus> statuses);

    @Query("SELECT j FROM ImportJob j WHERE j.status IN :statuses ORDER BY j.created DESC")
    List<ImportJob> findActiveJobs(@Param("statuses") List<ImportJobStatus> statuses);

    @Query("SELECT j FROM ImportJob j ORDER BY j.created DESC")
    List<ImportJob> findAllOrderByCreatedDesc();

    @Query("SELECT COUNT(j) FROM ImportJob j WHERE j.status IN :activeStatuses")
    long countActiveJobs(@Param("activeStatuses") List<ImportJobStatus> activeStatuses);

    @Query("SELECT j FROM ImportJob j WHERE j.status IN :statuses AND j.created < :before")
    List<ImportJob> findStaleJobsCreatedBefore(
            @Param("statuses") List<ImportJobStatus> statuses,
            @Param("before") LocalDateTime before);
}

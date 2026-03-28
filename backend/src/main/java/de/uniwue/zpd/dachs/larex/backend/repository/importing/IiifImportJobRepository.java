package de.uniwue.zpd.dachs.larex.backend.repository.importing;

import de.uniwue.zpd.dachs.larex.backend.entity.IiifImportJob;
import de.uniwue.zpd.dachs.larex.backend.entity.IiifImportJob.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IiifImportJobRepository extends JpaRepository<IiifImportJob, String> {

    Optional<IiifImportJob> findByIdAndWorkspaceIdAndProjectId(String id, String workspaceId, String projectId);

    @Query("SELECT j FROM IiifImportJob j WHERE j.projectId = :projectId AND j.status IN :statuses ORDER BY j.created DESC")
    List<IiifImportJob> findActiveJobsForProject(@Param("projectId") String projectId, @Param("statuses") List<Status> statuses);
}

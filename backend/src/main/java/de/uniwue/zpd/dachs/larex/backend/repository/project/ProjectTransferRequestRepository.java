package de.uniwue.zpd.dachs.larex.backend.repository.project;

import de.uniwue.zpd.dachs.larex.backend.entity.ProjectTransferRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectTransferRequestRepository extends JpaRepository<ProjectTransferRequest, String> {

    List<ProjectTransferRequest> findByStatus(ProjectTransferRequest.Status status);

    List<ProjectTransferRequest> findByRequestedByUserId(String requestedByUserId);

    List<ProjectTransferRequest> findByProjectId(String projectId);

    @Query("SELECT ptr FROM ProjectTransferRequest ptr WHERE ptr.targetWorkspaceId = :workspaceId AND ptr.status = 'PENDING'")
    List<ProjectTransferRequest> findPendingRequestsForTargetWorkspace(@Param("workspaceId") String workspaceId);

    @Query("SELECT ptr FROM ProjectTransferRequest ptr WHERE ptr.sourceWorkspaceId = :workspaceId AND ptr.status = 'PENDING'")
    List<ProjectTransferRequest> findPendingRequestsFromSourceWorkspace(@Param("workspaceId") String workspaceId);

    Optional<ProjectTransferRequest> findByProjectIdAndStatus(String projectId, ProjectTransferRequest.Status status);

    boolean existsByProjectIdAndStatus(String projectId, ProjectTransferRequest.Status status);
}

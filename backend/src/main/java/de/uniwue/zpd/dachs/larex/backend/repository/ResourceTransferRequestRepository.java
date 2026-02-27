package de.uniwue.zpd.dachs.larex.backend.repository;

import de.uniwue.zpd.dachs.larex.backend.entity.ResourceTransferRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResourceTransferRequestRepository extends JpaRepository<ResourceTransferRequest, String> {

    List<ResourceTransferRequest> findByRequestedByUserId(String requestedByUserId);

    @Query("SELECT r FROM ResourceTransferRequest r WHERE r.targetWorkspaceId = :workspaceId AND r.status = 'PENDING'")
    List<ResourceTransferRequest> findPendingRequestsForTargetWorkspace(@Param("workspaceId") String workspaceId);

    @Query("SELECT r FROM ResourceTransferRequest r WHERE r.sourceWorkspaceId = :workspaceId AND r.status = 'PENDING'")
    List<ResourceTransferRequest> findPendingRequestsFromSourceWorkspace(@Param("workspaceId") String workspaceId);

    boolean existsByResourceIdAndResourceTypeAndStatus(String resourceId, ResourceTransferRequest.ResourceType resourceType, ResourceTransferRequest.Status status);
}

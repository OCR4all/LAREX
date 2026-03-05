package de.uniwue.zpd.dachs.larex.backend.repository.workspace;

import de.uniwue.zpd.dachs.larex.backend.entity.WorkspaceMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, String> {
    
    List<WorkspaceMember> findByWorkspaceId(String workspaceId);
    
    List<WorkspaceMember> findByUserId(String userId);
    
    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(String workspaceId, String userId);

    List<WorkspaceMember> findByWorkspaceIdAndUserIdIn(String workspaceId, Collection<String> userIds);
    
    boolean existsByWorkspaceIdAndUserId(String workspaceId, String userId);
    
    List<WorkspaceMember> findByUserIdAndInvitationStatus(String userId, WorkspaceMember.InvitationStatus status);
    
    @Query("SELECT wm FROM WorkspaceMember wm WHERE wm.workspaceId = :workspaceId AND wm.role = :role AND wm.invitationStatus = 'ACCEPTED'")
    List<WorkspaceMember> findByWorkspaceIdAndRoleAndAccepted(@Param("workspaceId") String workspaceId, @Param("role") WorkspaceMember.Role role);
    
    @Query("SELECT COUNT(wm) FROM WorkspaceMember wm WHERE wm.workspaceId = :workspaceId AND wm.role = 'ADMINISTRATOR' AND wm.invitationStatus = 'ACCEPTED'")
    long countAdministratorsByWorkspaceId(@Param("workspaceId") String workspaceId);

    @Query("SELECT wm.workspaceId, COUNT(wm) FROM WorkspaceMember wm WHERE wm.workspaceId IN :workspaceIds AND wm.invitationStatus = 'ACCEPTED' GROUP BY wm.workspaceId")
    List<Object[]> countAcceptedByWorkspaceIds(@Param("workspaceIds") Collection<String> workspaceIds);

    @Query("SELECT wm.workspaceId, COUNT(wm) FROM WorkspaceMember wm WHERE wm.workspaceId IN :workspaceIds GROUP BY wm.workspaceId")
    List<Object[]> countByWorkspaceIds(@Param("workspaceIds") Collection<String> workspaceIds);
}

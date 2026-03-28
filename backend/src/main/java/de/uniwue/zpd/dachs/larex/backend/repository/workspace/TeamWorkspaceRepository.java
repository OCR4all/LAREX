package de.uniwue.zpd.dachs.larex.backend.repository.workspace;

import de.uniwue.zpd.dachs.larex.backend.entity.workspace.TeamWorkspace;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamWorkspaceRepository extends AbstractWorkspaceRepository<TeamWorkspace> {

    @Override
    @EntityGraph(attributePaths = {"codec", "labelSet", "tagSet", "normalizationProfile", "validationRuleset"})
    List<TeamWorkspace> findAll();

    @Override
    @EntityGraph(attributePaths = {"codec", "labelSet", "tagSet", "normalizationProfile", "validationRuleset"})
    Optional<TeamWorkspace> findById(String id);

    @Override
    @EntityGraph(attributePaths = {"codec", "labelSet", "tagSet", "normalizationProfile", "validationRuleset"})
    List<TeamWorkspace> findByOwnerUserId(String ownerUserId);
    
    boolean existsByName(String name);

    @EntityGraph(attributePaths = {"codec", "labelSet", "tagSet", "normalizationProfile", "validationRuleset"})
    Optional<TeamWorkspace> findByName(String name);

    @EntityGraph(attributePaths = {"codec", "labelSet", "tagSet", "normalizationProfile", "validationRuleset"})
    @Query("SELECT tw FROM TeamWorkspace tw JOIN WorkspaceMember wm ON tw.id = wm.workspaceId " +
           "WHERE wm.userId = :userId AND wm.invitationStatus = 'ACCEPTED'")
    List<TeamWorkspace> findTeamWorkspacesByMemberId(@Param("userId") String userId);
}

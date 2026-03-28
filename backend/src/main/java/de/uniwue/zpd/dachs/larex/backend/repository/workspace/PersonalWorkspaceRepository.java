package de.uniwue.zpd.dachs.larex.backend.repository.workspace;

import de.uniwue.zpd.dachs.larex.backend.entity.workspace.PersonalWorkspace;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PersonalWorkspaceRepository extends AbstractWorkspaceRepository<PersonalWorkspace> {

    @Override
    @EntityGraph(attributePaths = {"codec", "labelSet", "tagSet", "normalizationProfile", "validationRuleset"})
    List<PersonalWorkspace> findAll();

    @Override
    @EntityGraph(attributePaths = {"codec", "labelSet", "tagSet", "normalizationProfile", "validationRuleset"})
    Optional<PersonalWorkspace> findById(String id);

    @Override
    @EntityGraph(attributePaths = {"codec", "labelSet", "tagSet", "normalizationProfile", "validationRuleset"})
    List<PersonalWorkspace> findByOwnerUserId(String ownerUserId);

    // Override with specific return type for personal workspace (user can only have one)
    default Optional<PersonalWorkspace> findPersonalWorkspaceByOwnerUserId(String ownerUserId) {
        List<PersonalWorkspace> workspaces = findByOwnerUserId(ownerUserId);
        return workspaces.isEmpty() ? Optional.empty() : Optional.of(workspaces.getFirst());
    }

    boolean existsByOwnerUserId(String ownerUserId);
}

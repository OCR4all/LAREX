package de.uniwue.zpd.dachs.larex.backend.repository.workspace;

import de.uniwue.zpd.dachs.larex.backend.entity.workspace.AbstractWorkspace;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.PersonalWorkspace;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.TeamWorkspace;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for querying across both workspace types.
 * This handles the complexity of working with multiple repository types.
 */
@Service
public class WorkspaceQueryService {
    
    private final PersonalWorkspaceRepository personalWorkspaceRepository;
    private final TeamWorkspaceRepository teamWorkspaceRepository;

    public WorkspaceQueryService(PersonalWorkspaceRepository personalWorkspaceRepository,
                                TeamWorkspaceRepository teamWorkspaceRepository) {
        this.personalWorkspaceRepository = personalWorkspaceRepository;
        this.teamWorkspaceRepository = teamWorkspaceRepository;
    }

    /**
     * Find all workspaces (personal and team) that a user has access to
     */
    public List<AbstractWorkspace> findAllWorkspacesForUser(String userId) {
        Set<AbstractWorkspace> allWorkspaces = new LinkedHashSet<>();
        
        // Add personal workspace
        personalWorkspaceRepository.findPersonalWorkspaceByOwnerUserId(userId)
                .ifPresent(allWorkspaces::add);
        
        // Add owned team workspaces
        allWorkspaces.addAll(teamWorkspaceRepository.findByOwnerUserId(userId));
        
        // Add team workspaces where user is a member (may include duplicates with owned workspaces)
        allWorkspaces.addAll(teamWorkspaceRepository.findTeamWorkspacesByMemberId(userId));
        
        // Convert back to list (LinkedHashSet preserves insertion order and removes duplicates)
        return new ArrayList<>(allWorkspaces);
    }

    /**
     * Find any workspace by ID (searches both types)
     */
    public Optional<AbstractWorkspace> findWorkspaceById(String workspaceId) {
        Optional<AbstractWorkspace> workspace = personalWorkspaceRepository.findById(workspaceId)
                .map(pw -> (AbstractWorkspace) pw);
        
        if (workspace.isEmpty()) {
            workspace = teamWorkspaceRepository.findById(workspaceId)
                    .map(tw -> (AbstractWorkspace) tw);
        }
        
        return workspace;
    }

    /**
     * Find multiple workspaces by IDs (across personal and team workspaces)
     */
    public Map<String, AbstractWorkspace> findWorkspacesByIds(Set<String> workspaceIds) {
        if (workspaceIds == null || workspaceIds.isEmpty()) {
            return Map.of();
        }

        Map<String, AbstractWorkspace> result = new LinkedHashMap<>();

        for (PersonalWorkspace workspace : personalWorkspaceRepository.findAllById(workspaceIds)) {
            result.put(workspace.getId(), workspace);
        }
        for (TeamWorkspace workspace : teamWorkspaceRepository.findAllById(workspaceIds)) {
            result.put(workspace.getId(), workspace);
        }

        return result;
    }

    /**
     * Resolve workspace names in batch.
     */
    public Map<String, String> findWorkspaceNamesByIds(Set<String> workspaceIds) {
        if (workspaceIds == null || workspaceIds.isEmpty()) {
            return Map.of();
        }

        return findWorkspacesByIds(workspaceIds).entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().getName(),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    /**
     * Find workspace by ID with user access check
     */
    public Optional<AbstractWorkspace> findWorkspaceByIdAndUserId(String workspaceId, String userId) {
        // Check personal workspace
        Optional<PersonalWorkspace> personalWorkspace = personalWorkspaceRepository.findById(workspaceId);
        if (personalWorkspace.isPresent() && personalWorkspace.get().getOwnerUserId().equals(userId)) {
            return Optional.of(personalWorkspace.get());
        }

        // Check team workspace (owner or member)
        Optional<TeamWorkspace> teamWorkspace = teamWorkspaceRepository.findById(workspaceId);
        if (teamWorkspace.isPresent()) {
            TeamWorkspace tw = teamWorkspace.get();
            if (tw.getOwnerUserId().equals(userId)) {
                return Optional.of(tw);
            }
            
            // Check if user is a member (this would require WorkspaceMember check)
            // For now, we'll add this logic in the service layer
        }

        return Optional.empty();
    }

    /**
     * Delete workspace by ID (handles both types)
     */
    public boolean deleteWorkspaceById(String workspaceId) {
        if (personalWorkspaceRepository.existsById(workspaceId)) {
            personalWorkspaceRepository.deleteById(workspaceId);
            return true;
        }
        
        if (teamWorkspaceRepository.existsById(workspaceId)) {
            teamWorkspaceRepository.deleteById(workspaceId);
            return true;
        }
        
        return false;
    }
}

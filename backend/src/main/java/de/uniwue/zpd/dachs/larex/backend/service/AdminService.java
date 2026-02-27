package de.uniwue.zpd.dachs.larex.backend.service;

import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserDto;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminWorkspaceDto;
import de.uniwue.zpd.dachs.larex.backend.dto.UserDto;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.AbstractWorkspace;
import de.uniwue.zpd.dachs.larex.backend.repository.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.WorkspaceMemberRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.PersonalWorkspaceRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.TeamWorkspaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class AdminService {

    private final PersonalWorkspaceRepository personalWorkspaceRepository;
    private final TeamWorkspaceRepository teamWorkspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ProjectRepository projectRepository;
    private final UserService userService;

    public AdminService(PersonalWorkspaceRepository personalWorkspaceRepository,
                        TeamWorkspaceRepository teamWorkspaceRepository,
                        WorkspaceMemberRepository workspaceMemberRepository,
                        ProjectRepository projectRepository,
                        UserService userService) {
        this.personalWorkspaceRepository = personalWorkspaceRepository;
        this.teamWorkspaceRepository = teamWorkspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.projectRepository = projectRepository;
        this.userService = userService;
    }

    public List<AdminWorkspaceDto> getAllWorkspacesForAdmin() {
        List<AbstractWorkspace> workspaces = new ArrayList<>();
        workspaces.addAll(personalWorkspaceRepository.findAll());
        workspaces.addAll(teamWorkspaceRepository.findAll());

        if (workspaces.isEmpty()) {
            return List.of();
        }

        Set<String> workspaceIds = new LinkedHashSet<>();
        Set<String> ownerUserIds = new LinkedHashSet<>();
        for (AbstractWorkspace workspace : workspaces) {
            workspaceIds.add(workspace.getId());
            ownerUserIds.add(workspace.getOwnerUserId());
        }

        Map<String, Long> projectCountByWorkspaceId = toLongMap(
                projectRepository.countByWorkspaceIds(workspaceIds)
        );
        Map<String, Long> memberCountByWorkspaceId = toLongMap(
                workspaceMemberRepository.countByWorkspaceIds(workspaceIds)
        );
        Map<String, UserDto> usersById = userService.getUsersByIds(new ArrayList<>(ownerUserIds));

        List<AdminWorkspaceDto> result = new ArrayList<>(workspaces.size());
        for (AbstractWorkspace workspace : workspaces) {
            long memberCount = workspace.isPersonal()
                    ? 1L
                    : memberCountByWorkspaceId.getOrDefault(workspace.getId(), 0L);
            long projectCount = projectCountByWorkspaceId.getOrDefault(workspace.getId(), 0L);
            String ownerUsername = usersById.get(workspace.getOwnerUserId()) != null
                    ? usersById.get(workspace.getOwnerUserId()).username()
                    : null;

            result.add(new AdminWorkspaceDto(
                    workspace.getId(),
                    workspace.getName(),
                    workspace.getDescription(),
                    workspace.isPersonal(),
                    workspace.getOwnerUserId(),
                    ownerUsername,
                    memberCount,
                    projectCount,
                    workspace.getCreated() != null ? workspace.getCreated().toString() : null
            ));
        }

        return result;
    }

    public List<AdminUserDto> getAllUsersForAdmin() {
        return userService.getAllUsersForAdmin();
    }

    private Map<String, Long> toLongMap(Collection<Object[]> rows) {
        Map<String, Long> out = new HashMap<>();
        for (Object[] row : rows) {
            if (row.length < 2 || row[0] == null || row[1] == null) {
                continue;
            }
            out.put((String) row[0], ((Number) row[1]).longValue());
        }
        return out;
    }
}

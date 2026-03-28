package de.uniwue.zpd.dachs.larex.backend.service.admin;

import de.uniwue.zpd.dachs.larex.backend.dto.AdminCreateUserRequest;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminErrorEventDetailDto;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminErrorEventPageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminErrorSummaryDto;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminGlobalRolesDto;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserAuditEventDto;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserDto;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserPageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserStatusFilter;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminWorkspaceDto;
import de.uniwue.zpd.dachs.larex.backend.dto.UserDto;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.AbstractWorkspace;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.PersonalWorkspaceRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.TeamWorkspaceRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceMemberRepository;
import de.uniwue.zpd.dachs.larex.backend.service.user.UserService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminService {

    private final PersonalWorkspaceRepository personalWorkspaceRepository;
    private final TeamWorkspaceRepository teamWorkspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ProjectRepository projectRepository;
    private final UserService userService;
    private final ErrorEventService errorEventService;

    public AdminService(PersonalWorkspaceRepository personalWorkspaceRepository,
                        TeamWorkspaceRepository teamWorkspaceRepository,
                        WorkspaceMemberRepository workspaceMemberRepository,
                        ProjectRepository projectRepository,
                        UserService userService,
                        ErrorEventService errorEventService) {
        this.personalWorkspaceRepository = personalWorkspaceRepository;
        this.teamWorkspaceRepository = teamWorkspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.projectRepository = projectRepository;
        this.userService = userService;
        this.errorEventService = errorEventService;
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

    public AdminUserPageDto getUserPageForAdmin(int page, int size, String search, boolean includeServiceAccounts, AdminUserStatusFilter status) {
        return userService.getUserPageForAdmin(page, size, search, includeServiceAccounts, status);
    }

    public AdminUserDto getUserForAdmin(String userId) {
        return userService.getUserForAdmin(userId);
    }

    @Transactional
    public AdminUserDto createUserForAdmin(String actorUserId, String actorUsername, AdminCreateUserRequest request) {
        return userService.createUserForAdmin(actorUserId, actorUsername, request);
    }

    @Transactional
    public AdminUserDto disableUserForAdmin(String actorUserId, String actorUsername, String targetUserId) {
        return userService.disableUserForAdmin(actorUserId, actorUsername, targetUserId);
    }

    @Transactional
    public AdminUserDto enableUserForAdmin(String actorUserId, String actorUsername, String targetUserId) {
        return userService.enableUserForAdmin(actorUserId, actorUsername, targetUserId);
    }

    @Transactional
    public AdminUserDto resendSetupEmailForAdmin(String actorUserId, String actorUsername, String targetUserId) {
        return userService.resendSetupEmailForAdmin(actorUserId, actorUsername, targetUserId);
    }

    public List<AdminUserAuditEventDto> getUserAuditEventsForAdmin(String targetUserId, int limit) {
        return userService.getUserAuditEventsForAdmin(targetUserId, limit);
    }

    public AdminGlobalRolesDto getGlobalRolesForAdmin(String targetUserId) {
        return userService.getGlobalRolesForAdmin(targetUserId);
    }

    @Transactional
    public AdminGlobalRolesDto grantGlobalCuratorForAdmin(String actorUserId, String actorUsername, String targetUserId, String reason) {
        return userService.grantGlobalCuratorForAdmin(actorUserId, actorUsername, targetUserId, reason);
    }

    @Transactional
    public AdminGlobalRolesDto revokeGlobalCuratorForAdmin(String actorUserId, String actorUsername, String targetUserId, String reason) {
        return userService.revokeGlobalCuratorForAdmin(actorUserId, actorUsername, targetUserId, reason);
    }

    public AdminErrorSummaryDto getErrorSummaryForAdmin(int days) {
        return errorEventService.getSummary(days);
    }

    public AdminErrorEventPageDto getErrorsForAdmin(
            int page,
            int size,
            int days,
            Integer status,
            String userId,
            String workspaceId,
            String query
    ) {
        return errorEventService.getEvents(page, size, days, status, userId, workspaceId, query);
    }

    public AdminErrorEventDetailDto getErrorForAdmin(String errorId) {
        return errorEventService.getEvent(errorId);
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

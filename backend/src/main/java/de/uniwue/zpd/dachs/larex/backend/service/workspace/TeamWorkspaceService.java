package de.uniwue.zpd.dachs.larex.backend.service.workspace;

import de.uniwue.zpd.dachs.larex.backend.dto.UserDto;
import de.uniwue.zpd.dachs.larex.backend.dto.WorkspaceMemberDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.entity.WorkspaceMember;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.TeamWorkspace;
import de.uniwue.zpd.dachs.larex.backend.entity.Codec;
import de.uniwue.zpd.dachs.larex.backend.entity.LabelSet;
import de.uniwue.zpd.dachs.larex.backend.entity.TagSet;
import de.uniwue.zpd.dachs.larex.backend.repository.library.LibraryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceMemberRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.TeamWorkspaceRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceQueryService;
import de.uniwue.zpd.dachs.larex.backend.repository.codec.CodecRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.label.LabelSetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.tag.TagSetRepository;
import de.uniwue.zpd.dachs.larex.backend.service.label.LabelSetInitializationService;
import de.uniwue.zpd.dachs.larex.backend.service.notification.NotificationService;
import de.uniwue.zpd.dachs.larex.backend.service.user.UserService;
import de.uniwue.zpd.dachs.larex.backend.util.TextIndexDefaultsUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class TeamWorkspaceService extends AbstractWorkspaceService {

    private final TeamWorkspaceRepository teamWorkspaceRepository;
    private final LibraryRepository libraryRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final NotificationService notificationService;
    private final UserService userService;
    private final CodecRepository codecRepository;
    private final LabelSetRepository labelSetRepository;
    private final TagSetRepository tagSetRepository;
    private final LabelSetInitializationService labelSetInitializationService;
    private final WorkspaceAccessService workspaceAccessService;

    public TeamWorkspaceService(TeamWorkspaceRepository teamWorkspaceRepository,
                               LibraryRepository libraryRepository,
                               WorkspaceMemberRepository workspaceMemberRepository,
                               NotificationService notificationService,
                               UserService userService,
                               WorkspaceQueryService workspaceQueryService,
                               CodecRepository codecRepository,
                               LabelSetRepository labelSetRepository,
                               TagSetRepository tagSetRepository,
                               LabelSetInitializationService labelSetInitializationService,
                               WorkspaceAccessService workspaceAccessService) {
        super(workspaceQueryService);
        this.teamWorkspaceRepository = teamWorkspaceRepository;
        this.libraryRepository = libraryRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.notificationService = notificationService;
        this.userService = userService;
        this.codecRepository = codecRepository;
        this.labelSetRepository = labelSetRepository;
        this.tagSetRepository = tagSetRepository;
        this.labelSetInitializationService = labelSetInitializationService;
        this.workspaceAccessService = workspaceAccessService;
    }

    /**
     * Create a new team workspace
     */
    public TeamWorkspace createTeamWorkspace(String name, String description, String ownerUserId) {
        if (teamWorkspaceRepository.existsByName(name)) {
            throw new IllegalArgumentException("Team workspace name '" + name + "' already exists");
        }

        TeamWorkspace teamWorkspace = new TeamWorkspace(name, description, ownerUserId);
        teamWorkspace = teamWorkspaceRepository.save(teamWorkspace);

        Library library = new Library(teamWorkspace.getId(), teamWorkspace.getName());
        libraryRepository.save(library);

        WorkspaceMember member = new WorkspaceMember(
                ownerUserId,
                WorkspaceMember.Role.ADMINISTRATOR,
                WorkspaceMember.InvitationStatus.ACCEPTED,
                teamWorkspace.getId()
        );
        workspaceMemberRepository.save(member);

        LabelSet pageXmlLabelset = labelSetInitializationService.createPageXmlLabelset(teamWorkspace.getId());
        teamWorkspace.setLabelSet(pageXmlLabelset);
        teamWorkspace.setDefaultGtIndex(TextIndexDefaultsUtil.DEFAULT_GT_INDEX);
        teamWorkspace.setDefaultRecognitionIndicesList(TextIndexDefaultsUtil.DEFAULT_RECOGNITION_INDICES);
        teamWorkspaceRepository.save(teamWorkspace);

        return teamWorkspace;
    }

    /**
     * Update team workspace
     */
    public Optional<TeamWorkspace> updateTeamWorkspace(String workspaceId, String name, String description, String avatar,
                                                       String codecId, String labelSetId, String tagSetId,
                                                       Integer defaultGtIndex, List<Integer> defaultRecognitionIndices,
                                                       String userId) {
        Optional<TeamWorkspace> workspaceOpt = teamWorkspaceRepository.findById(workspaceId);

        if (workspaceOpt.isPresent()) {
            TeamWorkspace workspace = workspaceOpt.get();

            if (!workspaceAccessService.isUserAdministrator(workspaceId, userId)) {
                return Optional.empty();
            }

            if (!workspace.getName().equals(name) && teamWorkspaceRepository.existsByName(name)) {
                throw new IllegalArgumentException("Team workspace name '" + name + "' already exists");
            }

            workspace.setName(name);
            workspace.setDescription(description);
            workspace.setAvatar(avatar);

            Codec codec = null;
            if (codecId != null && !codecId.trim().isEmpty()) {
                codec = codecRepository.findById(codecId).orElse(null);
            }
            workspace.setCodec(codec);

            LabelSet labelSet = null;
            if (labelSetId != null && !labelSetId.trim().isEmpty()) {
                labelSet = labelSetRepository.findById(labelSetId).orElse(null);
            }
            workspace.setLabelSet(labelSet);

            TagSet tagSet = null;
            if (tagSetId != null && !tagSetId.trim().isEmpty()) {
                tagSet = tagSetRepository.findById(tagSetId).orElse(null);
            }
            workspace.setTagSet(tagSet);

            if (defaultGtIndex != null || defaultRecognitionIndices != null) {
                var resolved = TextIndexDefaultsUtil.resolve(
                        defaultGtIndex,
                        defaultRecognitionIndices,
                        workspace.getDefaultGtIndex(),
                        workspace.getDefaultRecognitionIndicesList()
                );
                boolean changedDefaults = !TextIndexDefaultsUtil.equalsDefaults(
                        workspace.getEffectiveDefaultGtIndex(),
                        workspace.getDefaultRecognitionIndicesList(),
                        resolved.gtIndex(),
                        resolved.recognitionIndices()
                );
                if (changedDefaults && !workspace.getOwnerUserId().equals(userId)) {
                    throw new SecurityException("Only the workspace owner can change default text indices.");
                }
                workspace.setDefaultGtIndex(resolved.gtIndex());
                workspace.setDefaultRecognitionIndicesList(resolved.recognitionIndices());
            }

            return Optional.of(teamWorkspaceRepository.save(workspace));
        }

        return Optional.empty();
    }

    /**
     * Delete team workspace
     */
    public boolean deleteTeamWorkspace(String workspaceId, String userId) {
        Optional<TeamWorkspace> workspaceOpt = teamWorkspaceRepository.findById(workspaceId);
        
        if (workspaceOpt.isPresent()) {
            TeamWorkspace workspace = workspaceOpt.get();
            
            if (!workspaceAccessService.isUserAdministrator(workspaceId, userId)) {
                return false; // No access
            }
            
            teamWorkspaceRepository.delete(workspace);
            return true;
        }
        
        return false;
    }

    /**
     * Invite user to team workspace
     */
    public boolean inviteUserToTeamWorkspace(String workspaceId, String inviterId, String userId, WorkspaceMember.Role role) {
        if (!teamWorkspaceRepository.existsById(workspaceId)) {
            return false;
        }
        
        if (!workspaceAccessService.isUserAdministrator(workspaceId, inviterId)) {
            return false;
        }
        
        // Check if user is already a member
        if (workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)) {
            return false;
        }
        
        WorkspaceMember member = new WorkspaceMember(
                userId,
                role,
                WorkspaceMember.InvitationStatus.PENDING,
                workspaceId
        );
        workspaceMemberRepository.save(member);
        
        return true;
    }

    /**
     * Accept workspace invitation
     */
    public boolean acceptTeamWorkspaceInvitation(String workspaceId, String userId) {
        Optional<WorkspaceMember> memberOpt = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId);
        
        if (memberOpt.isPresent() && memberOpt.get().getInvitationStatus() == WorkspaceMember.InvitationStatus.PENDING) {
            WorkspaceMember member = memberOpt.get();
            member.setInvitationStatus(WorkspaceMember.InvitationStatus.ACCEPTED);
            workspaceMemberRepository.save(member);
            return true;
        }
        
        return false;
    }

    /**
     * Decline workspace invitation
     */
    public boolean declineTeamWorkspaceInvitation(String workspaceId, String userId) {
        Optional<WorkspaceMember> memberOpt = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId);
        
        if (memberOpt.isPresent() && memberOpt.get().getInvitationStatus() == WorkspaceMember.InvitationStatus.PENDING) {
            workspaceMemberRepository.delete(memberOpt.get());
            return true;
        }
        
        return false;
    }

    /**
     * Get team workspace members with enriched user data
     */
    public List<WorkspaceMemberDto> getTeamWorkspaceMembers(String workspaceId, String userId) {
        if (!workspaceAccessService.hasWorkspaceAccess(workspaceId, userId)) {
            return List.of();
        }

        List<WorkspaceMember> members = workspaceMemberRepository.findByWorkspaceId(workspaceId);
        if (members.isEmpty()) {
            return List.of();
        }

        return enrichMembersWithUserData(members);
    }

    /**
     * Get only accepted workspace members with enriched user data.
     * Useful for task assignment where only active members should be assignable.
     */
    public List<WorkspaceMemberDto> getAcceptedTeamWorkspaceMembers(String workspaceId, String userId) {
        if (!workspaceAccessService.hasWorkspaceAccess(workspaceId, userId)) {
            return List.of();
        }

        List<WorkspaceMember> members = workspaceMemberRepository.findByWorkspaceId(workspaceId)
                .stream()
                .filter(m -> m.getInvitationStatus() == WorkspaceMember.InvitationStatus.ACCEPTED)
                .toList();

        if (members.isEmpty()) {
            return List.of();
        }

        return enrichMembersWithUserData(members);
    }

    private List<WorkspaceMemberDto> enrichMembersWithUserData(List<WorkspaceMember> members) {
        // Get all unique user IDs
        List<String> userIds = members.stream()
                .map(WorkspaceMember::getUserId)
                .distinct()
                .toList();

        // Batch fetch user data from Keycloak
        Map<String, UserDto> usersMap = userService.getUsersByIds(userIds);

        // Combine member data with user profile data
        return members.stream()
                .map(member -> {
                    UserDto user = usersMap.get(member.getUserId());
                    if (user != null) {
                        return WorkspaceMemberDto.from(member, user);
                    } else {
                        // Fallback when user data is not available
                        return WorkspaceMemberDto.fromMemberOnly(member);
                    }
                })
                .toList();
    }

    /**
     * Remove user from team workspace
     */
    public boolean removeUserFromTeamWorkspace(String workspaceId, String adminUserId, String targetUserId) {
        if (!workspaceAccessService.isUserAdministrator(workspaceId, adminUserId)) {
            return false;
        }
        
        Optional<WorkspaceMember> memberOpt = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId);
        if (memberOpt.isPresent()) {
            workspaceMemberRepository.delete(memberOpt.get());
            return true;
        }
        
        return false;
    }

    /**
     * Get pending invitations for a user
     */
    public List<WorkspaceMember> getPendingInvitationsForUser(String userId) {
        return workspaceMemberRepository.findByUserIdAndInvitationStatus(userId, WorkspaceMember.InvitationStatus.PENDING);
    }

    /**
     * Leave workspace (user removes themselves)
     */
    public boolean leaveTeamWorkspace(String workspaceId, String userId) {
        Optional<WorkspaceMember> memberOpt = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId);
        
        if (memberOpt.isEmpty()) {
            return false;
        }
        
        WorkspaceMember member = memberOpt.get();
        
        // Check if user is the last administrator
        if (member.getRole() == WorkspaceMember.Role.ADMINISTRATOR) {
            long adminCount = workspaceMemberRepository.countAdministratorsByWorkspaceId(workspaceId);
            if (adminCount <= 1) {
                // Cannot leave if you're the last administrator
                return false;
            }
        }
        
        workspaceMemberRepository.delete(member);
        return true;
    }

    /**
     * Update member role
     */
    public boolean updateMemberRole(String workspaceId, String memberId, String adminUserId, WorkspaceMember.Role newRole) {
        // Check if the requesting user is an administrator
        if (!workspaceAccessService.isUserAdministrator(workspaceId, adminUserId)) {
            return false;
        }
        
        // Find the member to update
        Optional<WorkspaceMember> memberOpt = workspaceMemberRepository.findById(memberId);
        if (memberOpt.isEmpty() || !memberOpt.get().getWorkspaceId().equals(workspaceId)) {
            return false;
        }
        
        WorkspaceMember member = memberOpt.get();
        
        // Prevent demoting the last administrator
        if (member.getRole() == WorkspaceMember.Role.ADMINISTRATOR && 
            newRole == WorkspaceMember.Role.MEMBER) {
            long adminCount = workspaceMemberRepository.countAdministratorsByWorkspaceId(workspaceId);
            if (adminCount <= 1) {
                return false;
            }
        }
        
        member.setRole(newRole);
        workspaceMemberRepository.save(member);
        return true;
    }

    @Override
    protected boolean hasTeamWorkspaceMembership(String workspaceId, String userId) {
        return workspaceAccessService.hasWorkspaceAccess(workspaceId, userId);
    }
}

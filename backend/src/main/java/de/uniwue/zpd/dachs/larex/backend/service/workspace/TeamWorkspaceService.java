package de.uniwue.zpd.dachs.larex.backend.service.workspace;

import de.uniwue.zpd.dachs.larex.backend.dto.UserDto;
import de.uniwue.zpd.dachs.larex.backend.dto.WorkspaceMemberDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.entity.WorkspaceMember;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.TeamWorkspace;
import de.uniwue.zpd.dachs.larex.backend.entity.Codec;
import de.uniwue.zpd.dachs.larex.backend.entity.ControlledDictionary;
import de.uniwue.zpd.dachs.larex.backend.entity.LabelSet;
import de.uniwue.zpd.dachs.larex.backend.entity.NormalizationProfile;
import de.uniwue.zpd.dachs.larex.backend.entity.TagSet;
import de.uniwue.zpd.dachs.larex.backend.entity.ValidationRuleset;
import de.uniwue.zpd.dachs.larex.backend.repository.library.LibraryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceMemberRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.TeamWorkspaceRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceQueryService;
import de.uniwue.zpd.dachs.larex.backend.repository.codec.CodecRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.dictionary.ControlledDictionaryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.label.LabelSetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.normalization.NormalizationProfileRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.tag.TagSetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.validation.ValidationRulesetRepository;
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
    private final ControlledDictionaryRepository dictionaryRepository;
    private final LabelSetRepository labelSetRepository;
    private final TagSetRepository tagSetRepository;
    private final NormalizationProfileRepository normalizationProfileRepository;
    private final ValidationRulesetRepository validationRulesetRepository;
    private final LabelSetInitializationService labelSetInitializationService;
    private final WorkspaceAccessService workspaceAccessService;

    public TeamWorkspaceService(TeamWorkspaceRepository teamWorkspaceRepository,
                               LibraryRepository libraryRepository,
                               WorkspaceMemberRepository workspaceMemberRepository,
                               NotificationService notificationService,
                               UserService userService,
                               WorkspaceQueryService workspaceQueryService,
                               CodecRepository codecRepository,
                               ControlledDictionaryRepository dictionaryRepository,
                               LabelSetRepository labelSetRepository,
                               TagSetRepository tagSetRepository,
                               NormalizationProfileRepository normalizationProfileRepository,
                               ValidationRulesetRepository validationRulesetRepository,
                               LabelSetInitializationService labelSetInitializationService,
                               WorkspaceAccessService workspaceAccessService) {
        super(workspaceQueryService);
        this.teamWorkspaceRepository = teamWorkspaceRepository;
        this.libraryRepository = libraryRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.notificationService = notificationService;
        this.userService = userService;
        this.codecRepository = codecRepository;
        this.dictionaryRepository = dictionaryRepository;
        this.labelSetRepository = labelSetRepository;
        this.tagSetRepository = tagSetRepository;
        this.normalizationProfileRepository = normalizationProfileRepository;
        this.validationRulesetRepository = validationRulesetRepository;
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
                WorkspaceMember.Role.CURATOR,
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
                                                       String codecId, String labelSetId, String dictionaryId, String tagSetId,
                                                       String normalizationProfileId, String validationRulesetId,
                                                       Integer defaultGtIndex, List<Integer> defaultRecognitionIndices,
                                                       String userId) {
        Optional<TeamWorkspace> workspaceOpt = teamWorkspaceRepository.findById(workspaceId);

        if (workspaceOpt.isPresent()) {
            TeamWorkspace workspace = workspaceOpt.get();

            if (!workspaceAccessService.isUserAdministrator(workspaceId, userId)) {
                return Optional.empty();
            }

            boolean isOwner = workspace.getOwnerUserId().equals(userId);

            if (!workspace.getName().equals(name) && teamWorkspaceRepository.existsByName(name) && isOwner) {
                throw new IllegalArgumentException("Team workspace name '" + name + "' already exists");
            }

            if (!isOwner) {
                boolean metadataChangeRequested =
                        !workspace.getName().equals(name)
                                || !java.util.Objects.equals(workspace.getDescription(), description)
                                || !java.util.Objects.equals(workspace.getAvatar(), avatar);
                if (metadataChangeRequested) {
                    throw new SecurityException("Only the workspace owner can edit workspace metadata.");
                }
            } else {
                workspace.setName(name);
                workspace.setDescription(description);
                workspace.setAvatar(avatar);
            }

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

            ControlledDictionary dictionary = null;
            if (dictionaryId != null && !dictionaryId.trim().isEmpty()) {
                dictionary = dictionaryRepository.findById(dictionaryId).orElse(null);
            }
            workspace.setDictionary(dictionary);

            TagSet tagSet = null;
            if (tagSetId != null && !tagSetId.trim().isEmpty()) {
                tagSet = tagSetRepository.findById(tagSetId).orElse(null);
            }
            workspace.setTagSet(tagSet);

            NormalizationProfile normalizationProfile = null;
            if (normalizationProfileId != null && !normalizationProfileId.trim().isEmpty()) {
                normalizationProfile = normalizationProfileRepository.findById(normalizationProfileId).orElse(null);
            }
            workspace.setNormalizationProfile(normalizationProfile);

            ValidationRuleset validationRuleset = null;
            if (validationRulesetId != null && !validationRulesetId.trim().isEmpty()) {
                validationRuleset = validationRulesetRepository.findById(validationRulesetId).orElse(null);
            }
            workspace.setValidationRuleset(validationRuleset);

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
                if (changedDefaults && !workspaceAccessService.canSetPresets(workspaceId, userId)) {
                    throw new SecurityException("You do not have permission to change workspace text index defaults.");
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
            
            if (!workspace.getOwnerUserId().equals(userId) && !workspaceAccessService.isWorkspaceOwner(workspaceId, userId)) {
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
                role.toCanonicalRole(),
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

        TeamWorkspace workspace = teamWorkspaceRepository.findById(workspaceId).orElse(null);
        if (workspace == null) {
            return false;
        }

        boolean actorIsOwner = workspace.getOwnerUserId().equals(adminUserId);
        boolean targetIsOwner = workspace.getOwnerUserId().equals(targetUserId);
        if (targetIsOwner && !actorIsOwner) {
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
        TeamWorkspace workspace = teamWorkspaceRepository.findById(workspaceId).orElse(null);
        if (workspace != null && workspace.getOwnerUserId().equals(userId)) {
            return false;
        }

        Optional<WorkspaceMember> memberOpt = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId);
        
        if (memberOpt.isEmpty()) {
            return false;
        }
        
        workspaceMemberRepository.delete(memberOpt.get());
        return true;
    }

    /**
     * Transfer workspace ownership to an accepted member.
     */
    public boolean transferOwnership(String workspaceId, String actorUserId, String newOwnerUserId) {
        if (newOwnerUserId == null || newOwnerUserId.isBlank()) {
            return false;
        }

        TeamWorkspace workspace = teamWorkspaceRepository.findById(workspaceId).orElse(null);
        if (workspace == null) {
            return false;
        }

        if (!workspaceAccessService.isWorkspaceOwner(workspaceId, actorUserId)) {
            return false;
        }

        String currentOwnerUserId = workspace.getOwnerUserId();
        if (newOwnerUserId.equals(currentOwnerUserId)) {
            return false;
        }

        Optional<WorkspaceMember> newOwnerMemberOpt = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, newOwnerUserId);
        if (newOwnerMemberOpt.isEmpty()
                || newOwnerMemberOpt.get().getInvitationStatus() != WorkspaceMember.InvitationStatus.ACCEPTED) {
            return false;
        }

        WorkspaceMember newOwnerMember = newOwnerMemberOpt.get();
        newOwnerMember.setRole(WorkspaceMember.Role.CURATOR);
        workspaceMemberRepository.save(newOwnerMember);

        Optional<WorkspaceMember> previousOwnerMemberOpt = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, currentOwnerUserId);
        if (previousOwnerMemberOpt.isPresent()) {
            WorkspaceMember previousOwnerMember = previousOwnerMemberOpt.get();
            previousOwnerMember.setRole(WorkspaceMember.Role.CURATOR);
            previousOwnerMember.setInvitationStatus(WorkspaceMember.InvitationStatus.ACCEPTED);
            workspaceMemberRepository.save(previousOwnerMember);
        } else {
            WorkspaceMember previousOwnerMember = new WorkspaceMember(
                    currentOwnerUserId,
                    WorkspaceMember.Role.CURATOR,
                    WorkspaceMember.InvitationStatus.ACCEPTED,
                    workspaceId
            );
            workspaceMemberRepository.save(previousOwnerMember);
        }

        workspace.setOwnerUserId(newOwnerUserId);
        teamWorkspaceRepository.save(workspace);
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

        TeamWorkspace workspace = teamWorkspaceRepository.findById(workspaceId).orElse(null);
        if (workspace == null) {
            return false;
        }
        boolean actorIsOwner = workspace.getOwnerUserId().equals(adminUserId);
        
        // Find the member to update
        Optional<WorkspaceMember> memberOpt = workspaceMemberRepository.findById(memberId);
        if (memberOpt.isEmpty() || !memberOpt.get().getWorkspaceId().equals(workspaceId)) {
            return false;
        }
        
        WorkspaceMember member = memberOpt.get();

        boolean targetIsOwner = workspace.getOwnerUserId().equals(member.getUserId());
        if (targetIsOwner && !actorIsOwner) {
            return false;
        }
        if (targetIsOwner && actorIsOwner) {
            return false;
        }

        member.setRole(newRole.toCanonicalRole());
        workspaceMemberRepository.save(member);
        return true;
    }

    @Override
    protected boolean hasTeamWorkspaceMembership(String workspaceId, String userId) {
        return workspaceAccessService.hasWorkspaceAccess(workspaceId, userId);
    }
}

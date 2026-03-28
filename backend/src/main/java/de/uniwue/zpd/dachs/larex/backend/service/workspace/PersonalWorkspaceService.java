package de.uniwue.zpd.dachs.larex.backend.service.workspace;

import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.entity.WorkspaceMember;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.PersonalWorkspace;
import de.uniwue.zpd.dachs.larex.backend.entity.Codec;
import de.uniwue.zpd.dachs.larex.backend.entity.ControlledDictionary;
import de.uniwue.zpd.dachs.larex.backend.entity.LabelSet;
import de.uniwue.zpd.dachs.larex.backend.entity.NormalizationProfile;
import de.uniwue.zpd.dachs.larex.backend.entity.TagSet;
import de.uniwue.zpd.dachs.larex.backend.entity.ValidationRuleset;
import de.uniwue.zpd.dachs.larex.backend.repository.library.LibraryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceMemberRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.PersonalWorkspaceRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceQueryService;
import de.uniwue.zpd.dachs.larex.backend.repository.codec.CodecRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.dictionary.ControlledDictionaryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.label.LabelSetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.normalization.NormalizationProfileRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.tag.TagSetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.validation.ValidationRulesetRepository;
import de.uniwue.zpd.dachs.larex.backend.service.label.LabelSetInitializationService;
import de.uniwue.zpd.dachs.larex.backend.util.TextIndexDefaultsUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class PersonalWorkspaceService extends AbstractWorkspaceService {

    private final PersonalWorkspaceRepository personalWorkspaceRepository;
    private final LibraryRepository libraryRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final CodecRepository codecRepository;
    private final ControlledDictionaryRepository dictionaryRepository;
    private final LabelSetRepository labelSetRepository;
    private final TagSetRepository tagSetRepository;
    private final NormalizationProfileRepository normalizationProfileRepository;
    private final ValidationRulesetRepository validationRulesetRepository;
    private final LabelSetInitializationService labelSetInitializationService;

    public PersonalWorkspaceService(PersonalWorkspaceRepository personalWorkspaceRepository,
                                   LibraryRepository libraryRepository,
                                   WorkspaceMemberRepository workspaceMemberRepository,
                                   WorkspaceQueryService workspaceQueryService,
                                   CodecRepository codecRepository,
                                   ControlledDictionaryRepository dictionaryRepository,
                                   LabelSetRepository labelSetRepository,
                                   TagSetRepository tagSetRepository,
                                   NormalizationProfileRepository normalizationProfileRepository,
                                   ValidationRulesetRepository validationRulesetRepository,
                                   LabelSetInitializationService labelSetInitializationService) {
        super(workspaceQueryService);
        this.personalWorkspaceRepository = personalWorkspaceRepository;
        this.libraryRepository = libraryRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.codecRepository = codecRepository;
        this.dictionaryRepository = dictionaryRepository;
        this.labelSetRepository = labelSetRepository;
        this.tagSetRepository = tagSetRepository;
        this.normalizationProfileRepository = normalizationProfileRepository;
        this.validationRulesetRepository = validationRulesetRepository;
        this.labelSetInitializationService = labelSetInitializationService;
    }

    /**
     * Ensure user has a personal workspace (create if doesn't exist)
     */
    public PersonalWorkspace ensurePersonalWorkspace(String userId) {
        Optional<PersonalWorkspace> existing = personalWorkspaceRepository.findPersonalWorkspaceByOwnerUserId(userId);
        
        if (existing.isPresent()) {
            return existing.get();
        }
        
        return createPersonalWorkspace(userId);
    }

    /**
     * Create a new personal workspace for user
     */
    public PersonalWorkspace createPersonalWorkspace(String userId) {
        if (personalWorkspaceRepository.existsByOwnerUserId(userId)) {
            throw new IllegalArgumentException("User already has a personal workspace");
        }

        PersonalWorkspace personalWorkspace = new PersonalWorkspace(userId);
        personalWorkspace = personalWorkspaceRepository.save(personalWorkspace);

        Library library = new Library(personalWorkspace.getId(), personalWorkspace.getName());
        libraryRepository.save(library);

        WorkspaceMember member = new WorkspaceMember(
                userId,
                WorkspaceMember.Role.CURATOR,
                WorkspaceMember.InvitationStatus.ACCEPTED,
                personalWorkspace.getId()
        );
        workspaceMemberRepository.save(member);

        LabelSet pageXmlLabelset = labelSetInitializationService.createPageXmlLabelset(personalWorkspace.getId());
        personalWorkspace.setLabelSet(pageXmlLabelset);
        personalWorkspace.setDefaultGtIndex(TextIndexDefaultsUtil.DEFAULT_GT_INDEX);
        personalWorkspace.setDefaultRecognitionIndicesList(TextIndexDefaultsUtil.DEFAULT_RECOGNITION_INDICES);
        personalWorkspaceRepository.save(personalWorkspace);

        return personalWorkspace;
    }

    /**
     * Update personal workspace
     */
    public Optional<PersonalWorkspace> updatePersonalWorkspace(String workspaceId, String description, String avatar,
                                                               String codecId, String labelSetId, String dictionaryId, String tagSetId,
                                                               String normalizationProfileId, String validationRulesetId,
                                                               Integer defaultGtIndex, List<Integer> defaultRecognitionIndices,
                                                               String userId) {
        Optional<PersonalWorkspace> workspaceOpt = personalWorkspaceRepository.findById(workspaceId);

        if (workspaceOpt.isPresent()) {
            PersonalWorkspace workspace = workspaceOpt.get();

            if (!workspace.getOwnerUserId().equals(userId)) {
                return Optional.empty();
            }

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
                workspace.setDefaultGtIndex(resolved.gtIndex());
                workspace.setDefaultRecognitionIndicesList(resolved.recognitionIndices());
            }

            return Optional.of(personalWorkspaceRepository.save(workspace));
        }

        return Optional.empty();
    }

    /**
     * Get user's personal workspace
     */
    public Optional<PersonalWorkspace> getPersonalWorkspace(String userId) {
        return personalWorkspaceRepository.findPersonalWorkspaceByOwnerUserId(userId);
    }

    @Override
    protected boolean hasTeamWorkspaceMembership(String workspaceId, String userId) {
        // Personal workspaces don't have team memberships
        return false;
    }
}

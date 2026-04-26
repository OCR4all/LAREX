package de.uniwue.zpd.dachs.larex.backend.service.project;

import de.uniwue.zpd.dachs.larex.backend.dto.ResourceTransferDto;
import de.uniwue.zpd.dachs.larex.backend.entity.*;
import de.uniwue.zpd.dachs.larex.backend.repository.codec.CodecRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.dictionary.ControlledDictionaryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.keyboard.VirtualKeyboardRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.label.LabelSetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.library.LibraryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.normalization.NormalizationProfileRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ResourceTransferRequestRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.tag.TagSetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.validation.ValidationRulesetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.PersonalWorkspaceRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.TeamWorkspaceRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceQueryService;
import de.uniwue.zpd.dachs.larex.backend.service.security.AuthorizationPolicyService;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.PersonalWorkspace;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.TeamWorkspace;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ResourceTransferService {

    private final ResourceTransferRequestRepository transferRequestRepository;
    private final CodecRepository codecRepository;
    private final ControlledDictionaryRepository dictionaryRepository;
    private final VirtualKeyboardRepository virtualKeyboardRepository;
    private final LabelSetRepository labelSetRepository;
    private final TagSetRepository tagSetRepository;
    private final NormalizationProfileRepository normalizationProfileRepository;
    private final ValidationRulesetRepository validationRulesetRepository;
    private final LibraryRepository libraryRepository;
    private final ProjectRepository projectRepository;
    private final PersonalWorkspaceRepository personalWorkspaceRepository;
    private final TeamWorkspaceRepository teamWorkspaceRepository;
    private final WorkspaceQueryService workspaceQueryService;
    private final AuthorizationPolicyService authorizationPolicyService;

    public ResourceTransferService(
            ResourceTransferRequestRepository transferRequestRepository,
            CodecRepository codecRepository,
            ControlledDictionaryRepository dictionaryRepository,
            VirtualKeyboardRepository virtualKeyboardRepository,
            LabelSetRepository labelSetRepository,
            TagSetRepository tagSetRepository,
            NormalizationProfileRepository normalizationProfileRepository,
            ValidationRulesetRepository validationRulesetRepository,
            LibraryRepository libraryRepository,
            ProjectRepository projectRepository,
            PersonalWorkspaceRepository personalWorkspaceRepository,
            TeamWorkspaceRepository teamWorkspaceRepository,
            WorkspaceQueryService workspaceQueryService,
            AuthorizationPolicyService authorizationPolicyService) {
        this.transferRequestRepository = transferRequestRepository;
        this.codecRepository = codecRepository;
        this.dictionaryRepository = dictionaryRepository;
        this.virtualKeyboardRepository = virtualKeyboardRepository;
        this.labelSetRepository = labelSetRepository;
        this.tagSetRepository = tagSetRepository;
        this.normalizationProfileRepository = normalizationProfileRepository;
        this.validationRulesetRepository = validationRulesetRepository;
        this.libraryRepository = libraryRepository;
        this.projectRepository = projectRepository;
        this.personalWorkspaceRepository = personalWorkspaceRepository;
        this.teamWorkspaceRepository = teamWorkspaceRepository;
        this.workspaceQueryService = workspaceQueryService;
        this.authorizationPolicyService = authorizationPolicyService;
    }

    public Optional<ResourceTransferRequest> requestTransfer(
            String resourceId, ResourceTransferRequest.ResourceType resourceType,
            String targetWorkspaceId, String requestedByUserId, String message,
            ResourceTransferRequest.TransferType transferType) {

        String sourceWorkspaceId = getSourceWorkspaceId(resourceId, resourceType);
        if (sourceWorkspaceId == null || sourceWorkspaceId.equals(targetWorkspaceId)) {
            return Optional.empty();
        }

        if (!isUserAdministratorInWorkspace(sourceWorkspaceId, requestedByUserId)) {
            return Optional.empty();
        }

        if (workspaceQueryService.findWorkspaceById(targetWorkspaceId).isEmpty()) {
            return Optional.empty();
        }

        if (transferRequestRepository.existsByResourceIdAndResourceTypeAndStatus(
                resourceId, resourceType, ResourceTransferRequest.Status.PENDING)) {
            return Optional.empty();
        }

        boolean canAutoApprove = isUserAdministratorInWorkspace(targetWorkspaceId, requestedByUserId);

        ResourceTransferRequest request = new ResourceTransferRequest(
                resourceId, resourceType, sourceWorkspaceId, targetWorkspaceId,
                requestedByUserId, message, transferType
        );

        if (canAutoApprove) {
            request.setStatus(ResourceTransferRequest.Status.APPROVED);
            request.setApprovedByUserId(requestedByUserId);
            request = transferRequestRepository.save(request);
            executeTransfer(request);
            return Optional.of(request);
        } else {
            request.setStatus(ResourceTransferRequest.Status.PENDING);
            return Optional.of(transferRequestRepository.save(request));
        }
    }

    public boolean approveTransferRequest(String requestId, String approvingUserId) {
        return transferRequestRepository.findById(requestId)
                .filter(r -> r.getStatus() == ResourceTransferRequest.Status.PENDING)
                .filter(r -> isUserAdministratorInWorkspace(r.getTargetWorkspaceId(), approvingUserId))
                .map(request -> {
                    request.setStatus(ResourceTransferRequest.Status.APPROVED);
                    request.setApprovedByUserId(approvingUserId);
                    transferRequestRepository.save(request);
                    executeTransfer(request);
                    return true;
                }).orElse(false);
    }

    public boolean rejectTransferRequest(String requestId, String rejectingUserId, String rejectionReason) {
        return transferRequestRepository.findById(requestId)
                .filter(r -> r.getStatus() == ResourceTransferRequest.Status.PENDING)
                .filter(r -> isUserAdministratorInWorkspace(r.getTargetWorkspaceId(), rejectingUserId))
                .map(request -> {
                    request.setStatus(ResourceTransferRequest.Status.REJECTED);
                    request.setApprovedByUserId(rejectingUserId);
                    request.setRejectionReason(rejectionReason);
                    transferRequestRepository.save(request);
                    return true;
                }).orElse(false);
    }

    public boolean cancelTransferRequest(String requestId, String cancellingUserId) {
        return transferRequestRepository.findById(requestId)
                .filter(r -> r.getStatus() == ResourceTransferRequest.Status.PENDING)
                .filter(r -> r.getRequestedByUserId().equals(cancellingUserId))
                .map(request -> {
                    request.setStatus(ResourceTransferRequest.Status.CANCELLED);
                    transferRequestRepository.save(request);
                    return true;
                }).orElse(false);
    }

    public List<ResourceTransferRequest> getUserTransferRequests(String userId) {
        return transferRequestRepository.findByRequestedByUserId(userId);
    }

    public List<ResourceTransferRequest> getPendingIncomingRequestsForWorkspace(String workspaceId) {
        return transferRequestRepository.findPendingRequestsForTargetWorkspace(workspaceId);
    }

    public List<ResourceTransferRequest> getPendingOutgoingRequestsForWorkspace(String workspaceId) {
        return transferRequestRepository.findPendingRequestsFromSourceWorkspace(workspaceId);
    }

    public List<ResourceTransferDto.Response> toResponses(List<ResourceTransferRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }

        Set<String> workspaceIds = new HashSet<>();
        Set<String> codecIds = new HashSet<>();
        Set<String> dictionaryIds = new HashSet<>();
        Set<String> keyboardIds = new HashSet<>();
        Set<String> labelSetIds = new HashSet<>();
        Set<String> tagSetIds = new HashSet<>();
        Set<String> normalizationProfileIds = new HashSet<>();
        Set<String> validationRulesetIds = new HashSet<>();

        for (ResourceTransferRequest request : requests) {
            workspaceIds.add(request.getSourceWorkspaceId());
            workspaceIds.add(request.getTargetWorkspaceId());
            switch (request.getResourceType()) {
                case CODEC -> codecIds.add(request.getResourceId());
                case DICTIONARY -> dictionaryIds.add(request.getResourceId());
                case VIRTUAL_KEYBOARD -> keyboardIds.add(request.getResourceId());
                case LABEL_SET -> labelSetIds.add(request.getResourceId());
                case TAG_SET -> tagSetIds.add(request.getResourceId());
                case NORMALIZATION_PROFILE -> normalizationProfileIds.add(request.getResourceId());
                case VALIDATION_RULESET -> validationRulesetIds.add(request.getResourceId());
            }
        }

        Map<String, String> workspaceNames = workspaceQueryService.findWorkspaceNamesByIds(workspaceIds);
        Map<String, String> resourceNames = new HashMap<>();
        for (Codec codec : codecRepository.findAllById(codecIds)) {
            resourceNames.put(codec.getId(), codec.getName());
        }
        for (ControlledDictionary dictionary : dictionaryRepository.findAllById(dictionaryIds)) {
            resourceNames.put(dictionary.getId(), dictionary.getName());
        }
        for (VirtualKeyboard keyboard : virtualKeyboardRepository.findAllById(keyboardIds)) {
            resourceNames.put(keyboard.getId(), keyboard.getName());
        }
        for (LabelSet labelSet : labelSetRepository.findAllById(labelSetIds)) {
            resourceNames.put(labelSet.getId(), labelSet.getName());
        }
        for (TagSet tagSet : tagSetRepository.findAllById(tagSetIds)) {
            resourceNames.put(tagSet.getId(), tagSet.getName());
        }
        for (NormalizationProfile profile : normalizationProfileRepository.findAllById(normalizationProfileIds)) {
            resourceNames.put(profile.getId(), profile.getName());
        }
        for (ValidationRuleset ruleset : validationRulesetRepository.findAllById(validationRulesetIds)) {
            resourceNames.put(ruleset.getId(), ruleset.getName());
        }

        List<ResourceTransferDto.Response> responses = new ArrayList<>(requests.size());
        for (ResourceTransferRequest request : requests) {
            responses.add(new ResourceTransferDto.Response(
                    request.getId(),
                    request.getResourceId(),
                    resourceNames.getOrDefault(request.getResourceId(), "Unknown"),
                    request.getResourceType(),
                    request.getSourceWorkspaceId(),
                    workspaceNames.getOrDefault(request.getSourceWorkspaceId(), "Unknown"),
                    request.getTargetWorkspaceId(),
                    workspaceNames.getOrDefault(request.getTargetWorkspaceId(), "Unknown"),
                    request.getRequestedByUserId(),
                    request.getApprovedByUserId(),
                    request.getStatus(),
                    request.getTransferType(),
                    request.getMessage(),
                    request.getRejectionReason(),
                    request.getCreated(),
                    request.getUpdated()
            ));
        }

        return responses;
    }

    public ResourceTransferDto.Response toResponse(ResourceTransferRequest request) {
        if (request == null) {
            return null;
        }
        List<ResourceTransferDto.Response> responses = toResponses(List.of(request));
        return responses.isEmpty() ? null : responses.getFirst();
    }

    private void executeTransfer(ResourceTransferRequest request) {
        switch (request.getResourceType()) {
            case CODEC -> executeCodecTransfer(request);
            case DICTIONARY -> executeDictionaryTransfer(request);
            case VIRTUAL_KEYBOARD -> executeVirtualKeyboardTransfer(request);
            case LABEL_SET -> executeLabelSetTransfer(request);
            case TAG_SET -> executeTagSetTransfer(request);
            case NORMALIZATION_PROFILE -> executeNormalizationProfileTransfer(request);
            case VALIDATION_RULESET -> executeValidationRulesetTransfer(request);
        }
        request.setStatus(ResourceTransferRequest.Status.COMPLETED);
        transferRequestRepository.save(request);
    }

    private void executeCodecTransfer(ResourceTransferRequest request) {
        codecRepository.findById(request.getResourceId()).ifPresent(codec -> {
            Library targetLibrary = getOrCreateTargetLibrary(request.getTargetWorkspaceId());
            if (request.getTransferType() == ResourceTransferRequest.TransferType.COPY) {
                Codec newCodec = new Codec(codec.getName() + " (Copy)", targetLibrary);
                newCodec.setDescription(codec.getDescription());
                newCodec.setCharacters(new HashSet<>(codec.getCharacters()));
                newCodec.setTags(new HashSet<>(codec.getTags()));
                codecRepository.save(newCodec);
            } else {
                codec.setLibrary(targetLibrary);
                codecRepository.save(codec);
            }
        });
    }

    private void executeVirtualKeyboardTransfer(ResourceTransferRequest request) {
        virtualKeyboardRepository.findById(request.getResourceId()).ifPresent(keyboard -> {
            if (request.getTransferType() == ResourceTransferRequest.TransferType.COPY) {
                VirtualKeyboard newKeyboard = new VirtualKeyboard();
                newKeyboard.setName(keyboard.getName() + " (Copy)");
                newKeyboard.setWorkspaceId(request.getTargetWorkspaceId());
                newKeyboard.setDescription(keyboard.getDescription());
                newKeyboard.setCols(keyboard.getCols());
                newKeyboard.setRows(keyboard.getRows());
                newKeyboard.setTags(new ArrayList<>(keyboard.getTags()));
                newKeyboard = virtualKeyboardRepository.save(newKeyboard);
                // Copy items
                for (KeyboardItem item : keyboard.getItems()) {
                    KeyboardItem newItem = new KeyboardItem();
                    newItem.setCharValue(item.getCharValue());
                    newItem.setShiftChar(item.getShiftChar());
                    newItem.setX(item.getX());
                    newItem.setY(item.getY());
                    newItem.setW(item.getW());
                    newItem.setColorClass(item.getColorClass());
                    newItem.setTextClass(item.getTextClass());
                    newItem.setDescription(item.getDescription());
                    newItem.setShiftDescription(item.getShiftDescription());
                    newKeyboard.addItem(newItem);
                }
                virtualKeyboardRepository.save(newKeyboard);
            } else {
                keyboard.setWorkspaceId(request.getTargetWorkspaceId());
                virtualKeyboardRepository.save(keyboard);
            }
        });
    }

    private void executeDictionaryTransfer(ResourceTransferRequest request) {
        dictionaryRepository.findById(request.getResourceId()).ifPresent(dictionary -> {
            Library targetLibrary = getOrCreateTargetLibrary(request.getTargetWorkspaceId());
            if (request.getTransferType() == ResourceTransferRequest.TransferType.COPY) {
                ControlledDictionary copy = new ControlledDictionary(dictionary.getName() + " (Copy)", targetLibrary);
                copy.setDescription(dictionary.getDescription());
                copy.setTags(new HashSet<>(dictionary.getTags()));
                copy.setCaseSensitive(dictionary.isCaseSensitive());
                copy.setUnicodeNormalization(dictionary.getUnicodeNormalization());

                if (dictionary.getEntries() != null) {
                    for (ControlledDictionaryEntry entry : dictionary.getEntries()) {
                        ControlledDictionaryEntry clonedEntry = new ControlledDictionaryEntry();
                        clonedEntry.setSurfaceForm(entry.getSurfaceForm());
                        clonedEntry.setNormalizedValue(entry.getNormalizedValue());
                        clonedEntry.setSourceEntryKey(entry.getSourceEntryKey());
                        clonedEntry.setMetadataJson(entry.getMetadataJson());
                        copy.addEntry(clonedEntry);
                    }
                }
                dictionaryRepository.save(copy);
            } else {
                dictionary.setLibrary(targetLibrary);
                dictionaryRepository.save(dictionary);
            }
        });
    }

    private void executeLabelSetTransfer(ResourceTransferRequest request) {
        labelSetRepository.findById(request.getResourceId()).ifPresent(labelSet -> {
            if (request.getTransferType() == ResourceTransferRequest.TransferType.COPY) {
                LabelSet newLabelSet = new LabelSet(
                        request.getTargetWorkspaceId(),
                        labelSet.getName() + " (Copy)",
                        labelSet.getDescription(),
                        labelSet.getDefinition()
                );
                newLabelSet.setTags(new ArrayList<>(labelSet.getTags()));
                labelSetRepository.save(newLabelSet);
            } else {
                labelSet.setWorkspaceId(request.getTargetWorkspaceId());
                labelSetRepository.save(labelSet);
            }
        });
    }

    private void executeTagSetTransfer(ResourceTransferRequest request) {
        tagSetRepository.findById(request.getResourceId()).ifPresent(tagSet -> {
            if (request.getTransferType() == ResourceTransferRequest.TransferType.COPY) {
                TagSet newTagSet = new TagSet(
                        request.getTargetWorkspaceId(),
                        tagSet.getName() + " (Copy)",
                        tagSet.getDescription(),
                        tagSet.getDefinition()
                );
                newTagSet.setTags(new ArrayList<>(tagSet.getTags()));
                tagSetRepository.save(newTagSet);
            } else {
                tagSet.setWorkspaceId(request.getTargetWorkspaceId());
                tagSetRepository.save(tagSet);
            }
        });
    }

    private void executeNormalizationProfileTransfer(ResourceTransferRequest request) {
        normalizationProfileRepository.findById(request.getResourceId()).ifPresent(profile -> {
            if (request.getTransferType() == ResourceTransferRequest.TransferType.COPY) {
                NormalizationProfile copy = new NormalizationProfile();
                copy.setName(profile.getName() + " (Copy)");
                copy.setWorkspaceId(request.getTargetWorkspaceId());
                copy.setDescription(profile.getDescription());
                copy.setTags(new ArrayList<>(profile.getTags()));
                copy.setUnicodeNormalization(profile.getUnicodeNormalization());
                copy.setCollapseWhitespace(profile.isCollapseWhitespace());
                copy.setTrimText(profile.isTrimText());
                copy.setDehyphenateLineBreaks(profile.isDehyphenateLineBreaks());
                copy.setMapLongSToS(profile.isMapLongSToS());
                copy.setExpandCommonLigatures(profile.isExpandCommonLigatures());
                copy.setNormalizeQuotes(profile.isNormalizeQuotes());
                copy.setNormalizeDashes(profile.isNormalizeDashes());
                copy.setNormalizeEllipsis(profile.isNormalizeEllipsis());
                List<NormalizationReplacementRule> copiedRules = profile.getReplacementRules().stream()
                        .map(rule -> {
                            NormalizationReplacementRule copyRule = new NormalizationReplacementRule();
                            copyRule.setSearch(rule.getSearch());
                            copyRule.setReplacement(rule.getReplacement());
                            copyRule.setRegex(rule.isRegex());
                            return copyRule;
                        })
                        .toList();
                copy.setReplacementRules(copiedRules);
                normalizationProfileRepository.save(copy);
            } else {
                clearNormalizationAssignments(request.getSourceWorkspaceId(), profile);
                profile.setWorkspaceId(request.getTargetWorkspaceId());
                normalizationProfileRepository.save(profile);
            }
        });
    }

    private void executeValidationRulesetTransfer(ResourceTransferRequest request) {
        validationRulesetRepository.findById(request.getResourceId()).ifPresent(ruleset -> {
            if (request.getTransferType() == ResourceTransferRequest.TransferType.COPY) {
                ValidationRuleset copy = new ValidationRuleset();
                copy.setName(ruleset.getName() + " (Copy)");
                copy.setWorkspaceId(request.getTargetWorkspaceId());
                copy.setDescription(ruleset.getDescription());
                copy.setTags(new ArrayList<>(ruleset.getTags()));
                copy.setRulesJson(ruleset.getRulesJson());
                validationRulesetRepository.save(copy);
            } else {
                clearValidationAssignments(request.getSourceWorkspaceId(), ruleset);
                ruleset.setWorkspaceId(request.getTargetWorkspaceId());
                validationRulesetRepository.save(ruleset);
            }
        });
    }

    private String getSourceWorkspaceId(String resourceId, ResourceTransferRequest.ResourceType resourceType) {
        return switch (resourceType) {
            case CODEC -> codecRepository.findById(resourceId)
                    .map(c -> c.getLibrary().getWorkspaceId()).orElse(null);
            case DICTIONARY -> dictionaryRepository.findById(resourceId)
                    .map(d -> d.getLibrary().getWorkspaceId()).orElse(null);
            case VIRTUAL_KEYBOARD -> virtualKeyboardRepository.findById(resourceId)
                    .map(VirtualKeyboard::getWorkspaceId).orElse(null);
            case LABEL_SET -> labelSetRepository.findById(resourceId)
                    .map(LabelSet::getWorkspaceId).orElse(null);
            case TAG_SET -> tagSetRepository.findById(resourceId)
                    .map(TagSet::getWorkspaceId).orElse(null);
            case NORMALIZATION_PROFILE -> normalizationProfileRepository.findById(resourceId)
                    .map(NormalizationProfile::getWorkspaceId).orElse(null);
            case VALIDATION_RULESET -> validationRulesetRepository.findById(resourceId)
                    .map(ValidationRuleset::getWorkspaceId).orElse(null);
        };
    }

    private void clearNormalizationAssignments(String workspaceId, NormalizationProfile profile) {
        for (Project project : projectRepository.findByLibraryWorkspaceIdAndNormalizationProfileId(workspaceId, profile.getId())) {
            project.setNormalizationProfile(null);
        }
        Optional<PersonalWorkspace> personalWorkspace = personalWorkspaceRepository.findById(workspaceId);
        if (personalWorkspace.isPresent()
                && personalWorkspace.get().getNormalizationProfile() != null
                && profile.getId().equals(personalWorkspace.get().getNormalizationProfile().getId())) {
            personalWorkspace.get().setNormalizationProfile(null);
            personalWorkspaceRepository.save(personalWorkspace.get());
        }
        Optional<TeamWorkspace> teamWorkspace = teamWorkspaceRepository.findById(workspaceId);
        if (teamWorkspace.isPresent()
                && teamWorkspace.get().getNormalizationProfile() != null
                && profile.getId().equals(teamWorkspace.get().getNormalizationProfile().getId())) {
            teamWorkspace.get().setNormalizationProfile(null);
            teamWorkspaceRepository.save(teamWorkspace.get());
        }
    }

    private void clearValidationAssignments(String workspaceId, ValidationRuleset ruleset) {
        for (Project project : projectRepository.findByLibraryWorkspaceIdAndValidationRulesetId(workspaceId, ruleset.getId())) {
            project.setValidationRuleset(null);
        }
        Optional<PersonalWorkspace> personalWorkspace = personalWorkspaceRepository.findById(workspaceId);
        if (personalWorkspace.isPresent()
                && personalWorkspace.get().getValidationRuleset() != null
                && ruleset.getId().equals(personalWorkspace.get().getValidationRuleset().getId())) {
            personalWorkspace.get().setValidationRuleset(null);
            personalWorkspaceRepository.save(personalWorkspace.get());
        }
        Optional<TeamWorkspace> teamWorkspace = teamWorkspaceRepository.findById(workspaceId);
        if (teamWorkspace.isPresent()
                && teamWorkspace.get().getValidationRuleset() != null
                && ruleset.getId().equals(teamWorkspace.get().getValidationRuleset().getId())) {
            teamWorkspace.get().setValidationRuleset(null);
            teamWorkspaceRepository.save(teamWorkspace.get());
        }
    }

    private Library getOrCreateTargetLibrary(String targetWorkspaceId) {
        return libraryRepository.findByWorkspaceId(targetWorkspaceId)
                .orElseGet(() -> {
                    String name = workspaceQueryService.findWorkspaceById(targetWorkspaceId)
                            .map(w -> w.getName()).orElse("Unknown Workspace");
                    return libraryRepository.save(new Library(targetWorkspaceId, name));
                });
    }

    private boolean isUserAdministratorInWorkspace(String workspaceId, String userId) {
        return authorizationPolicyService.canManageUtilities(workspaceId, userId);
    }
}

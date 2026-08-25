package de.uniwue.zpd.dachs.larex.backend.service.workspace;

import de.uniwue.zpd.dachs.larex.backend.dto.WorkspaceDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.AbstractWorkspace;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceQueryService;
import de.uniwue.zpd.dachs.larex.backend.util.TextIndexDefaultsUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Calculates and applies the optional propagation of workspace defaults to projects.
 * Workspace defaults remain materialized on projects; this service deliberately does
 * not introduce a second inheritance state.
 */
@Service
@Transactional
public class WorkspaceProjectDefaultsService {

    private final ProjectRepository projectRepository;
    private final WorkspaceQueryService workspaceQueryService;
    private final WorkspaceAccessService workspaceAccessService;

    public WorkspaceProjectDefaultsService(ProjectRepository projectRepository,
                                           WorkspaceQueryService workspaceQueryService,
                                           WorkspaceAccessService workspaceAccessService) {
        this.projectRepository = projectRepository;
        this.workspaceQueryService = workspaceQueryService;
        this.workspaceAccessService = workspaceAccessService;
    }

    public WorkspaceDto.ProjectDefaultsPreviewResponse preview(
            String workspaceId,
            WorkspaceDto.ProjectDefaultsProposal proposal,
            String userId) {
        workspaceAccessService.requireSetPresetsAccess(workspaceId, userId);
        AbstractWorkspace workspace = workspaceQueryService.findWorkspaceById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));

        WorkspaceDefaults before = WorkspaceDefaults.from(workspace);
        WorkspaceDefaults after = WorkspaceDefaults.from(proposal);
        Set<WorkspaceDto.ProjectDefaultKey> changed = changedKeys(before, after);
        return new WorkspaceDto.ProjectDefaultsPreviewResponse(
                List.copyOf(changed),
                impact(workspaceId, after, changed, WorkspaceDto.ProjectDefaultPropagationScope.UNSET_ONLY),
                impact(workspaceId, after, changed, WorkspaceDto.ProjectDefaultPropagationScope.ALL)
        );
    }

    public WorkspaceDto.ProjectDefaultsPropagationResult apply(
            String workspaceId,
            WorkspaceDefaults before,
            AbstractWorkspace after,
            WorkspaceDto.ProjectDefaultPropagationScope scope,
            String userId) {
        WorkspaceDto.ProjectDefaultPropagationScope effectiveScope = scope == null
                ? WorkspaceDto.ProjectDefaultPropagationScope.FUTURE_ONLY
                : scope;
        workspaceAccessService.requireSetPresetsAccess(workspaceId, userId);
        WorkspaceDefaults next = WorkspaceDefaults.from(after);
        Set<WorkspaceDto.ProjectDefaultKey> changed = changedKeys(before, next);
        if (effectiveScope == WorkspaceDto.ProjectDefaultPropagationScope.FUTURE_ONLY || changed.isEmpty()) {
            return new WorkspaceDto.ProjectDefaultsPropagationResult(effectiveScope, 0, 0);
        }

        int updated = 0;
        int skippedLocked = 0;
        for (Project project : projectRepository.findByLibraryWorkspaceId(workspaceId)) {
            boolean wouldChange = wouldChange(project, next, changed, effectiveScope);
            if (!wouldChange) continue;
            if (project.isLocked()) {
                skippedLocked++;
                continue;
            }
            applyDefaults(project, next, changed);
            projectRepository.save(project);
            updated++;
        }
        return new WorkspaceDto.ProjectDefaultsPropagationResult(effectiveScope, updated, skippedLocked);
    }

    private WorkspaceDto.ProjectDefaultsImpact impact(
            String workspaceId,
            WorkspaceDefaults after,
            Set<WorkspaceDto.ProjectDefaultKey> changed,
            WorkspaceDto.ProjectDefaultPropagationScope scope) {
        int affected = 0;
        int locked = 0;
        for (Project project : projectRepository.findByLibraryWorkspaceId(workspaceId)) {
            if (!wouldChange(project, after, changed, scope)) continue;
            if (project.isLocked()) locked++;
            else affected++;
        }
        return new WorkspaceDto.ProjectDefaultsImpact(affected, locked);
    }

    private boolean wouldChange(
            Project project,
            WorkspaceDefaults after,
            Set<WorkspaceDto.ProjectDefaultKey> changed,
            WorkspaceDto.ProjectDefaultPropagationScope scope) {
        for (WorkspaceDto.ProjectDefaultKey key : changed) {
            if (key == WorkspaceDto.ProjectDefaultKey.TEXT_INDICES) {
                if (scope == WorkspaceDto.ProjectDefaultPropagationScope.UNSET_ONLY) {
                    if (project.getDefaultGtIndex() == null && project.getDefaultRecognitionIndices() == null) return true;
                } else if (!TextIndexDefaultsUtil.equalsDefaults(
                        project.getEffectiveDefaultGtIndex(),
                        project.getDefaultRecognitionIndicesList(),
                        after.gtIndex,
                        after.recognitionIndices)) {
                    return true;
                }
                continue;
            }

            String current = resourceId(project, key);
            String target = resourceId(after, key);
            if (scope == WorkspaceDto.ProjectDefaultPropagationScope.UNSET_ONLY) {
                if (current == null && target != null) return true;
            } else if (!Objects.equals(current, target)) {
                return true;
            }
        }
        return false;
    }

    private void applyDefaults(Project project,
                               WorkspaceDefaults after,
                               Set<WorkspaceDto.ProjectDefaultKey> changed) {
        if (changed.contains(WorkspaceDto.ProjectDefaultKey.CODEC)) project.setCodec(after.codecEntity);
        if (changed.contains(WorkspaceDto.ProjectDefaultKey.LABEL_SET)) project.setLabelSet(after.labelSetEntity);
        if (changed.contains(WorkspaceDto.ProjectDefaultKey.DICTIONARY)) project.setDictionary(after.dictionaryEntity);
        if (changed.contains(WorkspaceDto.ProjectDefaultKey.TAG_SET)) project.setTagSet(after.tagSetEntity);
        if (changed.contains(WorkspaceDto.ProjectDefaultKey.NORMALIZATION_PROFILE)) project.setNormalizationProfile(after.normalizationProfileEntity);
        if (changed.contains(WorkspaceDto.ProjectDefaultKey.VALIDATION_RULESET)) project.setValidationRuleset(after.validationRulesetEntity);
        if (changed.contains(WorkspaceDto.ProjectDefaultKey.TEXT_INDICES)) {
            project.setDefaultGtIndex(after.gtIndex);
            project.setDefaultRecognitionIndicesList(after.recognitionIndices);
        }
    }

    private Set<WorkspaceDto.ProjectDefaultKey> changedKeys(WorkspaceDefaults before, WorkspaceDefaults after) {
        Set<WorkspaceDto.ProjectDefaultKey> changed = EnumSet.noneOf(WorkspaceDto.ProjectDefaultKey.class);
        if (!Objects.equals(before.codecId, after.codecId)) changed.add(WorkspaceDto.ProjectDefaultKey.CODEC);
        if (!Objects.equals(before.labelSetId, after.labelSetId)) changed.add(WorkspaceDto.ProjectDefaultKey.LABEL_SET);
        if (!Objects.equals(before.dictionaryId, after.dictionaryId)) changed.add(WorkspaceDto.ProjectDefaultKey.DICTIONARY);
        if (!Objects.equals(before.tagSetId, after.tagSetId)) changed.add(WorkspaceDto.ProjectDefaultKey.TAG_SET);
        if (!Objects.equals(before.normalizationProfileId, after.normalizationProfileId)) changed.add(WorkspaceDto.ProjectDefaultKey.NORMALIZATION_PROFILE);
        if (!Objects.equals(before.validationRulesetId, after.validationRulesetId)) changed.add(WorkspaceDto.ProjectDefaultKey.VALIDATION_RULESET);
        if (!TextIndexDefaultsUtil.equalsDefaults(before.gtIndex, before.recognitionIndices, after.gtIndex, after.recognitionIndices)) {
            changed.add(WorkspaceDto.ProjectDefaultKey.TEXT_INDICES);
        }
        return changed;
    }

    private String resourceId(Project project, WorkspaceDto.ProjectDefaultKey key) {
        return switch (key) {
            case CODEC -> project.getCodec() == null ? null : project.getCodec().getId();
            case LABEL_SET -> project.getLabelSet() == null ? null : project.getLabelSet().getId();
            case DICTIONARY -> project.getDictionary() == null ? null : project.getDictionary().getId();
            case TAG_SET -> project.getTagSet() == null ? null : project.getTagSet().getId();
            case NORMALIZATION_PROFILE -> project.getNormalizationProfile() == null ? null : project.getNormalizationProfile().getId();
            case VALIDATION_RULESET -> project.getValidationRuleset() == null ? null : project.getValidationRuleset().getId();
            case TEXT_INDICES -> null;
        };
    }

    private String resourceId(WorkspaceDefaults defaults, WorkspaceDto.ProjectDefaultKey key) {
        return switch (key) {
            case CODEC -> defaults.codecId;
            case LABEL_SET -> defaults.labelSetId;
            case DICTIONARY -> defaults.dictionaryId;
            case TAG_SET -> defaults.tagSetId;
            case NORMALIZATION_PROFILE -> defaults.normalizationProfileId;
            case VALIDATION_RULESET -> defaults.validationRulesetId;
            case TEXT_INDICES -> null;
        };
    }

    public static final class WorkspaceDefaults {
        private final String codecId;
        private final String labelSetId;
        private final String dictionaryId;
        private final String tagSetId;
        private final String normalizationProfileId;
        private final String validationRulesetId;
        private final int gtIndex;
        private final List<Integer> recognitionIndices;
        private final de.uniwue.zpd.dachs.larex.backend.entity.Codec codecEntity;
        private final de.uniwue.zpd.dachs.larex.backend.entity.LabelSet labelSetEntity;
        private final de.uniwue.zpd.dachs.larex.backend.entity.ControlledDictionary dictionaryEntity;
        private final de.uniwue.zpd.dachs.larex.backend.entity.TagSet tagSetEntity;
        private final de.uniwue.zpd.dachs.larex.backend.entity.NormalizationProfile normalizationProfileEntity;
        private final de.uniwue.zpd.dachs.larex.backend.entity.ValidationRuleset validationRulesetEntity;

        private WorkspaceDefaults(String codecId, String labelSetId, String dictionaryId, String tagSetId,
                                  String normalizationProfileId, String validationRulesetId,
                                  int gtIndex, List<Integer> recognitionIndices,
                                  de.uniwue.zpd.dachs.larex.backend.entity.Codec codecEntity,
                                  de.uniwue.zpd.dachs.larex.backend.entity.LabelSet labelSetEntity,
                                  de.uniwue.zpd.dachs.larex.backend.entity.ControlledDictionary dictionaryEntity,
                                  de.uniwue.zpd.dachs.larex.backend.entity.TagSet tagSetEntity,
                                  de.uniwue.zpd.dachs.larex.backend.entity.NormalizationProfile normalizationProfileEntity,
                                  de.uniwue.zpd.dachs.larex.backend.entity.ValidationRuleset validationRulesetEntity) {
            this.codecId = codecId;
            this.labelSetId = labelSetId;
            this.dictionaryId = dictionaryId;
            this.tagSetId = tagSetId;
            this.normalizationProfileId = normalizationProfileId;
            this.validationRulesetId = validationRulesetId;
            this.gtIndex = gtIndex;
            this.recognitionIndices = List.copyOf(recognitionIndices);
            this.codecEntity = codecEntity;
            this.labelSetEntity = labelSetEntity;
            this.dictionaryEntity = dictionaryEntity;
            this.tagSetEntity = tagSetEntity;
            this.normalizationProfileEntity = normalizationProfileEntity;
            this.validationRulesetEntity = validationRulesetEntity;
        }

        public static WorkspaceDefaults from(AbstractWorkspace workspace) {
            return new WorkspaceDefaults(
                    id(workspace.getCodec()), id(workspace.getLabelSet()), id(workspace.getDictionary()), id(workspace.getTagSet()),
                    id(workspace.getNormalizationProfile()), id(workspace.getValidationRuleset()),
                    workspace.getEffectiveDefaultGtIndex(), workspace.getDefaultRecognitionIndicesList(),
                    workspace.getCodec(), workspace.getLabelSet(), workspace.getDictionary(), workspace.getTagSet(),
                    workspace.getNormalizationProfile(), workspace.getValidationRuleset()
            );
        }

        public static WorkspaceDefaults from(WorkspaceDto.ProjectDefaultsProposal proposal) {
            String codecId = normalize(proposal.codecId());
            String labelSetId = normalize(proposal.labelSetId());
            String dictionaryId = normalize(proposal.dictionaryId());
            String tagSetId = normalize(proposal.tagSetId());
            String normalizationProfileId = normalize(proposal.normalizationProfileId());
            String validationRulesetId = normalize(proposal.validationRulesetId());
            int gtIndex = TextIndexDefaultsUtil.effectiveGtIndex(proposal.defaultGtIndex());
            List<Integer> recognitionIndices = TextIndexDefaultsUtil.effectiveRecognitionIndices(proposal.defaultRecognitionIndices());
            return new WorkspaceDefaults(codecId, labelSetId, dictionaryId, tagSetId,
                    normalizationProfileId, validationRulesetId, gtIndex, recognitionIndices,
                    null, null, null, null, null, null);
        }

        private static String id(Object entity) {
            if (entity == null) return null;
            if (entity instanceof de.uniwue.zpd.dachs.larex.backend.entity.Codec value) return value.getId();
            if (entity instanceof de.uniwue.zpd.dachs.larex.backend.entity.LabelSet value) return value.getId();
            if (entity instanceof de.uniwue.zpd.dachs.larex.backend.entity.ControlledDictionary value) return value.getId();
            if (entity instanceof de.uniwue.zpd.dachs.larex.backend.entity.TagSet value) return value.getId();
            if (entity instanceof de.uniwue.zpd.dachs.larex.backend.entity.NormalizationProfile value) return value.getId();
            if (entity instanceof de.uniwue.zpd.dachs.larex.backend.entity.ValidationRuleset value) return value.getId();
            return null;
        }

        private static String normalize(String value) {
            return value == null || value.isBlank() ? null : value;
        }
    }
}

package de.uniwue.zpd.dachs.larex.backend.service.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.uniwue.zpd.dachs.larex.backend.dto.ValidationRulesetDto;
import de.uniwue.zpd.dachs.larex.backend.entity.NormalizationProfile;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageTextContent;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.ValidationRuleset;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.PersonalWorkspace;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.TeamWorkspace;
import de.uniwue.zpd.dachs.larex.backend.exception.ResourceNotFoundException;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageTextContentRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.validation.ValidationRulesetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.PersonalWorkspaceRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.TeamWorkspaceRepository;
import de.uniwue.zpd.dachs.larex.backend.service.normalization.NormalizationProfileService;
import de.uniwue.zpd.dachs.larex.backend.service.security.AuthorizationPolicyService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Service
public class ValidationRulesetService {

    private static final TypeReference<List<ValidationRulesetDto.Rule>> RULE_LIST_TYPE = new TypeReference<>() {};

    private final ValidationRulesetRepository validationRulesetRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final AuthorizationPolicyService authorizationPolicyService;
    private final ObjectMapper objectMapper;
    private final ProjectRepository projectRepository;
    private final PageRepository pageRepository;
    private final PageTextContentRepository pageTextContentRepository;
    private final PersonalWorkspaceRepository personalWorkspaceRepository;
    private final TeamWorkspaceRepository teamWorkspaceRepository;
    private final NormalizationProfileService normalizationProfileService;

    public ValidationRulesetService(ValidationRulesetRepository validationRulesetRepository,
                                    WorkspaceAccessService workspaceAccessService,
                                    AuthorizationPolicyService authorizationPolicyService,
                                    ObjectMapper objectMapper,
                                    ProjectRepository projectRepository,
                                    PageRepository pageRepository,
                                    PageTextContentRepository pageTextContentRepository,
                                    PersonalWorkspaceRepository personalWorkspaceRepository,
                                    TeamWorkspaceRepository teamWorkspaceRepository,
                                    NormalizationProfileService normalizationProfileService) {
        this.validationRulesetRepository = validationRulesetRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.authorizationPolicyService = authorizationPolicyService;
        this.objectMapper = objectMapper;
        this.projectRepository = projectRepository;
        this.pageRepository = pageRepository;
        this.pageTextContentRepository = pageTextContentRepository;
        this.personalWorkspaceRepository = personalWorkspaceRepository;
        this.teamWorkspaceRepository = teamWorkspaceRepository;
        this.normalizationProfileService = normalizationProfileService;
    }

    @Transactional(readOnly = true)
    public List<ValidationRulesetDto.SummaryResponse> getRulesets(String userId, String workspaceId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        return validationRulesetRepository.findByWorkspaceId(workspaceId).stream()
                .map(ruleset -> toSummary(ruleset, userId))
                .toList();
    }

    @Transactional(readOnly = true)
    public ValidationRulesetDto.Response getRuleset(String userId, String workspaceId, String rulesetId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        return toResponse(requireRuleset(workspaceId, rulesetId), userId);
    }

    @Transactional
    public ValidationRulesetDto.Response createRuleset(String userId,
                                                       String workspaceId,
                                                       ValidationRulesetDto.CreateOrUpdateRequest request) {
        workspaceAccessService.requireManageUtilitiesAccess(workspaceId, userId);
        String name = request.name().trim();
        if (validationRulesetRepository.existsByNameAndWorkspaceId(name, workspaceId)) {
            throw new IllegalArgumentException("Validation ruleset with name '" + name + "' already exists in this workspace");
        }
        ValidationRuleset ruleset = new ValidationRuleset();
        ruleset.setWorkspaceId(workspaceId);
        apply(ruleset, request);
        return toResponse(validationRulesetRepository.save(ruleset), userId);
    }

    @Transactional
    public ValidationRulesetDto.Response updateRuleset(String userId,
                                                       String workspaceId,
                                                       String rulesetId,
                                                       ValidationRulesetDto.CreateOrUpdateRequest request) {
        workspaceAccessService.requireManageUtilitiesAccess(workspaceId, userId);
        ValidationRuleset ruleset = requireRuleset(workspaceId, rulesetId);
        String nextName = request.name().trim();
        if (!ruleset.getName().equals(nextName) && validationRulesetRepository.existsByNameAndWorkspaceId(nextName, workspaceId)) {
            throw new IllegalArgumentException("Validation ruleset with name '" + nextName + "' already exists in this workspace");
        }
        apply(ruleset, request);
        return toResponse(validationRulesetRepository.save(ruleset), userId);
    }

    @Transactional
    public void deleteRuleset(String userId, String workspaceId, String rulesetId) {
        workspaceAccessService.requireManageUtilitiesAccess(workspaceId, userId);
        ValidationRuleset ruleset = requireRuleset(workspaceId, rulesetId);
        clearAssignments(workspaceId, rulesetId);
        validationRulesetRepository.delete(ruleset);
    }

    @Transactional(readOnly = true)
    public ValidationRulesetDto.ValidateAgainstSourcesResponse validateAgainstSources(String userId,
                                                                                      String workspaceId,
                                                                                      String rulesetId,
                                                                                      ValidationRulesetDto.ValidateAgainstSourcesRequest request) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        ValidationRuleset ruleset = requireRuleset(workspaceId, rulesetId);
        List<ValidationRulesetDto.Rule> rules = readRules(ruleset);
        VariantSelection variantSelection = resolveVariantSelection(request.variantScope(), request.variantIndex(), request.unindexedOnly());
        List<PageTextContent> rows = loadSourceRows(workspaceId, request.sources(), variantSelection);

        Map<String, MutableRuleResult> resultByRuleId = new LinkedHashMap<>();
        int analyzedProjectCount = 0;
        int analyzedPageCount = 0;
        Set<String> analyzedProjectIds = new LinkedHashSet<>();
        Set<String> analyzedPageIds = new LinkedHashSet<>();

        List<CompiledRule> compiledRules = rules.stream().map(this::compileRule).toList();
        for (PageTextContent row : rows) {
            if (row == null || row.getPage() == null) {
                continue;
            }
            String text = row.getTextContent();
            if (text == null || text.isBlank()) {
                continue;
            }
            Project project = row.getPage().getProject();
            NormalizationProfile profile = normalizationProfileService.findEffectiveProfileForProject(project).orElse(null);
            String targetText = normalizationProfileService.normalizeText(profile, text);

            analyzedProjectIds.add(project.getId());
            analyzedPageIds.add(row.getPage().getId());

            for (CompiledRule compiledRule : compiledRules) {
                Matcher matcher = compiledRule.pattern().matcher(targetText);
                while (matcher.find()) {
                    MutableRuleResult mutable = resultByRuleId.computeIfAbsent(compiledRule.rule().id(), ignored ->
                            new MutableRuleResult(compiledRule.rule().id(), compiledRule.rule().name(), compiledRule.rule().severity(),
                                    resolveRuleMessage(compiledRule.rule()), new LinkedHashSet<>(), new LinkedHashSet<>(), 0));
                    mutable.occurrenceCount++;
                    String sample = matcher.group();
                    if (sample != null && !sample.isBlank() && mutable.matchedSamples.size() < 5) {
                        mutable.matchedSamples.add(sample);
                    }
                    mutable.pageRefs.add(new ValidationRulesetDto.RulePageRef(
                            project.getId(),
                            project.getName(),
                            row.getPage().getId(),
                            row.getPage().getName()
                    ));
                }
            }
        }

        analyzedProjectCount = analyzedProjectIds.size();
        analyzedPageCount = analyzedPageIds.size();

        List<ValidationRulesetDto.RuleResult> ruleResults = resultByRuleId.values().stream()
                .sorted(Comparator
                        .comparing((MutableRuleResult result) -> severityRank(result.severity)).reversed()
                        .thenComparing(result -> result.ruleName, String.CASE_INSENSITIVE_ORDER))
                .map(result -> new ValidationRulesetDto.RuleResult(
                        result.ruleId,
                        result.ruleName,
                        result.severity,
                        result.message,
                        result.occurrenceCount,
                        new ArrayList<>(result.matchedSamples),
                        result.pageRefs.stream().toList()
                ))
                .toList();

        int totalOccurrenceCount = resultByRuleId.values().stream().mapToInt(result -> result.occurrenceCount).sum();
        String message = totalOccurrenceCount == 0
                ? "Validation ruleset found no matching issues in selected sources"
                : "Validation ruleset found matching issues in selected sources";

        return new ValidationRulesetDto.ValidateAgainstSourcesResponse(
                totalOccurrenceCount == 0,
                analyzedProjectCount,
                analyzedPageCount,
                totalOccurrenceCount,
                ruleResults,
                message
        );
    }

    @Transactional(readOnly = true)
    public List<ValidationRulesetDto.Rule> readRules(ValidationRuleset ruleset) {
        try {
            return objectMapper.readValue(ruleset.getRulesJson(), RULE_LIST_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize validation ruleset " + ruleset.getId(), e);
        }
    }

    private ValidationRuleset requireRuleset(String workspaceId, String rulesetId) {
        return validationRulesetRepository.findByIdAndWorkspaceId(rulesetId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Validation ruleset", rulesetId));
    }

    private void apply(ValidationRuleset ruleset, ValidationRulesetDto.CreateOrUpdateRequest request) {
        ruleset.setName(request.name().trim());
        ruleset.setDescription(trimToNull(request.description()));
        ruleset.setTags(normalizeTags(request.tags()));
        List<ValidationRulesetDto.Rule> rules = request.rules().stream()
                .map(rule -> new ValidationRulesetDto.Rule(
                        trimToNull(rule.id()) == null ? request.name().trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-") + "-" + Math.abs(rule.name().hashCode()) : rule.id().trim(),
                        rule.name().trim(),
                        trimToNull(rule.description()),
                        rule.severity() == null ? ValidationRulesetDto.Severity.WARNING : rule.severity(),
                        rule.pattern(),
                        trimToNull(rule.flags()),
                        trimToNull(rule.message())
                ))
                .toList();
        for (ValidationRulesetDto.Rule rule : rules) {
            compileRule(rule);
        }
        try {
            ruleset.setRulesJson(objectMapper.writeValueAsString(rules));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize validation rules", e);
        }
    }

    private ValidationRulesetDto.Response toResponse(ValidationRuleset ruleset, String userId) {
        return new ValidationRulesetDto.Response(
                ruleset.getId(),
                ruleset.getName(),
                ruleset.getDescription(),
                ruleset.getTags(),
                readRules(ruleset),
                ruleset.getCreated(),
                ruleset.getUpdated(),
                authorizationPolicyService.resolveWorkspaceResourceCapabilities(ruleset.getWorkspaceId(), userId)
        );
    }

    private ValidationRulesetDto.SummaryResponse toSummary(ValidationRuleset ruleset, String userId) {
        return new ValidationRulesetDto.SummaryResponse(
                ruleset.getId(),
                ruleset.getName(),
                ruleset.getDescription(),
                ruleset.getTags(),
                readRules(ruleset).size(),
                ruleset.getCreated(),
                ruleset.getUpdated(),
                authorizationPolicyService.resolveWorkspaceResourceCapabilities(ruleset.getWorkspaceId(), userId)
        );
    }

    private CompiledRule compileRule(ValidationRulesetDto.Rule rule) {
        try {
            return new CompiledRule(rule, Pattern.compile(rule.pattern(), parseFlags(rule.flags())));
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("Invalid regex pattern for rule '" + rule.name() + "': " + e.getDescription(), e);
        }
    }

    private int parseFlags(String flags) {
        if (flags == null || flags.isBlank()) {
            return 0;
        }
        int compiled = 0;
        for (char flag : flags.toCharArray()) {
            compiled |= switch (flag) {
                case 'i', 'I' -> Pattern.CASE_INSENSITIVE;
                case 'm', 'M' -> Pattern.MULTILINE;
                case 's', 'S' -> Pattern.DOTALL;
                case 'u', 'U' -> Pattern.UNICODE_CASE;
                case 'x', 'X' -> Pattern.COMMENTS;
                default -> throw new IllegalArgumentException("Unsupported regex flag: " + flag);
            };
        }
        return compiled;
    }

    private String resolveRuleMessage(ValidationRulesetDto.Rule rule) {
        return trimToNull(rule.message()) != null ? rule.message().trim() : ("Matched rule: " + rule.name());
    }

    private int severityRank(ValidationRulesetDto.Severity severity) {
        if (severity == null) return 0;
        return switch (severity) {
            case INFO -> 1;
            case WARNING -> 2;
            case ERROR -> 3;
        };
    }

    private List<PageTextContent> loadSourceRows(String workspaceId,
                                                 List<ValidationRulesetDto.ProjectScope> sources,
                                                 VariantSelection variantSelection) {
        if (sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("At least one source is required");
        }

        List<PageTextContent> rows = new ArrayList<>();
        for (ValidationRulesetDto.ProjectScope source : sources) {
            if (source == null || trimToNull(source.projectId()) == null) {
                throw new IllegalArgumentException("Project ID is required in every source");
            }
            Project project = projectRepository.findByIdAndLibraryWorkspaceId(source.projectId().trim(), workspaceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Project", source.projectId()));
            PageScopeResolution pageScope = resolvePageScope(project.getId(), source.pageIds());
            if (pageScope.pageIds().isEmpty()) {
                continue;
            }
            List<PageTextContent> projectRows = pageScope.allPagesSelected()
                    ? pageTextContentRepository.findByProjectId(project.getId())
                    : pageTextContentRepository.findByProjectIdAndPageIds(project.getId(), new ArrayList<>(pageScope.pageIds()));
            for (PageTextContent row : projectRows) {
                if (matchesVariantSelection(row.getVariantIndex(), variantSelection)) {
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private PageScopeResolution resolvePageScope(String projectId, List<String> requestedPageIds) {
        if (requestedPageIds == null || requestedPageIds.isEmpty()) {
            List<Page> allPages = pageRepository.findByProjectId(projectId);
            Set<String> pageIds = allPages.stream().map(Page::getId).collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
            return new PageScopeResolution(pageIds, true);
        }

        Set<String> normalizedRequested = requestedPageIds.stream()
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
        if (normalizedRequested.isEmpty()) {
            return resolvePageScope(projectId, List.of());
        }

        List<Page> matchedPages = pageRepository.findByIdInAndProjectId(new ArrayList<>(normalizedRequested), projectId);
        Set<String> matchedIds = matchedPages.stream().map(Page::getId).collect(HashSet::new, HashSet::add, HashSet::addAll);
        if (matchedIds.size() != normalizedRequested.size()) {
            Set<String> missing = new LinkedHashSet<>(normalizedRequested);
            missing.removeAll(matchedIds);
            throw new IllegalArgumentException("Some page IDs do not belong to project '" + projectId + "': " + String.join(",", missing));
        }

        return new PageScopeResolution(normalizedRequested, false);
    }

    private VariantSelection resolveVariantSelection(ValidationRulesetDto.VariantScope scope,
                                                     Integer variantIndex,
                                                     Boolean unindexedOnly) {
        if (Boolean.TRUE.equals(unindexedOnly)) {
            if (variantIndex != null) {
                throw new IllegalArgumentException("variantIndex and unindexedOnly cannot be set at the same time");
            }
            return new VariantSelection(VariantSelectionMode.UNINDEXED_ONLY, null);
        }
        if (variantIndex != null) {
            if (variantIndex < 0) {
                throw new IllegalArgumentException("variantIndex must be >= 0");
            }
            return new VariantSelection(VariantSelectionMode.SPECIFIC_INDEX, variantIndex);
        }
        ValidationRulesetDto.VariantScope resolvedScope = scope == null ? ValidationRulesetDto.VariantScope.ALL : scope;
        if (resolvedScope == ValidationRulesetDto.VariantScope.PRIMARY) {
            return new VariantSelection(VariantSelectionMode.PRIMARY_COMPAT, null);
        }
        return new VariantSelection(VariantSelectionMode.ALL, null);
    }

    private boolean matchesVariantSelection(Integer rowVariantIndex, VariantSelection selection) {
        return switch (selection.mode()) {
            case ALL -> true;
            case PRIMARY_COMPAT -> rowVariantIndex == null || rowVariantIndex.intValue() == 0;
            case SPECIFIC_INDEX -> rowVariantIndex != null && rowVariantIndex.equals(selection.variantIndex());
            case UNINDEXED_ONLY -> rowVariantIndex == null;
        };
    }

    private void clearAssignments(String workspaceId, String rulesetId) {
        List<Project> projects = projectRepository.findByLibraryWorkspaceIdAndValidationRulesetId(workspaceId, rulesetId);
        if (!projects.isEmpty()) {
            for (Project project : projects) {
                project.setValidationRuleset(null);
            }
            projectRepository.saveAll(projects);
        }

        PersonalWorkspace personalWorkspace = personalWorkspaceRepository.findById(workspaceId).orElse(null);
        if (personalWorkspace != null
                && personalWorkspace.getValidationRuleset() != null
                && Objects.equals(personalWorkspace.getValidationRuleset().getId(), rulesetId)) {
            personalWorkspace.setValidationRuleset(null);
            personalWorkspaceRepository.save(personalWorkspace);
            return;
        }

        TeamWorkspace teamWorkspace = teamWorkspaceRepository.findById(workspaceId).orElse(null);
        if (teamWorkspace != null
                && teamWorkspace.getValidationRuleset() != null
                && Objects.equals(teamWorkspace.getValidationRuleset().getId(), rulesetId)) {
            teamWorkspace.setValidationRuleset(null);
            teamWorkspaceRepository.save(teamWorkspace);
        }
    }

    private List<String> normalizeTags(Collection<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        Map<String, String> deduped = new LinkedHashMap<>();
        for (String tag : tags) {
            String trimmed = trimToNull(tag);
            if (trimmed == null) {
                continue;
            }
            deduped.put(trimmed.toLowerCase(Locale.ROOT), trimmed);
        }
        return new ArrayList<>(deduped.values());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record CompiledRule(ValidationRulesetDto.Rule rule, Pattern pattern) {
    }

    private record PageScopeResolution(Set<String> pageIds, boolean allPagesSelected) {
    }

    private enum VariantSelectionMode {
        ALL,
        PRIMARY_COMPAT,
        SPECIFIC_INDEX,
        UNINDEXED_ONLY
    }

    private record VariantSelection(VariantSelectionMode mode, Integer variantIndex) {
    }

    private static final class MutableRuleResult {
        private final String ruleId;
        private final String ruleName;
        private final ValidationRulesetDto.Severity severity;
        private final String message;
        private final Set<String> matchedSamples;
        private final Set<ValidationRulesetDto.RulePageRef> pageRefs;
        private int occurrenceCount;

        private MutableRuleResult(String ruleId,
                                  String ruleName,
                                  ValidationRulesetDto.Severity severity,
                                  String message,
                                  Set<String> matchedSamples,
                                  Set<ValidationRulesetDto.RulePageRef> pageRefs,
                                  int occurrenceCount) {
            this.ruleId = ruleId;
            this.ruleName = ruleName;
            this.severity = severity;
            this.message = message;
            this.matchedSamples = matchedSamples;
            this.pageRefs = pageRefs;
            this.occurrenceCount = occurrenceCount;
        }
    }
}

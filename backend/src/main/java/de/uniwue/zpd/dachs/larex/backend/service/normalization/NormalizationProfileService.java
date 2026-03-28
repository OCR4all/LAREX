package de.uniwue.zpd.dachs.larex.backend.service.normalization;

import de.uniwue.zpd.dachs.larex.backend.dto.NormalizationProfileDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.region.RegionDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.TextContentVariantDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.TextLineDto;
import de.uniwue.zpd.dachs.larex.backend.entity.NormalizationProfile;
import de.uniwue.zpd.dachs.larex.backend.entity.NormalizationReplacementRule;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageTextContent;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.AbstractWorkspace;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.PersonalWorkspace;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.TeamWorkspace;
import de.uniwue.zpd.dachs.larex.backend.exception.ResourceNotFoundException;
import de.uniwue.zpd.dachs.larex.backend.repository.normalization.NormalizationProfileRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageTextContentRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.PersonalWorkspaceRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.TeamWorkspaceRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceQueryService;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.application.AnnotationProcessingService;
import de.uniwue.zpd.dachs.larex.backend.service.security.AuthorizationPolicyService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import de.uniwue.zpd.dachs.larex.backend.util.TextNormalizationUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Comparator;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.IntStream;

@Service
public class NormalizationProfileService {

    private final NormalizationProfileRepository normalizationProfileRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final AuthorizationPolicyService authorizationPolicyService;
    private final ProjectRepository projectRepository;
    private final PageRepository pageRepository;
    private final PageTextContentRepository pageTextContentRepository;
    private final PageXmlRepository pageXmlRepository;
    private final PersonalWorkspaceRepository personalWorkspaceRepository;
    private final TeamWorkspaceRepository teamWorkspaceRepository;
    private final WorkspaceQueryService workspaceQueryService;
    private final AnnotationProcessingService annotationProcessingService;

    public NormalizationProfileService(NormalizationProfileRepository normalizationProfileRepository,
                                       WorkspaceAccessService workspaceAccessService,
                                       AuthorizationPolicyService authorizationPolicyService,
                                       ProjectRepository projectRepository,
                                       PageRepository pageRepository,
                                       PageTextContentRepository pageTextContentRepository,
                                       PageXmlRepository pageXmlRepository,
                                       PersonalWorkspaceRepository personalWorkspaceRepository,
                                       TeamWorkspaceRepository teamWorkspaceRepository,
                                       WorkspaceQueryService workspaceQueryService,
                                       AnnotationProcessingService annotationProcessingService) {
        this.normalizationProfileRepository = normalizationProfileRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.authorizationPolicyService = authorizationPolicyService;
        this.projectRepository = projectRepository;
        this.pageRepository = pageRepository;
        this.pageTextContentRepository = pageTextContentRepository;
        this.pageXmlRepository = pageXmlRepository;
        this.personalWorkspaceRepository = personalWorkspaceRepository;
        this.teamWorkspaceRepository = teamWorkspaceRepository;
        this.workspaceQueryService = workspaceQueryService;
        this.annotationProcessingService = annotationProcessingService;
    }

    @Transactional(readOnly = true)
    public List<NormalizationProfileDto.SummaryResponse> getProfiles(String userId, String workspaceId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        return normalizationProfileRepository.findByWorkspaceId(workspaceId).stream()
                .map(profile -> toSummary(profile, userId))
                .toList();
    }

    @Transactional(readOnly = true)
    public NormalizationProfileDto.Response getProfile(String userId, String workspaceId, String profileId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        return toResponse(requireProfile(workspaceId, profileId), userId);
    }

    @Transactional
    public NormalizationProfileDto.Response createProfile(String userId,
                                                          String workspaceId,
                                                          NormalizationProfileDto.CreateOrUpdateRequest request) {
        workspaceAccessService.requireManageUtilitiesAccess(workspaceId, userId);
        String name = request.name().trim();
        if (normalizationProfileRepository.existsByNameAndWorkspaceId(name, workspaceId)) {
            throw new IllegalArgumentException("Normalization profile with name '" + name + "' already exists in this workspace");
        }

        NormalizationProfile profile = new NormalizationProfile();
        profile.setWorkspaceId(workspaceId);
        apply(profile, request);
        return toResponse(normalizationProfileRepository.save(profile), userId);
    }

    @Transactional
    public NormalizationProfileDto.Response updateProfile(String userId,
                                                          String workspaceId,
                                                          String profileId,
                                                          NormalizationProfileDto.CreateOrUpdateRequest request) {
        workspaceAccessService.requireManageUtilitiesAccess(workspaceId, userId);
        NormalizationProfile profile = requireProfile(workspaceId, profileId);
        String nextName = request.name().trim();
        if (!profile.getName().equals(nextName) && normalizationProfileRepository.existsByNameAndWorkspaceId(nextName, workspaceId)) {
            throw new IllegalArgumentException("Normalization profile with name '" + nextName + "' already exists in this workspace");
        }
        apply(profile, request);
        return toResponse(normalizationProfileRepository.save(profile), userId);
    }

    @Transactional
    public void deleteProfile(String userId, String workspaceId, String profileId) {
        workspaceAccessService.requireManageUtilitiesAccess(workspaceId, userId);
        NormalizationProfile profile = requireProfile(workspaceId, profileId);
        clearAssignments(workspaceId, profileId);
        normalizationProfileRepository.delete(profile);
    }

    @Transactional(readOnly = true)
    public NormalizationProfileDto.NormalizeSourcesResponse normalizeSources(String userId,
                                                                             String workspaceId,
                                                                             String profileId,
                                                                             NormalizationProfileDto.NormalizeSourcesRequest request) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        NormalizationProfile profile = requireProfile(workspaceId, profileId);
        CompiledNormalizationProfile compiledProfile = compileProfile(profile);
        VariantSelection variantSelection = resolveVariantSelection(request.variantScope(), request.variantIndex(), request.unindexedOnly());

        List<PageTextContent> rows = loadSourceRows(workspaceId, request.sources(), variantSelection, request.targets());
        List<RowSnapshot> rowSnapshots = rows.stream()
                .map(this::toRowSnapshot)
                .filter(Objects::nonNull)
                .toList();

        Set<String> analyzedProjectIds = rowSnapshots.stream()
                .map(RowSnapshot::projectId)
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
        Set<String> analyzedPageIds = rowSnapshots.stream()
                .map(RowSnapshot::pageId)
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);

        List<IndexedPreview> indexedPreviews = IntStream.range(0, rowSnapshots.size())
                .parallel()
                .mapToObj(index -> buildPreview(index, rowSnapshots.get(index), compiledProfile))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(IndexedPreview::index))
                .toList();

        List<NormalizationProfileDto.NormalizePreview> previews = indexedPreviews.stream()
                .map(IndexedPreview::preview)
                .toList();
        Set<String> changedPageIds = previews.stream()
                .map(NormalizationProfileDto.NormalizePreview::pageId)
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);

        int analyzedRowCount = rowSnapshots.size();
        int changedRowCount = previews.size();

        String message = changedRowCount == 0
                ? "No normalization changes detected in selected sources"
                : "Normalization profile changes text in selected sources";

        return new NormalizationProfileDto.NormalizeSourcesResponse(
                analyzedProjectIds.size(),
                analyzedPageIds.size(),
                analyzedRowCount,
                changedRowCount,
                changedPageIds.size(),
                previews,
                message
        );
    }

    @Transactional
    public NormalizationProfileDto.ApplySourcesResponse applySources(String userId,
                                                                    String workspaceId,
                                                                    String profileId,
                                                                    NormalizationProfileDto.NormalizeSourcesRequest request) {
        workspaceAccessService.requireManageProjectsAccess(workspaceId, userId);
        NormalizationProfile profile = requireProfile(workspaceId, profileId);
        CompiledNormalizationProfile compiledProfile = compileProfile(profile);
        VariantSelection variantSelection = resolveVariantSelection(request.variantScope(), request.variantIndex(), request.unindexedOnly());

        List<PageTextContent> rows = loadSourceRows(workspaceId, request.sources(), variantSelection, request.targets());
        Set<String> analyzedProjectIds = new LinkedHashSet<>();
        Set<String> analyzedPageIds = new LinkedHashSet<>();
        Map<String, List<PageTextContent>> rowsByPageId = new LinkedHashMap<>();

        for (PageTextContent row : rows) {
            if (row == null || row.getPage() == null || row.getPage().getProject() == null) {
                continue;
            }
            analyzedProjectIds.add(row.getPage().getProject().getId());
            analyzedPageIds.add(row.getPage().getId());
            rowsByPageId.computeIfAbsent(row.getPage().getId(), ignored -> new ArrayList<>()).add(row);
        }

        int changedRowCount = 0;
        int changedPageCount = 0;

        for (Map.Entry<String, List<PageTextContent>> entry : rowsByPageId.entrySet()) {
            List<PageTextContent> pageRows = entry.getValue();
            if (pageRows == null || pageRows.isEmpty()) {
                continue;
            }

            Project project = pageRows.getFirst().getPage().getProject();
            if (project == null) {
                continue;
            }
            if (project.isLocked()) {
                throw new IllegalStateException("Project '" + project.getName() + "' is locked");
            }

            PageXml pageXml = resolveEditablePageXml(entry.getKey());
            PageDto pageDto;
            try {
                pageDto = annotationProcessingService.parseXmlToAnnotation(pageXml.getId());
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to load PAGE XML for page '" + pageRows.getFirst().getPage().getName() + "'", exception);
            }

            PageApplyResult result = applyNormalizationToPage(pageDto, pageRows, compiledProfile);
            if (result.changedRowCount() == 0) {
                continue;
            }

            try {
                annotationProcessingService.saveAnnotationToXml(pageXml.getId(), result.pageDto(), userId);
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to save normalized PAGE XML for page '" + pageRows.getFirst().getPage().getName() + "'", exception);
            }

            changedRowCount += result.changedRowCount();
            changedPageCount++;
        }

        String message = changedRowCount == 0
                ? "No normalization changes needed for the selected sources"
                : "Normalization changes were saved to PAGE XML. Search and filter indexes may take a moment to refresh.";

        return new NormalizationProfileDto.ApplySourcesResponse(
                analyzedProjectIds.size(),
                analyzedPageIds.size(),
                rows.size(),
                changedRowCount,
                changedPageCount,
                message
        );
    }

    @Transactional(readOnly = true)
    public Optional<NormalizationProfile> findEffectiveProfileForProject(Project project) {
        if (project == null) {
            return Optional.empty();
        }
        if (project.getNormalizationProfile() != null) {
            return Optional.of(project.getNormalizationProfile());
        }
        AbstractWorkspace workspace = workspaceQueryService.findWorkspaceById(project.getLibrary().getWorkspaceId()).orElse(null);
        return workspace == null ? Optional.empty() : Optional.ofNullable(workspace.getNormalizationProfile());
    }

    @Transactional(readOnly = true)
    public String normalizeText(NormalizationProfile profile, String text) {
        if (profile == null) {
            return text;
        }
        return normalizeText(compileProfile(profile), text);
    }

    @Transactional(readOnly = true)
    public TextNormalizationUtil.TraceResult normalizeTextWithTrace(NormalizationProfile profile, String text) {
        if (profile == null) {
            return new TextNormalizationUtil.TraceResult(text, List.of());
        }
        return normalizeTextWithTrace(compileProfile(profile), text);
    }

    private TextNormalizationUtil.TraceResult normalizeTextWithTrace(CompiledNormalizationProfile profile, String text) {
        return TextNormalizationUtil.normalizeWithPreparedTrace(
                text,
                profile.unicodeNormalization(),
                profile.collapseWhitespace(),
                profile.trimText(),
                profile.dehyphenateLineBreaks(),
                profile.mapLongSToS(),
                profile.expandCommonLigatures(),
                profile.normalizeQuotes(),
                profile.normalizeDashes(),
                profile.normalizeEllipsis(),
                profile.replacementRules()
        );
    }

    private String normalizeText(CompiledNormalizationProfile profile, String text) {
        return normalizeTextWithTrace(profile, text).normalizedText();
    }

    private CompiledNormalizationProfile compileProfile(NormalizationProfile profile) {
        return new CompiledNormalizationProfile(
                profile.getUnicodeNormalization(),
                profile.isCollapseWhitespace(),
                profile.isTrimText(),
                profile.isDehyphenateLineBreaks(),
                profile.isMapLongSToS(),
                profile.isExpandCommonLigatures(),
                profile.isNormalizeQuotes(),
                profile.isNormalizeDashes(),
                profile.isNormalizeEllipsis(),
                TextNormalizationUtil.prepareReplacementRules(
                        profile.getReplacementRules().stream()
                                .map(rule -> new TextNormalizationUtil.ReplacementRule(rule.getSearch(), rule.getReplacement(), rule.isRegex()))
                                .toList()
                )
        );
    }

    private NormalizationProfile requireProfile(String workspaceId, String profileId) {
        return normalizationProfileRepository.findByIdAndWorkspaceId(profileId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Normalization profile", profileId));
    }

    private void apply(NormalizationProfile profile, NormalizationProfileDto.CreateOrUpdateRequest request) {
        profile.setName(request.name().trim());
        profile.setDescription(trimToNull(request.description()));
        profile.setTags(normalizeTags(request.tags()));
        profile.setUnicodeNormalization(resolveNormalization(request.unicodeNormalization()));
        profile.setCollapseWhitespace(Boolean.TRUE.equals(request.collapseWhitespace()));
        profile.setTrimText(Boolean.TRUE.equals(request.trimText()));
        profile.setDehyphenateLineBreaks(Boolean.TRUE.equals(request.dehyphenateLineBreaks()));
        profile.setMapLongSToS(Boolean.TRUE.equals(request.mapLongSToS()));
        profile.setExpandCommonLigatures(Boolean.TRUE.equals(request.expandCommonLigatures()));
        profile.setNormalizeQuotes(Boolean.TRUE.equals(request.normalizeQuotes()));
        profile.setNormalizeDashes(Boolean.TRUE.equals(request.normalizeDashes()));
        profile.setNormalizeEllipsis(Boolean.TRUE.equals(request.normalizeEllipsis()));
        profile.setReplacementRules(normalizeReplacementRules(request.replacementRules()));
    }

    private NormalizationProfileDto.Response toResponse(NormalizationProfile profile, String userId) {
        return new NormalizationProfileDto.Response(
                profile.getId(),
                profile.getName(),
                profile.getDescription(),
                profile.getTags(),
                profile.getUnicodeNormalization(),
                profile.isCollapseWhitespace(),
                profile.isTrimText(),
                profile.isDehyphenateLineBreaks(),
                profile.isMapLongSToS(),
                profile.isExpandCommonLigatures(),
                profile.isNormalizeQuotes(),
                profile.isNormalizeDashes(),
                profile.isNormalizeEllipsis(),
                profile.getReplacementRules().stream()
                        .map(rule -> new NormalizationProfileDto.ReplacementRule(
                                rule.getSearch(),
                                rule.getReplacement(),
                                rule.isRegex()
                        ))
                        .toList(),
                profile.getCreated(),
                profile.getUpdated(),
                authorizationPolicyService.resolveWorkspaceResourceCapabilities(profile.getWorkspaceId(), userId)
        );
    }

    private NormalizationProfileDto.SummaryResponse toSummary(NormalizationProfile profile, String userId) {
        return new NormalizationProfileDto.SummaryResponse(
                profile.getId(),
                profile.getName(),
                profile.getDescription(),
                profile.getTags(),
                profile.getUnicodeNormalization(),
                profile.getCreated(),
                profile.getUpdated(),
                authorizationPolicyService.resolveWorkspaceResourceCapabilities(profile.getWorkspaceId(), userId)
        );
    }

    private List<PageTextContent> loadSourceRows(String workspaceId,
                                                 List<NormalizationProfileDto.ProjectScope> sources,
                                                 VariantSelection variantSelection,
                                                 List<NormalizationProfileDto.NormalizeTarget> targets) {
        if (sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("At least one source is required");
        }

        List<PageTextContent> rows = new ArrayList<>();
        for (NormalizationProfileDto.ProjectScope source : sources) {
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
        return filterRowsByTargets(rows, targets);
    }

    private List<PageTextContent> filterRowsByTargets(List<PageTextContent> rows,
                                                      List<NormalizationProfileDto.NormalizeTarget> targets) {
        if (targets == null || targets.isEmpty()) {
            return rows;
        }

        Set<RowTargetKey> targetKeys = new LinkedHashSet<>();
        for (NormalizationProfileDto.NormalizeTarget target : targets) {
            RowTargetKey key = toRowTargetKey(target);
            if (key != null) {
                targetKeys.add(key);
            }
        }

        if (targetKeys.isEmpty()) {
            throw new IllegalArgumentException("At least one valid normalization target is required");
        }

        return rows.stream()
                .filter(row -> targetKeys.contains(toRowTargetKey(row)))
                .toList();
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

    private VariantSelection resolveVariantSelection(NormalizationProfileDto.VariantScope scope,
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
        NormalizationProfileDto.VariantScope resolvedScope = scope == null ? NormalizationProfileDto.VariantScope.ALL : scope;
        if (resolvedScope == NormalizationProfileDto.VariantScope.PRIMARY) {
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

    private PageXml resolveEditablePageXml(String pageId) {
        return pageXmlRepository.findByPage_Id(pageId).stream()
                .filter(pageXml -> pageXml.getSchema() == XmlSchema.PAGE_XML)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No PAGE XML file found for page '" + pageId + "'"));
    }

    private PageApplyResult applyNormalizationToPage(PageDto pageDto,
                                                     List<PageTextContent> pageRows,
                                                     CompiledNormalizationProfile profile) {
        TargetMaps targetMaps = buildTargetMaps(pageRows);
        List<RegionDto> updatedRegions = new ArrayList<>();
        int changedRowCount = 0;

        for (RegionDto region : pageDto.regions() == null ? List.<RegionDto>of() : pageDto.regions()) {
            RegionApplyResult regionResult = applyNormalizationToRegion(region, targetMaps, profile);
            updatedRegions.add(regionResult.region());
            changedRowCount += regionResult.changedRowCount();
        }

        return new PageApplyResult(
                new PageDto(
                        pageDto.imageFilename(),
                        pageDto.imageWidth(),
                        pageDto.imageHeight(),
                        pageDto.imageXResolution(),
                        pageDto.imageYResolution(),
                        pageDto.imageResolutionUnit(),
                        pageDto.metadata(),
                        pageDto.pcGtsId(),
                        pageDto.type(),
                        pageDto.custom(),
                        pageDto.orientation(),
                        pageDto.primaryLanguage(),
                        pageDto.secondaryLanguage(),
                        pageDto.primaryScript(),
                        pageDto.secondaryScript(),
                        pageDto.readingDirection(),
                        pageDto.textLineOrder(),
                        pageDto.confidence(),
                        pageDto.border(),
                        pageDto.printSpace(),
                        updatedRegions,
                        pageDto.readingOrder(),
                        pageDto.alternativeImages(),
                        pageDto.labels(),
                        pageDto.userDefined(),
                        pageDto.textStyle(),
                        pageDto.layers(),
                        pageDto.relations(),
                        pageDto.formatVersion(),
                        pageDto.labelIds()
                ),
                changedRowCount
        );
    }

    private RegionApplyResult applyNormalizationToRegion(RegionDto region,
                                                         TargetMaps targetMaps,
                                                         CompiledNormalizationProfile profile) {
        VariantApplyResult ownVariants = normalizeTargetedVariants(
                region.textContentVariants(),
                targetMaps.regionTargetsById().get(trimToNull(region.id())),
                profile
        );

        List<TextLineDto> updatedTextLines = null;
        int changedRowCount = ownVariants.changedRowCount();
        if (region.textLines() != null) {
            updatedTextLines = new ArrayList<>(region.textLines().size());
            for (TextLineDto textLine : region.textLines()) {
                TextLineApplyResult textLineResult = applyNormalizationToTextLine(textLine, targetMaps, profile);
                updatedTextLines.add(textLineResult.textLine());
                changedRowCount += textLineResult.changedRowCount();
            }
        }

        List<RegionDto> updatedNestedRegions = null;
        if (region.nestedRegions() != null) {
            updatedNestedRegions = new ArrayList<>(region.nestedRegions().size());
            for (RegionDto nestedRegion : region.nestedRegions()) {
                RegionApplyResult nestedRegionResult = applyNormalizationToRegion(nestedRegion, targetMaps, profile);
                updatedNestedRegions.add(nestedRegionResult.region());
                changedRowCount += nestedRegionResult.changedRowCount();
            }
        }

        return new RegionApplyResult(
                new RegionDto(
                        region.id(),
                        region.kind(),
                        region.coords(),
                        updatedTextLines,
                        ownVariants.variants(),
                        region.alternativeImages(),
                        region.labels(),
                        region.userDefined(),
                        region.roles(),
                        region.grid(),
                        region.textStyle(),
                        region.type(),
                        region.orientation(),
                        region.textColour(),
                        region.bgColour(),
                        region.reverseVideo(),
                        region.fontSize(),
                        region.fontFamily(),
                        region.serif(),
                        region.monospace(),
                        region.xHeight(),
                        region.leading(),
                        region.kerning(),
                        region.align(),
                        region.textColourRgb(),
                        region.bgColourRgb(),
                        region.readingDirection(),
                        region.readingOrientation(),
                        region.textLineOrder(),
                        region.indented(),
                        region.primaryLanguage(),
                        region.secondaryLanguage(),
                        region.primaryScript(),
                        region.secondaryScript(),
                        region.production(),
                        region.numColours(),
                        region.embText(),
                        region.colourDepth(),
                        region.lineColour(),
                        region.lineSeparators(),
                        region.rows(),
                        region.columns(),
                        region.colour(),
                        region.penColour(),
                        region.borderPresent(),
                        updatedNestedRegions,
                        region.confidence(),
                        region.custom(),
                        region.comments(),
                        region.continuation(),
                        region.labelIds()
                ),
                changedRowCount
        );
    }

    private TextLineApplyResult applyNormalizationToTextLine(TextLineDto textLine,
                                                             TargetMaps targetMaps,
                                                             CompiledNormalizationProfile profile) {
        VariantApplyResult variants = normalizeTargetedVariants(
                textLine.textContentVariants(),
                targetMaps.textLineTargetsById().get(trimToNull(textLine.id())),
                profile
        );

        return new TextLineApplyResult(
                new TextLineDto(
                        textLine.id(),
                        textLine.coords(),
                        textLine.baseline(),
                        variants.variants(),
                        textLine.words(),
                        textLine.alternativeImages(),
                        textLine.labels(),
                        textLine.userDefined(),
                        textLine.textStyle(),
                        textLine.bold(),
                        textLine.italic(),
                        textLine.underlined(),
                        textLine.underlineStyle(),
                        textLine.subscript(),
                        textLine.superscript(),
                        textLine.strikethrough(),
                        textLine.smallCaps(),
                        textLine.letterSpaced(),
                        textLine.primaryLanguage(),
                        textLine.primaryScript(),
                        textLine.secondaryScript(),
                        textLine.readingDirection(),
                        textLine.production(),
                        textLine.confidence(),
                        textLine.index(),
                        textLine.custom(),
                        textLine.comments()
                ),
                variants.changedRowCount()
        );
    }

    private VariantApplyResult normalizeTargetedVariants(List<TextContentVariantDto> variants,
                                                         Set<Integer> targetedVariantKeys,
                                                         CompiledNormalizationProfile profile) {
        if (variants == null || variants.isEmpty() || targetedVariantKeys == null || targetedVariantKeys.isEmpty()) {
            return new VariantApplyResult(variants, 0);
        }

        List<TextContentVariantDto> updatedVariants = new ArrayList<>(variants.size());
        int changedRowCount = 0;

        for (TextContentVariantDto variant : variants) {
            if (variant == null || !targetedVariantKeys.contains(normalizeVariantKey(variant.index()))) {
                updatedVariants.add(variant);
                continue;
            }

            String normalizedUnicode = variant.unicode() == null ? null : normalizeText(profile, variant.unicode());
            String normalizedPlainText = variant.plainText() == null ? null : normalizeText(profile, variant.plainText());
            boolean changed = !Objects.equals(variant.unicode(), normalizedUnicode)
                    || !Objects.equals(variant.plainText(), normalizedPlainText);

            if (changed) {
                changedRowCount++;
                updatedVariants.add(new TextContentVariantDto(
                        normalizedUnicode,
                        normalizedPlainText,
                        variant.confidence(),
                        variant.index(),
                        variant.dataType(),
                        variant.dataTypeDetails(),
                        variant.comments()
                ));
            } else {
                updatedVariants.add(variant);
            }
        }

        return new VariantApplyResult(updatedVariants, changedRowCount);
    }

    private TargetMaps buildTargetMaps(List<PageTextContent> pageRows) {
        Map<String, Set<Integer>> textLineTargetsById = new HashMap<>();
        Map<String, Set<Integer>> regionTargetsById = new HashMap<>();

        for (PageTextContent row : pageRows) {
            if (row == null) {
                continue;
            }

            String textLineId = trimToNull(row.getTextLineId());
            if (textLineId != null) {
                textLineTargetsById.computeIfAbsent(textLineId, ignored -> new LinkedHashSet<>())
                        .add(normalizeVariantKey(row.getVariantIndex()));
            }

            String regionId = trimToNull(row.getRegionId());
            if (regionId != null) {
                regionTargetsById.computeIfAbsent(regionId, ignored -> new LinkedHashSet<>())
                        .add(normalizeVariantKey(row.getVariantIndex()));
            }
        }

        return new TargetMaps(textLineTargetsById, regionTargetsById);
    }

    private int normalizeVariantKey(Integer variantIndex) {
        return variantIndex == null ? 0 : variantIndex;
    }

    private void clearAssignments(String workspaceId, String profileId) {
        List<Project> projects = projectRepository.findByLibraryWorkspaceIdAndNormalizationProfileId(workspaceId, profileId);
        if (!projects.isEmpty()) {
            for (Project project : projects) {
                project.setNormalizationProfile(null);
            }
            projectRepository.saveAll(projects);
        }

        PersonalWorkspace personalWorkspace = personalWorkspaceRepository.findById(workspaceId).orElse(null);
        if (personalWorkspace != null
                && personalWorkspace.getNormalizationProfile() != null
                && Objects.equals(personalWorkspace.getNormalizationProfile().getId(), profileId)) {
            personalWorkspace.setNormalizationProfile(null);
            personalWorkspaceRepository.save(personalWorkspace);
            return;
        }

        TeamWorkspace teamWorkspace = teamWorkspaceRepository.findById(workspaceId).orElse(null);
        if (teamWorkspace != null
                && teamWorkspace.getNormalizationProfile() != null
                && Objects.equals(teamWorkspace.getNormalizationProfile().getId(), profileId)) {
            teamWorkspace.setNormalizationProfile(null);
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

    private String resolveNormalization(String normalization) {
        String resolved = trimToNull(normalization);
        if (resolved == null) {
            return "NONE";
        }
        String upper = resolved.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "NFC", "NFD", "NFKC", "NFKD", "NONE" -> upper;
            default -> throw new IllegalArgumentException("Unsupported Unicode normalization: " + normalization);
        };
    }

    private List<NormalizationReplacementRule> normalizeReplacementRules(List<NormalizationProfileDto.ReplacementRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }

        List<NormalizationReplacementRule> normalizedRules = new ArrayList<>(rules.size());
        for (int index = 0; index < rules.size(); index++) {
            NormalizationProfileDto.ReplacementRule rule = rules.get(index);
            if (rule == null) {
                continue;
            }

            String search = trimToNull(rule.search());
            if (search == null) {
                throw new IllegalArgumentException("Replacement rule " + (index + 1) + " requires a search value");
            }

            boolean regex = Boolean.TRUE.equals(rule.regex());
            if (regex) {
                try {
                    Pattern.compile(search);
                } catch (PatternSyntaxException exception) {
                    throw new IllegalArgumentException("Replacement rule " + (index + 1) + " has an invalid regex pattern: " + exception.getDescription());
                }
            }

            NormalizationReplacementRule normalizedRule = new NormalizationReplacementRule();
            normalizedRule.setSearch(search);
            normalizedRule.setReplacement(rule.replacement() == null ? "" : rule.replacement());
            normalizedRule.setRegex(regex);
            normalizedRules.add(normalizedRule);
        }
        return normalizedRules;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private RowSnapshot toRowSnapshot(PageTextContent row) {
        if (row == null || row.getPage() == null || row.getPage().getProject() == null) {
            return null;
        }
        String originalText = row.getTextContent();
        if (originalText == null) {
            return null;
        }

        return new RowSnapshot(
                row.getPage().getProject().getId(),
                row.getPage().getProject().getName(),
                row.getPage().getId(),
                row.getPage().getName(),
                row.getTextLineId(),
                row.getRegionId(),
                row.getVariantIndex(),
                originalText
        );
    }

    private IndexedPreview buildPreview(int index,
                                        RowSnapshot row,
                                        CompiledNormalizationProfile profile) {
        TextNormalizationUtil.TraceResult traceResult = normalizeTextWithTrace(profile, row.originalText());
        String normalized = traceResult.normalizedText();
        if (Objects.equals(row.originalText(), normalized)) {
            return null;
        }

        return new IndexedPreview(
                index,
                new NormalizationProfileDto.NormalizePreview(
                        row.projectId(),
                        row.projectName(),
                        row.pageId(),
                        row.pageName(),
                        row.textLineId(),
                        row.regionId(),
                        row.variantIndex(),
                        row.originalText(),
                        normalized,
                        traceResult.matchedRules().stream()
                                .map(match -> new NormalizationProfileDto.NormalizeMatch(
                                        match.key(),
                                        match.label(),
                                        match.description(),
                                        match.manual(),
                                        match.regex()
                                ))
                                .toList()
                )
        );
    }

    private RowTargetKey toRowTargetKey(PageTextContent row) {
        if (row == null || row.getPage() == null) {
            return null;
        }
        return new RowTargetKey(
                row.getPage().getId(),
                trimToNull(row.getTextLineId()),
                trimToNull(row.getRegionId()),
                row.getVariantIndex()
        );
    }

    private RowTargetKey toRowTargetKey(NormalizationProfileDto.NormalizeTarget target) {
        if (target == null) {
            return null;
        }
        String pageId = trimToNull(target.pageId());
        if (pageId == null) {
            return null;
        }
        return new RowTargetKey(
                pageId,
                trimToNull(target.textLineId()),
                trimToNull(target.regionId()),
                target.variantIndex()
        );
    }

    private record TargetMaps(Map<String, Set<Integer>> textLineTargetsById,
                              Map<String, Set<Integer>> regionTargetsById) {
    }

    private record VariantApplyResult(List<TextContentVariantDto> variants, int changedRowCount) {
    }

    private record TextLineApplyResult(TextLineDto textLine, int changedRowCount) {
    }

    private record RegionApplyResult(RegionDto region, int changedRowCount) {
    }

    private record PageApplyResult(PageDto pageDto, int changedRowCount) {
    }

    private record PageScopeResolution(Set<String> pageIds, boolean allPagesSelected) {
    }

    private record RowTargetKey(String pageId, String textLineId, String regionId, Integer variantIndex) {
    }

    private record RowSnapshot(String projectId,
                               String projectName,
                               String pageId,
                               String pageName,
                               String textLineId,
                               String regionId,
                               Integer variantIndex,
                               String originalText) {
    }

    private record IndexedPreview(int index, NormalizationProfileDto.NormalizePreview preview) {
    }

    private record CompiledNormalizationProfile(String unicodeNormalization,
                                                boolean collapseWhitespace,
                                                boolean trimText,
                                                boolean dehyphenateLineBreaks,
                                                boolean mapLongSToS,
                                                boolean expandCommonLigatures,
                                                boolean normalizeQuotes,
                                                boolean normalizeDashes,
                                                boolean normalizeEllipsis,
                                                List<TextNormalizationUtil.PreparedReplacementRule> replacementRules) {
    }

    private enum VariantSelectionMode {
        ALL,
        PRIMARY_COMPAT,
        SPECIFIC_INDEX,
        UNINDEXED_ONLY
    }

    private record VariantSelection(VariantSelectionMode mode, Integer variantIndex) {
    }
}

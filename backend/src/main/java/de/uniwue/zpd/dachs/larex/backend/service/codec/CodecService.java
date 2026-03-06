package de.uniwue.zpd.dachs.larex.backend.service.codec;

import de.uniwue.zpd.dachs.larex.backend.dto.CodecDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Codec;
import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageTextContent;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.exception.ResourceNotFoundException;
import de.uniwue.zpd.dachs.larex.backend.repository.codec.CodecRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.library.LibraryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageTextContentRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.service.security.AuthorizationPolicyService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class CodecService {

    private final CodecRepository codecRepository;
    private final LibraryRepository libraryRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final ProjectRepository projectRepository;
    private final PageRepository pageRepository;
    private final PageTextContentRepository pageTextContentRepository;
    private final AuthorizationPolicyService authorizationPolicyService;

    public CodecService(CodecRepository codecRepository,
                        LibraryRepository libraryRepository,
                        WorkspaceAccessService workspaceAccessService,
                        ProjectRepository projectRepository,
                        PageRepository pageRepository,
                        PageTextContentRepository pageTextContentRepository,
                        AuthorizationPolicyService authorizationPolicyService) {
        this.codecRepository = codecRepository;
        this.libraryRepository = libraryRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.projectRepository = projectRepository;
        this.pageRepository = pageRepository;
        this.pageTextContentRepository = pageTextContentRepository;
        this.authorizationPolicyService = authorizationPolicyService;
    }

    @CacheEvict(value = "codecs", allEntries = true)
    public CodecDto.Response createCodec(String userId, String workspaceId, CodecDto.CreateOrUpdateRequest request) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        Library library = libraryRepository.findByWorkspaceId(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Library not found for workspace: " + workspaceId));

        if (codecRepository.existsByNameAndLibraryId(request.name(), library.getId())) {
            throw new IllegalArgumentException("Codec with name '" + request.name() + "' already exists in this workspace");
        }

        Codec codec = new Codec(request.name(), library);
        codec.setDescription(request.description());

        codec.getTags().clear();
        codec.getTags().addAll(normalizeTags(request.tags()));

        if (request.codec() != null && !request.codec().isEmpty()) {
            for (String character : request.codec()) {
                codec.addCharacter(character);
            }
        }

        codec = codecRepository.save(codec);
        return convertToCodecResponse(codec, userId);
    }

    @CacheEvict(value = "codecs", allEntries = true)
    public CodecDto.Response updateCodec(String userId, String workspaceId, String codecId, CodecDto.CreateOrUpdateRequest request) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        Codec codec = codecRepository.findByIdAndLibraryWorkspaceId(codecId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Codec not found: " + codecId));

        if (!codec.getName().equals(request.name())
                && codecRepository.existsByNameAndLibraryId(request.name(), codec.getLibrary().getId())) {
            throw new IllegalArgumentException("Codec with name '" + request.name() + "' already exists in this workspace");
        }

        codec.setName(request.name());
        codec.setDescription(request.description());

        codec.getTags().clear();
        codec.getTags().addAll(normalizeTags(request.tags()));

        codec.getCharacters().clear();
        if (request.codec() != null && !request.codec().isEmpty()) {
            for (String character : request.codec()) {
                codec.addCharacter(character);
            }
        }

        codec = codecRepository.save(codec);
        return convertToCodecResponse(codec, userId);
    }

    @CacheEvict(value = "codecs", allEntries = true)
    public void deleteCodec(String userId, String workspaceId, String codecId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        Codec codec = codecRepository.findByIdAndLibraryWorkspaceId(codecId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Codec not found: " + codecId));

        codecRepository.delete(codec);
    }

    @Cacheable(value = "codecs", key = "#workspaceId + ':list'")
    @Transactional(readOnly = true)
    public List<CodecDto.SummaryResponse> getCodecs(String userId, String workspaceId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        List<Codec> codecs = codecRepository.findByLibraryWorkspaceId(workspaceId);
        return codecs.stream()
                .map(codec -> convertToCodecSummaryResponse(codec, userId))
                .collect(Collectors.toList());
    }

    @Cacheable(value = "codecs", key = "#workspaceId + ':' + #codecId")
    @Transactional(readOnly = true)
    public CodecDto.Response getCodec(String userId, String workspaceId, String codecId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        Codec codec = codecRepository.findByIdAndLibraryWorkspaceId(codecId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Codec not found: " + codecId));

        return convertToCodecResponse(codec, userId);
    }

    @Cacheable(value = "codecs", key = "#workspaceId + ':search:' + #query")
    @Transactional(readOnly = true)
    public List<CodecDto.SummaryResponse> searchCodecs(String userId, String workspaceId, String query) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        List<Codec> codecs = codecRepository.findCodecsInWorkspaceBySearch(workspaceId, query);
        return codecs.stream()
                .map(codec -> convertToCodecSummaryResponse(codec, userId))
                .collect(Collectors.toList());
    }

    @CacheEvict(value = "codecs", allEntries = true)
    public CodecDto.Response addCharacter(String userId, String workspaceId, String codecId, String character) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        Codec codec = codecRepository.findByIdAndLibraryWorkspaceId(codecId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Codec not found: " + codecId));

        if (codec.hasCharacter(character)) {
            throw new IllegalArgumentException("Character '" + character + "' already exists in codec");
        }

        codec.addCharacter(character);
        codec = codecRepository.save(codec);
        return convertToCodecResponse(codec, userId);
    }

    @CacheEvict(value = "codecs", allEntries = true)
    public CodecDto.Response removeCharacter(String userId, String workspaceId, String codecId, String character) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        Codec codec = codecRepository.findByIdAndLibraryWorkspaceId(codecId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Codec not found: " + codecId));

        if (!codec.hasCharacter(character)) {
            throw new IllegalArgumentException("Character '" + character + "' does not exist in codec");
        }

        codec.removeCharacter(character);
        codec = codecRepository.save(codec);
        return convertToCodecResponse(codec, userId);
    }

    @Transactional(readOnly = true)
    public CodecDto.CharacterSearchResponse findCodecsContainingCharacter(String userId, String workspaceId, String character) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        List<Codec> codecs = codecRepository.findByCharacterAndWorkspace(character, workspaceId);
        List<CodecDto.CodecSummary> codecSummaries = codecs.stream()
                .map(codec -> new CodecDto.CodecSummary(codec.getId(), codec.getName(), true))
                .collect(Collectors.toList());

        return new CodecDto.CharacterSearchResponse(codecSummaries);
    }

    @Transactional(readOnly = true)
    public boolean isCharacterInCodec(String userId, String workspaceId, String codecId, String character) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        Codec codec = codecRepository.findByIdAndLibraryWorkspaceId(codecId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Codec not found: " + codecId));

        return codec.hasCharacter(character);
    }

    @CacheEvict(value = "codecs", allEntries = true)
    public CodecDto.GenerateFromSourcesResponse generateFromSources(
            String userId,
            String workspaceId,
            CodecDto.GenerateFromSourcesRequest request) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        List<ProjectCharacterAnalysis> analyses = analyzeSources(
                workspaceId,
                request.sources(),
                resolveVariantSelection(request.variantScope(), request.variantIndex(), request.unindexedOnly()),
                resolveIncludeWhitespace(request.includeWhitespace())
        );

        Set<String> extractedCharacters = analyses.stream()
                .flatMap(a -> a.characters().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        int analyzedPageCount = analyses.stream().mapToInt(a -> a.pageIds().size()).sum();

        Codec codec;
        boolean createdNewCodec;
        int addedCharacterCount;

        String targetCodecId = trimToNull(request.targetCodecId());
        if (targetCodecId != null) {
            codec = codecRepository.findByIdAndLibraryWorkspaceId(targetCodecId, workspaceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Codec not found: " + targetCodecId));
            int before = codec.getCharacterCount();
            for (String ch : extractedCharacters) {
                codec.addCharacter(ch);
            }
            codec = codecRepository.save(codec);
            addedCharacterCount = codec.getCharacterCount() - before;
            createdNewCodec = false;
        } else {
            String newCodecName = trimToNull(request.newCodecName());
            if (newCodecName == null) {
                throw new IllegalArgumentException("newCodecName is required when targetCodecId is not provided");
            }

            Library library = libraryRepository.findByWorkspaceId(workspaceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Library not found for workspace: " + workspaceId));

            if (codecRepository.existsByNameAndLibraryId(newCodecName, library.getId())) {
                throw new IllegalArgumentException("Codec with name '" + newCodecName + "' already exists in this workspace");
            }

            codec = new Codec(newCodecName, library);
            codec.setDescription(trimToNull(request.newCodecDescription()));
            codec.getTags().clear();
            codec.getTags().addAll(normalizeTags(request.newCodecTags()));
            for (String ch : extractedCharacters) {
                codec.addCharacter(ch);
            }
            codec = codecRepository.save(codec);
            addedCharacterCount = codec.getCharacterCount();
            createdNewCodec = true;
        }

        String message = createdNewCodec
                ? "Created codec from selected sources"
                : "Added extracted characters to existing codec";

        return new CodecDto.GenerateFromSourcesResponse(
                convertToCodecResponse(codec, userId),
                createdNewCodec,
                analyses.size(),
                analyzedPageCount,
                extractedCharacters.size(),
                addedCharacterCount,
                message
        );
    }

    @Transactional(readOnly = true)
    public CodecDto.ValidateAgainstSourcesResponse validateAgainstSources(
            String userId,
            String workspaceId,
            String codecId,
            CodecDto.ValidateAgainstSourcesRequest request) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        Codec codec = codecRepository.findByIdAndLibraryWorkspaceId(codecId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Codec not found: " + codecId));

        Set<String> codecCharacters = codec.getCharacters() == null
                ? Set.of()
                : codec.getCharacters().stream()
                .filter(s -> s != null && !s.isEmpty())
                .collect(Collectors.toSet());

        List<ProjectCharacterAnalysis> analyses = analyzeSources(
                workspaceId,
                request.sources(),
                resolveVariantSelection(request.variantScope(), request.variantIndex(), request.unindexedOnly()),
                resolveIncludeWhitespace(request.includeWhitespace())
        );

        Set<String> allExtracted = analyses.stream()
                .flatMap(a -> a.characters().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<String> missingCharacters = differenceSorted(allExtracted, codecCharacters);
        Set<String> missingCharacterSet = new LinkedHashSet<>(missingCharacters);

        Map<String, Map<String, CodecDto.ValidateCharacterPageRef>> pagesByMissingCharacter = new LinkedHashMap<>();

        List<CodecDto.ValidateProjectResult> projectResults = analyses.stream()
                .map(analysis -> {
                    List<String> projectMissing = differenceSorted(analysis.characters(), codecCharacters);
                    Set<String> missingPageIds = new LinkedHashSet<>();

                    for (String character : projectMissing) {
                        Set<String> pageIdsForCharacter = analysis.pageIdsByCharacter().getOrDefault(character, Set.of());
                        missingPageIds.addAll(pageIdsForCharacter);

                        Map<String, CodecDto.ValidateCharacterPageRef> pageRefs = pagesByMissingCharacter
                                .computeIfAbsent(character, key -> new LinkedHashMap<>());
                        for (String pageId : pageIdsForCharacter) {
                            String pageName = analysis.pageNamesById().getOrDefault(pageId, pageId);
                            pageRefs.putIfAbsent(
                                    analysis.projectId() + ":" + pageId,
                                    new CodecDto.ValidateCharacterPageRef(
                                            analysis.projectId(),
                                            analysis.projectName(),
                                            pageId,
                                            pageName
                                    )
                            );
                        }
                    }

                    return new CodecDto.ValidateProjectResult(
                            analysis.projectId(),
                            analysis.projectName(),
                            analysis.pageIds().size(),
                            projectMissing,
                            projectMissing.size(),
                            missingPageIds.stream().sorted().toList(),
                            missingPageIds.size(),
                            projectMissing.isEmpty()
                    );
                })
                .toList();

        List<CodecDto.ValidateCharacterResult> missingCharacterResults = missingCharacters.stream()
                .filter(missingCharacterSet::contains)
                .map(character -> {
                    List<CodecDto.ValidateCharacterPageRef> pageRefs = pagesByMissingCharacter
                            .getOrDefault(character, Map.of())
                            .values()
                            .stream()
                            .sorted((a, b) -> {
                                String aProject = a.projectName() != null ? a.projectName() : a.projectId();
                                String bProject = b.projectName() != null ? b.projectName() : b.projectId();
                                int projectCmp = aProject.compareToIgnoreCase(bProject);
                                if (projectCmp != 0) return projectCmp;
                                String aPage = a.pageName() != null ? a.pageName() : a.pageId();
                                String bPage = b.pageName() != null ? b.pageName() : b.pageId();
                                return aPage.compareToIgnoreCase(bPage);
                            })
                            .toList();

                    return new CodecDto.ValidateCharacterResult(character, pageRefs);
                })
                .toList();

        int analyzedPageCount = analyses.stream().mapToInt(a -> a.pageIds().size()).sum();
        boolean valid = missingCharacters.isEmpty();

        return new CodecDto.ValidateAgainstSourcesResponse(
                valid,
                missingCharacters,
                missingCharacters.size(),
                analyses.size(),
                analyzedPageCount,
                projectResults,
                missingCharacterResults,
                valid ? "Codec fully covers selected sources" : "Codec is missing characters from selected sources"
        );
    }

    @Transactional(readOnly = true)
    public CodecDto.GenerateFromProjectResponse generateFromProject(String userId, String workspaceId, String projectId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        List<ProjectCharacterAnalysis> analyses = analyzeSources(
                workspaceId,
                List.of(new CodecDto.ProjectScope(projectId, List.of())),
                resolveVariantSelection(CodecDto.VariantScope.ALL, null, false),
                false
        );

        Set<String> extractedCharacters = analyses.stream()
                .flatMap(a -> a.characters().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<String> sortedCharacters = extractedCharacters.stream().sorted().toList();

        return new CodecDto.GenerateFromProjectResponse(
                sortedCharacters,
                sortedCharacters.size(),
                "Generated character set from project"
        );
    }

    @Transactional(readOnly = true)
    public CodecDto.ValidateAgainstProjectResponse validateAgainstProject(String userId, String workspaceId, String codecId, String projectId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        CodecDto.ValidateAgainstSourcesResponse response = validateAgainstSources(
                userId,
                workspaceId,
                codecId,
                new CodecDto.ValidateAgainstSourcesRequest(
                        List.of(new CodecDto.ProjectScope(projectId, List.of())),
                        CodecDto.VariantScope.ALL,
                        null,
                        false,
                        false
                )
        );

        return new CodecDto.ValidateAgainstProjectResponse(
                response.valid(),
                response.missingCharacters(),
                response.message()
        );
    }

    private List<ProjectCharacterAnalysis> analyzeSources(
            String workspaceId,
            List<CodecDto.ProjectScope> sources,
            VariantSelection variantSelection,
            boolean includeWhitespace) {
        if (sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("At least one source is required");
        }

        Map<String, MutableProjectAnalysis> mergedByProject = new LinkedHashMap<>();

        for (CodecDto.ProjectScope source : sources) {
            if (source == null || source.projectId() == null || source.projectId().isBlank()) {
                throw new IllegalArgumentException("Project ID is required in every source");
            }

            String projectId = source.projectId().trim();

            Project project = projectRepository.findByIdAndLibraryWorkspaceId(projectId, workspaceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Project not found in workspace: " + projectId));

            PageScopeResolution pageScope = resolvePageScope(project.getId(), source.pageIds());

            List<PageTextContent> textRows;
            if (pageScope.pageIds().isEmpty()) {
                textRows = List.of();
            } else if (pageScope.allPagesSelected()) {
                textRows = pageTextContentRepository.findByProjectId(projectId);
            } else {
                textRows = pageTextContentRepository.findByProjectIdAndPageIds(projectId, new ArrayList<>(pageScope.pageIds()));
            }

            CharacterExtractionResult extraction = extractCharacters(textRows, variantSelection, includeWhitespace);

            MutableProjectAnalysis merged = mergedByProject.computeIfAbsent(projectId, id -> new MutableProjectAnalysis());
            merged.projectName = project.getName();
            merged.pageIds.addAll(pageScope.pageIds());
            merged.pageNamesById.putAll(pageScope.pageNamesById());
            merged.characters.addAll(extraction.characters());
            extraction.pageIdsByCharacter().forEach((character, pageIds) ->
                    merged.pageIdsByCharacter
                            .computeIfAbsent(character, key -> new LinkedHashSet<>())
                            .addAll(pageIds)
            );
        }

        return mergedByProject.entrySet().stream()
                .map(entry -> new ProjectCharacterAnalysis(
                        entry.getKey(),
                        entry.getValue().projectName,
                        new LinkedHashSet<>(entry.getValue().pageIds),
                        new LinkedHashMap<>(entry.getValue().pageNamesById),
                        new LinkedHashSet<>(entry.getValue().characters),
                        entry.getValue().pageIdsByCharacter.entrySet().stream()
                                .collect(Collectors.toMap(
                                        Map.Entry::getKey,
                                        e -> new LinkedHashSet<>(e.getValue()),
                                        (a, b) -> a,
                                        LinkedHashMap::new))
                ))
                .toList();
    }

    private PageScopeResolution resolvePageScope(String projectId, List<String> requestedPageIds) {
        if (requestedPageIds == null || requestedPageIds.isEmpty()) {
            List<Page> allPages = pageRepository.findByProjectId(projectId);
            Set<String> allPageIds = allPages.stream()
                    .map(Page::getId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Map<String, String> pageNamesById = allPages.stream()
                    .collect(Collectors.toMap(
                            Page::getId,
                            page -> page.getName() != null ? page.getName() : page.getId(),
                            (a, b) -> a,
                            LinkedHashMap::new));
            return new PageScopeResolution(allPageIds, pageNamesById, true);
        }

        Set<String> normalizedRequested = requestedPageIds.stream()
                .map(this::trimToNull)
                .filter(id -> id != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (normalizedRequested.isEmpty()) {
            List<Page> allPages = pageRepository.findByProjectId(projectId);
            Set<String> allPageIds = allPages.stream()
                    .map(Page::getId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Map<String, String> pageNamesById = allPages.stream()
                    .collect(Collectors.toMap(
                            Page::getId,
                            page -> page.getName() != null ? page.getName() : page.getId(),
                            (a, b) -> a,
                            LinkedHashMap::new));
            return new PageScopeResolution(allPageIds, pageNamesById, true);
        }

        List<Page> matchedPages = pageRepository.findByIdInAndProjectId(new ArrayList<>(normalizedRequested), projectId);
        Set<String> matchedIds = matchedPages.stream().map(Page::getId).collect(Collectors.toSet());

        if (matchedIds.size() != normalizedRequested.size()) {
            Set<String> missing = new HashSet<>(normalizedRequested);
            missing.removeAll(matchedIds);
            throw new IllegalArgumentException("Some page IDs do not belong to project '" + projectId + "': " + String.join(",", missing));
        }

        Map<String, String> pageNamesById = matchedPages.stream()
                .collect(Collectors.toMap(
                        Page::getId,
                        page -> page.getName() != null ? page.getName() : page.getId(),
                        (a, b) -> a,
                        LinkedHashMap::new));

        return new PageScopeResolution(normalizedRequested, pageNamesById, false);
    }

    private CharacterExtractionResult extractCharacters(
            List<PageTextContent> textRows,
            VariantSelection variantSelection,
            boolean includeWhitespace) {
        Set<String> characters = new LinkedHashSet<>();
        Map<String, Set<String>> pageIdsByCharacter = new LinkedHashMap<>();

        for (PageTextContent row : textRows) {
            if (row == null) {
                continue;
            }

            if (!matchesVariantSelection(row.getVariantIndex(), variantSelection)) {
                continue;
            }

            String text = row.getTextContent();
            if (text == null || text.isEmpty()) {
                continue;
            }

            String pageId = row.getPage() != null ? row.getPage().getId() : null;
            text.codePoints().forEach(cp -> {
                if (!includeWhitespace && Character.isWhitespace(cp)) {
                    return;
                }
                String character = new String(Character.toChars(cp));
                characters.add(character);
                if (pageId != null && !pageId.isBlank()) {
                    pageIdsByCharacter
                            .computeIfAbsent(character, key -> new LinkedHashSet<>())
                            .add(pageId);
                }
            });
        }

        return new CharacterExtractionResult(characters, pageIdsByCharacter);
    }

    private List<String> differenceSorted(Set<String> left, Set<String> right) {
        Set<String> diff = new HashSet<>(left);
        diff.removeAll(right);
        return diff.stream()
                .filter(s -> s != null && !s.isEmpty())
                .sorted()
                .toList();
    }

    private VariantSelection resolveVariantSelection(
            CodecDto.VariantScope scope,
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

        CodecDto.VariantScope resolvedScope = scope == null ? CodecDto.VariantScope.ALL : scope;
        if (resolvedScope == CodecDto.VariantScope.PRIMARY) {
            // Backward compatibility for older clients.
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

    private boolean resolveIncludeWhitespace(Boolean includeWhitespace) {
        return Boolean.TRUE.equals(includeWhitespace);
    }

    private Set<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return Set.of();
        }

        return tags.stream()
                .map(this::trimToNull)
                .filter(tag -> tag != null)
                .collect(Collectors.toSet());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private CodecDto.Response convertToCodecResponse(Codec codec, String userId) {
        List<String> tags = codec.getTags() == null
                ? List.of()
                : codec.getTags().stream().filter(s -> s != null && !s.isBlank()).sorted().toList();

        List<String> codecCharacters = codec.getCharacters() == null
                ? List.of()
                : codec.getCharacters().stream().filter(s -> s != null && !s.isEmpty()).sorted().toList();

        return new CodecDto.Response(
                codec.getId(),
                codec.getName(),
                codec.getDescription(),
                tags,
                codecCharacters,
                codec.getCharacterCount(),
                codec.getCreated(),
                codec.getUpdated(),
                authorizationPolicyService.resolveWorkspaceResourceCapabilities(codec.getLibrary().getWorkspaceId(), userId)
        );
    }

    private CodecDto.SummaryResponse convertToCodecSummaryResponse(Codec codec, String userId) {
        List<String> tags = codec.getTags() == null
                ? List.of()
                : codec.getTags().stream().filter(s -> s != null && !s.isBlank()).sorted().toList();

        return new CodecDto.SummaryResponse(
                codec.getId(),
                codec.getName(),
                codec.getDescription(),
                tags,
                codec.getCharacterCount(),
                codec.getCreated(),
                codec.getUpdated(),
                authorizationPolicyService.resolveWorkspaceResourceCapabilities(codec.getLibrary().getWorkspaceId(), userId)
        );
    }

    private record PageScopeResolution(Set<String> pageIds, Map<String, String> pageNamesById, boolean allPagesSelected) {
    }

    private record ProjectCharacterAnalysis(
            String projectId,
            String projectName,
            Set<String> pageIds,
            Map<String, String> pageNamesById,
            Set<String> characters,
            Map<String, Set<String>> pageIdsByCharacter) {
    }

    private record CharacterExtractionResult(
            Set<String> characters,
            Map<String, Set<String>> pageIdsByCharacter) {
    }

    private record VariantSelection(VariantSelectionMode mode, Integer variantIndex) {
    }

    private enum VariantSelectionMode {
        ALL,
        PRIMARY_COMPAT,
        SPECIFIC_INDEX,
        UNINDEXED_ONLY
    }

    private static class MutableProjectAnalysis {
        private String projectName;
        private final Set<String> pageIds = new LinkedHashSet<>();
        private final Map<String, String> pageNamesById = new LinkedHashMap<>();
        private final Set<String> characters = new LinkedHashSet<>();
        private final Map<String, Set<String>> pageIdsByCharacter = new LinkedHashMap<>();
    }
}

package de.uniwue.zpd.dachs.larex.backend.service.dictionary;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.uniwue.zpd.dachs.larex.backend.dto.BulkDeleteDto;
import de.uniwue.zpd.dachs.larex.backend.dto.DictionaryDto;
import de.uniwue.zpd.dachs.larex.backend.entity.ControlledDictionary;
import de.uniwue.zpd.dachs.larex.backend.entity.ControlledDictionaryEntry;
import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageTextContent;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.PersonalWorkspace;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.TeamWorkspace;
import de.uniwue.zpd.dachs.larex.backend.exception.ResourceNotFoundException;
import de.uniwue.zpd.dachs.larex.backend.repository.dictionary.ControlledDictionaryEntryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.dictionary.ControlledDictionaryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.library.LibraryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageTextContentRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.PersonalWorkspaceRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.TeamWorkspaceRepository;
import de.uniwue.zpd.dachs.larex.backend.service.security.AuthorizationPolicyService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
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
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
@Transactional
public class DictionaryService {

    private static final int DEFAULT_SUGGEST_LIMIT = 5;

    private final ControlledDictionaryRepository dictionaryRepository;
    private final ControlledDictionaryEntryRepository entryRepository;
    private final LibraryRepository libraryRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final ProjectRepository projectRepository;
    private final PersonalWorkspaceRepository personalWorkspaceRepository;
    private final TeamWorkspaceRepository teamWorkspaceRepository;
    private final PageRepository pageRepository;
    private final PageTextContentRepository pageTextContentRepository;
    private final AuthorizationPolicyService authorizationPolicyService;
    private final ObjectMapper objectMapper;
    private final Cache<String, DictionaryIndex> dictionaryIndexCache = Caffeine.newBuilder()
            .maximumSize(512)
            .expireAfterAccess(Duration.ofMinutes(30))
            .build();

    public DictionaryService(ControlledDictionaryRepository dictionaryRepository,
                             ControlledDictionaryEntryRepository entryRepository,
                             LibraryRepository libraryRepository,
                             WorkspaceAccessService workspaceAccessService,
                             ProjectRepository projectRepository,
                             PersonalWorkspaceRepository personalWorkspaceRepository,
                             TeamWorkspaceRepository teamWorkspaceRepository,
                             PageRepository pageRepository,
                             PageTextContentRepository pageTextContentRepository,
                             AuthorizationPolicyService authorizationPolicyService,
                             ObjectMapper objectMapper) {
        this.dictionaryRepository = dictionaryRepository;
        this.entryRepository = entryRepository;
        this.libraryRepository = libraryRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.projectRepository = projectRepository;
        this.personalWorkspaceRepository = personalWorkspaceRepository;
        this.teamWorkspaceRepository = teamWorkspaceRepository;
        this.pageRepository = pageRepository;
        this.pageTextContentRepository = pageTextContentRepository;
        this.authorizationPolicyService = authorizationPolicyService;
        this.objectMapper = objectMapper;
    }

    public DictionaryDto.Response createDictionary(String userId, String workspaceId, DictionaryDto.CreateOrUpdateRequest request) {
        workspaceAccessService.requireManageUtilitiesAccess(workspaceId, userId);

        Library library = libraryRepository.findByWorkspaceId(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Projects not found for workspace: " + workspaceId));

        if (dictionaryRepository.existsByNameAndLibraryId(request.name(), library.getId())) {
            throw new IllegalArgumentException("Dictionary with name '" + request.name() + "' already exists in this workspace");
        }

        ControlledDictionary dictionary = new ControlledDictionary(request.name(), library);
        applyDictionaryMetadata(dictionary, request);
        dictionary = dictionaryRepository.save(dictionary);
        invalidateDictionaryCache(dictionary.getId());
        return toResponse(dictionary, userId);
    }

    public DictionaryDto.Response updateDictionary(String userId, String workspaceId, String dictionaryId, DictionaryDto.CreateOrUpdateRequest request) {
        workspaceAccessService.requireManageUtilitiesAccess(workspaceId, userId);

        ControlledDictionary dictionary = dictionaryRepository.findByIdAndLibraryWorkspaceId(dictionaryId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Dictionary not found: " + dictionaryId));

        if (!dictionary.getName().equals(request.name())
                && dictionaryRepository.existsByNameAndLibraryId(request.name(), dictionary.getLibrary().getId())) {
            throw new IllegalArgumentException("Dictionary with name '" + request.name() + "' already exists in this workspace");
        }

        dictionary.setName(request.name());
        applyDictionaryMetadata(dictionary, request);
        dictionary = dictionaryRepository.save(dictionary);
        invalidateDictionaryCache(dictionary.getId());
        return toResponse(dictionary, userId);
    }

    public void deleteDictionary(String userId, String workspaceId, String dictionaryId) {
        workspaceAccessService.requireManageUtilitiesAccess(workspaceId, userId);

        ControlledDictionary dictionary = dictionaryRepository.findByIdAndLibraryWorkspaceId(dictionaryId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Dictionary not found: " + dictionaryId));

        clearDictionaryAssignments(workspaceId, dictionaryId);
        entryRepository.deleteByDictionaryId(dictionaryId);
        dictionary.setEntries(new ArrayList<>());
        dictionaryRepository.delete(dictionary);
        invalidateDictionaryCache(dictionaryId);
    }

    public BulkDeleteDto.BulkDeleteResponse bulkDeleteDictionaries(String userId, String workspaceId, List<String> ids) {
        List<String> deletedIds = new ArrayList<>();
        List<String> failedIds = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (String dictionaryId : new LinkedHashSet<>(ids)) {
            if (dictionaryId == null || dictionaryId.isBlank()) {
                failedIds.add(Objects.toString(dictionaryId, "<null>"));
                errors.add("Cannot delete dictionary with a blank ID.");
                continue;
            }

            try {
                deleteDictionary(userId, workspaceId, dictionaryId);
                deletedIds.add(dictionaryId);
            } catch (RuntimeException ex) {
                failedIds.add(dictionaryId);
                errors.add("Failed to delete dictionary " + dictionaryId + ": " + describeError(ex));
            }
        }

        return new BulkDeleteDto.BulkDeleteResponse(
                deletedIds.size(),
                failedIds.size(),
                deletedIds,
                failedIds,
                errors
        );
    }

    @Transactional(readOnly = true)
    public List<DictionaryDto.SummaryResponse> getDictionaries(String userId, String workspaceId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        return dictionaryRepository.findByLibraryWorkspaceId(workspaceId).stream()
                .map(dictionary -> toSummaryResponse(dictionary, userId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DictionaryDto.SummaryResponse> searchDictionaries(String userId, String workspaceId, String query) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return dictionaryRepository.findDictionariesInWorkspaceBySearch(workspaceId, normalizedQuery).stream()
                .map(dictionary -> toSummaryResponse(dictionary, userId))
                .toList();
    }

    @Transactional(readOnly = true)
    public DictionaryDto.Response getDictionary(String userId, String workspaceId, String dictionaryId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        ControlledDictionary dictionary = dictionaryRepository.findByIdAndLibraryWorkspaceId(dictionaryId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Dictionary not found: " + dictionaryId));
        return toResponse(dictionary, userId);
    }

    @Transactional(readOnly = true)
    public DictionaryDto.EntryPageResponse getEntries(String userId, String workspaceId, String dictionaryId, int page, int size, String search) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        requireDictionaryInWorkspace(workspaceId, dictionaryId);

        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 500)));
        String normalizedSearch = trimToNull(search);
        org.springframework.data.domain.Page<ControlledDictionaryEntry> result = normalizedSearch == null
                ? entryRepository.findByDictionaryIdOrderBySurfaceFormAsc(dictionaryId, pageable)
                : entryRepository.searchByDictionaryId(dictionaryId, normalizedSearch, pageable);

        return new DictionaryDto.EntryPageResponse(
                result.getContent().stream().map(this::toEntryResponse).toList(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber(),
                result.getSize()
        );
    }

    public DictionaryDto.EntryResponse addEntry(String userId, String workspaceId, String dictionaryId, DictionaryDto.EntryCreateOrUpdateRequest request) {
        workspaceAccessService.requireManageUtilitiesAccess(workspaceId, userId);
        ControlledDictionary dictionary = requireDictionaryInWorkspace(workspaceId, dictionaryId);
        if (Boolean.TRUE.equals(request.fromEditor()) && dictionary.isLocked()) {
            throw new IllegalArgumentException("This dictionary is locked for editor additions");
        }
        String form = normalizeSurfaceForm(request.form());
        String normalizedValue = normalizeForDictionary(dictionary, form);

        ensureUniqueNormalizedValue(dictionaryId, normalizedValue, null);

        ControlledDictionaryEntry entry = new ControlledDictionaryEntry(form, normalizedValue);
        entry.setDictionary(dictionary);
        entry.setSourceEntryKey(trimToNull(request.sourceEntryKey()));
        entry.setMetadataJson(toMetadataJson(request.metadata()));
        entry = entryRepository.save(entry);
        invalidateDictionaryCache(dictionaryId);
        return toEntryResponse(entry);
    }

    public DictionaryDto.EntryResponse updateEntry(String userId, String workspaceId, String dictionaryId, String entryId, DictionaryDto.EntryCreateOrUpdateRequest request) {
        workspaceAccessService.requireManageUtilitiesAccess(workspaceId, userId);
        ControlledDictionary dictionary = requireDictionaryInWorkspace(workspaceId, dictionaryId);
        ControlledDictionaryEntry entry = entryRepository.findByIdAndDictionaryId(entryId, dictionaryId)
                .orElseThrow(() -> new ResourceNotFoundException("Dictionary entry not found: " + entryId));

        String form = normalizeSurfaceForm(request.form());
        String normalizedValue = normalizeForDictionary(dictionary, form);
        ensureUniqueNormalizedValue(dictionaryId, normalizedValue, entryId);

        entry.setSurfaceForm(form);
        entry.setNormalizedValue(normalizedValue);
        entry.setSourceEntryKey(trimToNull(request.sourceEntryKey()));
        entry.setMetadataJson(toMetadataJson(request.metadata()));
        entry = entryRepository.save(entry);
        invalidateDictionaryCache(dictionaryId);
        return toEntryResponse(entry);
    }

    private String describeError(RuntimeException ex) {
        return ex.getMessage() == null || ex.getMessage().isBlank() ? "Unexpected error" : ex.getMessage();
    }

    public void deleteEntry(String userId, String workspaceId, String dictionaryId, String entryId) {
        workspaceAccessService.requireManageUtilitiesAccess(workspaceId, userId);
        requireDictionaryInWorkspace(workspaceId, dictionaryId);
        ControlledDictionaryEntry entry = entryRepository.findByIdAndDictionaryId(entryId, dictionaryId)
                .orElseThrow(() -> new ResourceNotFoundException("Dictionary entry not found: " + entryId));
        entryRepository.delete(entry);
        invalidateDictionaryCache(dictionaryId);
    }

    public DictionaryDto.ImportResult importIntoDictionary(String userId,
                                                           String workspaceId,
                                                           String dictionaryId,
                                                           MultipartFile file,
                                                           DictionaryDto.ImportFormat format,
                                                           DictionaryDto.ImportMode mode) throws IOException {
        workspaceAccessService.requireManageUtilitiesAccess(workspaceId, userId);
        ControlledDictionary dictionary = requireDictionaryInWorkspace(workspaceId, dictionaryId);

        ParsedDictionaryImport parsed = parseImport(file, format);
        ImportApplyResult applyResult = applyImportedEntries(dictionary, parsed.entries(), mode == DictionaryDto.ImportMode.REPLACE);
        dictionaryRepository.save(dictionary);
        invalidateDictionaryCache(dictionaryId);

        return new DictionaryDto.ImportResult(
                dictionary.getId(),
                dictionary.getName(),
                applyResult.importedCount(),
                applyResult.skippedCount(),
                mode == DictionaryDto.ImportMode.REPLACE,
                applyResult.warnings(),
                mode == DictionaryDto.ImportMode.REPLACE ? "Dictionary entries replaced from import" : "Dictionary entries imported"
        );
    }

    public DictionaryDto.Response createDictionaryFromImport(String userId,
                                                             String workspaceId,
                                                             MultipartFile file,
                                                             DictionaryDto.ImportFormat format,
                                                             String name,
                                                             String description,
                                                             List<String> tags,
                                                             Boolean caseSensitive,
                                                             String unicodeNormalization,
                                                             Boolean locked) throws IOException {
        workspaceAccessService.requireManageUtilitiesAccess(workspaceId, userId);

        ParsedDictionaryImport parsed = parseImport(file, format);
        String resolvedName = trimToNull(name) != null ? trimToNull(name)
                : trimToNull(parsed.name()) != null ? trimToNull(parsed.name())
                : deriveDictionaryName(file.getOriginalFilename());

        Library library = libraryRepository.findByWorkspaceId(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Projects not found for workspace: " + workspaceId));

        if (dictionaryRepository.existsByNameAndLibraryId(resolvedName, library.getId())) {
            throw new IllegalArgumentException("Dictionary with name '" + resolvedName + "' already exists in this workspace");
        }

        ControlledDictionary dictionary = new ControlledDictionary(resolvedName, library);
        dictionary.setDescription(trimToNull(description) != null ? trimToNull(description) : trimToNull(parsed.description()));
        dictionary.setTags(normalizeTags(tags != null ? tags : parsed.tags()));
        dictionary.setCaseSensitive(Boolean.TRUE.equals(caseSensitive) || Boolean.TRUE.equals(parsed.caseSensitive()));
        dictionary.setUnicodeNormalization(resolveNormalization(unicodeNormalization != null ? unicodeNormalization : parsed.unicodeNormalization()));
        dictionary.setLocked(Boolean.TRUE.equals(locked) || Boolean.TRUE.equals(parsed.locked()));
        dictionary = dictionaryRepository.save(dictionary);

        applyImportedEntries(dictionary, parsed.entries(), true);
        dictionary = dictionaryRepository.save(dictionary);
        invalidateDictionaryCache(dictionary.getId());
        return toResponse(dictionary, userId);
    }

    public String importDictionaryFromPackage(String userId,
                                              String workspaceId,
                                              String targetName,
                                              DictionaryDto.PackagePayload payload) {
        Library library = libraryRepository.findByWorkspaceId(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Projects not found for workspace: " + workspaceId));

        ControlledDictionary dictionary = new ControlledDictionary(targetName, library);
        dictionary.setDescription(trimToNull(payload.description()));
        dictionary.setTags(normalizeTags(payload.tags()));
        dictionary.setCaseSensitive(Boolean.TRUE.equals(payload.caseSensitive()));
        dictionary.setUnicodeNormalization(resolveNormalization(payload.unicodeNormalization()));
        dictionary.setLocked(Boolean.TRUE.equals(payload.locked()));
        dictionary = dictionaryRepository.save(dictionary);

        List<ImportedEntry> entries = (payload.entries() == null ? List.<DictionaryDto.PackageEntry>of() : payload.entries()).stream()
                .map(entry -> new ImportedEntry(
                        normalizeSurfaceForm(entry.form()),
                        trimToNull(entry.sourceEntryKey()),
                        entry.metadata()
                ))
                .filter(entry -> entry.form() != null)
                .toList();
        applyImportedEntries(dictionary, entries, true);
        dictionaryRepository.save(dictionary);
        invalidateDictionaryCache(dictionary.getId());
        return dictionary.getId();
    }

    @Transactional(readOnly = true)
    public DictionaryDto.PackagePayload buildPackagePayload(String userId, String workspaceId, String dictionaryId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        ControlledDictionary dictionary = requireDictionaryInWorkspace(workspaceId, dictionaryId);
        List<DictionaryDto.PackageEntry> entries = entryRepository.findByDictionaryIdOrderBySurfaceFormAsc(dictionaryId).stream()
                .map(entry -> new DictionaryDto.PackageEntry(
                        entry.getSurfaceForm(),
                        entry.getSourceEntryKey(),
                        readMetadata(entry.getMetadataJson())
                ))
                .toList();
        return new DictionaryDto.PackagePayload(
                dictionary.getName(),
                dictionary.getDescription(),
                sortedTags(dictionary.getTags()),
                dictionary.isCaseSensitive(),
                dictionary.getUnicodeNormalization(),
                dictionary.isLocked(),
                entries
        );
    }

    @Transactional(readOnly = true)
    public DictionaryDto.CheckTokensResponse checkTokens(String userId,
                                                         String workspaceId,
                                                         String dictionaryId,
                                                         DictionaryDto.CheckTokensRequest request) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        ControlledDictionary dictionary = requireDictionaryInWorkspace(workspaceId, dictionaryId);
        DictionaryIndex index = getDictionaryIndex(dictionary);

        boolean includeSuggestions = Boolean.TRUE.equals(request.includeSuggestions());
        Integer suggestionLimit = request.limit();

        List<DictionaryDto.TokenCheckResult> results = (request.tokens() == null ? List.<String>of() : request.tokens()).stream()
                .map(this::normalizeSurfaceForm)
                .filter(Objects::nonNull)
                .distinct()
                .map(token -> {
                    String normalized = normalizeForDictionary(dictionary, token);
                    boolean known = normalized != null && index.forms().containsKey(normalized);
                    List<DictionaryDto.Suggestion> suggestions = known || !includeSuggestions
                            ? List.of()
                            : suggestForNormalizedToken(dictionary, normalized, suggestionLimit);
                    return new DictionaryDto.TokenCheckResult(token, normalized, known, suggestions);
                })
                .toList();

        return new DictionaryDto.CheckTokensResponse(dictionary.getId(), results);
    }

    @Transactional(readOnly = true)
    public DictionaryDto.ValidateAgainstSourcesResponse validateAgainstSources(String userId,
                                                                              String workspaceId,
                                                                              String dictionaryId,
                                                                              DictionaryDto.ValidateAgainstSourcesRequest request) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        ControlledDictionary dictionary = requireDictionaryInWorkspace(workspaceId, dictionaryId);
        DictionaryIndex dictionaryIndex = getDictionaryIndex(dictionary);

        List<ProjectTokenAnalysis> analyses = analyzeSources(
                workspaceId,
                request.sources(),
                resolveVariantSelection(request.variantScope(), request.variantIndex(), request.unindexedOnly()),
                dictionary
        );

        Set<String> allUnknownNormalized = new LinkedHashSet<>();
        Map<String, String> representativeTokenByNormalized = new LinkedHashMap<>();
        Map<String, Integer> occurrenceCountByNormalized = new LinkedHashMap<>();
        Map<String, Map<String, DictionaryDto.ValidateTokenPageRef>> pageRefsByNormalized = new LinkedHashMap<>();

        List<DictionaryDto.ValidateProjectResult> projectResults = analyses.stream()
                .map(analysis -> {
                    List<String> unknownTokens = new ArrayList<>();
                    Set<String> unknownPageIds = new LinkedHashSet<>();

                    for (TokenStats token : analysis.tokens().values()) {
                        if (dictionaryIndex.contains(token.normalizedValue())) {
                            continue;
                        }
                        allUnknownNormalized.add(token.normalizedValue());
                        representativeTokenByNormalized.putIfAbsent(token.normalizedValue(), token.displayValue());
                        occurrenceCountByNormalized.merge(token.normalizedValue(), token.occurrenceCount(), Integer::sum);
                        unknownTokens.add(token.displayValue());
                        unknownPageIds.addAll(token.pageIds());

                        Map<String, DictionaryDto.ValidateTokenPageRef> refs = pageRefsByNormalized
                                .computeIfAbsent(token.normalizedValue(), ignored -> new LinkedHashMap<>());
                        for (String pageId : token.pageIds()) {
                            refs.putIfAbsent(
                                    analysis.projectId() + ":" + pageId,
                                    new DictionaryDto.ValidateTokenPageRef(
                                            analysis.projectId(),
                                            analysis.projectName(),
                                            pageId,
                                            analysis.pageNamesById().getOrDefault(pageId, pageId)
                                    )
                            );
                        }
                    }

                    List<String> sortedUnknownTokens = unknownTokens.stream()
                            .distinct()
                            .sorted(String.CASE_INSENSITIVE_ORDER)
                            .toList();

                    return new DictionaryDto.ValidateProjectResult(
                            analysis.projectId(),
                            analysis.projectName(),
                            analysis.pageIds().size(),
                            sortedUnknownTokens,
                            sortedUnknownTokens.size(),
                            unknownPageIds.stream().sorted().toList(),
                            unknownPageIds.size(),
                            sortedUnknownTokens.isEmpty()
                    );
                })
                .toList();

        List<String> unknownTokens = allUnknownNormalized.stream()
                .map(representativeTokenByNormalized::get)
                .filter(Objects::nonNull)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        List<DictionaryDto.ValidateTokenResult> unknownTokenResults = allUnknownNormalized.stream()
                .sorted(Comparator.comparing(representativeTokenByNormalized::get, String.CASE_INSENSITIVE_ORDER))
                .map(normalized -> new DictionaryDto.ValidateTokenResult(
                        representativeTokenByNormalized.get(normalized),
                        normalized,
                        occurrenceCountByNormalized.getOrDefault(normalized, 0),
                        pageRefsByNormalized.getOrDefault(normalized, Map.of()).values().stream()
                                .sorted((a, b) -> {
                                    String aProject = a.projectName() != null ? a.projectName() : a.projectId();
                                    String bProject = b.projectName() != null ? b.projectName() : b.projectId();
                                    int projectCmp = aProject.compareToIgnoreCase(bProject);
                                    if (projectCmp != 0) return projectCmp;
                                    String aPage = a.pageName() != null ? a.pageName() : a.pageId();
                                    String bPage = b.pageName() != null ? b.pageName() : b.pageId();
                                    return aPage.compareToIgnoreCase(bPage);
                                })
                                .toList(),
                        suggestForNormalizedToken(dictionary, normalized, DEFAULT_SUGGEST_LIMIT)
                ))
                .toList();

        int analyzedTokenCount = analyses.stream()
                .flatMap(analysis -> analysis.tokens().values().stream())
                .mapToInt(stats -> stats.occurrenceCount())
                .sum();
        int unknownTokenCount = unknownTokenResults.stream().mapToInt(DictionaryDto.ValidateTokenResult::occurrenceCount).sum();
        int analyzedPageCount = analyses.stream().mapToInt(analysis -> analysis.pageIds().size()).sum();
        boolean valid = unknownTokenResults.isEmpty();

        return new DictionaryDto.ValidateAgainstSourcesResponse(
                valid,
                analyses.size(),
                analyzedPageCount,
                analyzedTokenCount,
                Math.max(analyzedTokenCount - unknownTokenCount, 0),
                unknownTokenCount,
                unknownTokens,
                projectResults,
                unknownTokenResults,
                valid ? "Dictionary fully covers selected sources" : "Dictionary is missing tokens from selected sources"
        );
    }

    private ImportApplyResult applyImportedEntries(ControlledDictionary dictionary,
                                                   List<ImportedEntry> entries,
                                                   boolean replaceExistingEntries) {
        if (replaceExistingEntries) {
            dictionary.getEntries().clear();
            entryRepository.deleteByDictionaryId(dictionary.getId());
        }

        Map<String, ControlledDictionaryEntry> existingByNormalized = dictionary.getEntries().stream()
                .filter(entry -> entry.getNormalizedValue() != null)
                .collect(Collectors.toMap(
                        ControlledDictionaryEntry::getNormalizedValue,
                        entry -> entry,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        List<String> warnings = new ArrayList<>();
        int imported = 0;
        int skipped = 0;

        for (ImportedEntry importedEntry : entries) {
            if (importedEntry.form() == null) {
                skipped++;
                continue;
            }

            String normalized = normalizeForDictionary(dictionary, importedEntry.form());
            if (existingByNormalized.containsKey(normalized)) {
                skipped++;
                warnings.add("Skipped duplicate form '" + importedEntry.form() + "'");
                continue;
            }

            ControlledDictionaryEntry entry = new ControlledDictionaryEntry(importedEntry.form(), normalized);
            entry.setDictionary(dictionary);
            entry.setSourceEntryKey(importedEntry.sourceEntryKey());
            entry.setMetadataJson(toMetadataJson(importedEntry.metadata()));
            dictionary.addEntry(entry);
            existingByNormalized.put(normalized, entry);
            imported++;
        }

        return new ImportApplyResult(imported, skipped, warnings);
    }

    private ParsedDictionaryImport parseImport(MultipartFile file, DictionaryDto.ImportFormat format) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Import file is required");
        }

        byte[] bytes = file.getBytes();
        String content = new String(bytes, StandardCharsets.UTF_8);
        DictionaryDto.ImportFormat resolvedFormat = resolveImportFormat(format, file.getOriginalFilename(), content);

        return switch (resolvedFormat) {
            case TXT -> parseTxt(content);
            case CSV -> parseDelimited(content, ',');
            case TSV -> parseDelimited(content, '\t');
            case JSON -> parseJson(content);
            case TEI -> parseTei(content);
            case AUTO -> throw new IllegalStateException("AUTO format must be resolved before parsing");
        };
    }

    private DictionaryDto.ImportFormat resolveImportFormat(DictionaryDto.ImportFormat requested,
                                                           String originalFilename,
                                                           String content) {
        if (requested != null && requested != DictionaryDto.ImportFormat.AUTO) {
            return requested;
        }

        String fileName = originalFilename == null ? "" : originalFilename.toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".json")) return DictionaryDto.ImportFormat.JSON;
        if (fileName.endsWith(".csv")) return DictionaryDto.ImportFormat.CSV;
        if (fileName.endsWith(".tsv")) return DictionaryDto.ImportFormat.TSV;
        if (fileName.endsWith(".xml") || fileName.endsWith(".tei")) return DictionaryDto.ImportFormat.TEI;
        if (fileName.endsWith(".txt")) return DictionaryDto.ImportFormat.TXT;

        String trimmed = content == null ? "" : content.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) return DictionaryDto.ImportFormat.JSON;
        if (trimmed.startsWith("<")) return DictionaryDto.ImportFormat.TEI;
        if (looksDelimited(trimmed, '\t')) return DictionaryDto.ImportFormat.TSV;
        if (looksDelimited(trimmed, ',')) return DictionaryDto.ImportFormat.CSV;
        return DictionaryDto.ImportFormat.TXT;
    }

    private ParsedDictionaryImport parseTxt(String content) {
        List<ImportedEntry> entries = Arrays.stream((content == null ? "" : content).trim().split("\\s+"))
                .filter(token -> !token.isBlank())
                .map(token -> new ImportedEntry(token, null, null))
                .toList();
        return new ParsedDictionaryImport(null, null, List.of(), false, "NFC", false, entries);
    }

    private ParsedDictionaryImport parseDelimited(String content, char delimiter) {
        List<List<String>> rows = content == null ? List.of() : content.lines()
                .map(line -> parseDelimitedLine(line, delimiter))
                .filter(row -> !row.isEmpty())
                .toList();
        if (rows.isEmpty()) {
            return new ParsedDictionaryImport(null, null, List.of(), false, "NFC", false, List.of());
        }

        List<String> header = rows.getFirst().stream().map(String::trim).toList();
        boolean hasNamedFormColumn = header.stream().anyMatch("form"::equalsIgnoreCase);
        int startIndex = hasNamedFormColumn ? 1 : 0;
        int formIndex = hasNamedFormColumn
                ? header.indexOf(header.stream().filter("form"::equalsIgnoreCase).findFirst().orElse("form"))
                : 0;

        List<ImportedEntry> entries = new ArrayList<>();
        for (int i = startIndex; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            String form = trimToNull(formIndex < row.size() ? row.get(formIndex) : null);
            if (form == null) {
                continue;
            }

            JsonNode metadata = null;
            if (hasNamedFormColumn) {
                Map<String, String> metadataMap = new LinkedHashMap<>();
                for (int c = 0; c < header.size() && c < row.size(); c++) {
                    if (c == formIndex) {
                        continue;
                    }
                    String key = trimToNull(header.get(c));
                    String value = trimToNull(row.get(c));
                    if (key != null && value != null) {
                        metadataMap.put(key, value);
                    }
                }
                if (!metadataMap.isEmpty()) {
                    metadata = objectMapper.valueToTree(metadataMap);
                }
            }

            entries.add(new ImportedEntry(form, null, metadata));
        }

        return new ParsedDictionaryImport(null, null, List.of(), false, "NFC", false, entries);
    }

    private List<String> parseDelimitedLine(String line, char delimiter) {
        List<String> values = new ArrayList<>();
        if (line == null) {
            return values;
        }

        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }
            if (ch == delimiter && !inQuotes) {
                values.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        values.add(current.toString());
        return values.stream().anyMatch(value -> !value.trim().isEmpty()) ? values : List.of();
    }

    private ParsedDictionaryImport parseJson(String content) {
        try {
            JsonNode root = objectMapper.readTree(content);

            if (looksLikeUtilityPackage(root)) {
                throw new IllegalArgumentException("Use the utility package import for .larex-utilities.json files");
            }

            if (root.isArray()) {
                return new ParsedDictionaryImport(null, null, List.of(), false, "NFC", false, parseJsonArrayEntries(root));
            }

            if (root.isObject() && !looksLikeNativeDictionaryPayload(root)) {
                return new ParsedDictionaryImport(null, null, List.of(), false, "NFC", false, parseJsonObjectEntries(root));
            }

            DictionaryDto.PackagePayload payload = objectMapper.convertValue(root, DictionaryDto.PackagePayload.class);
            List<ImportedEntry> entries = (payload.entries() == null ? List.<DictionaryDto.PackageEntry>of() : payload.entries()).stream()
                    .map(entry -> new ImportedEntry(
                            normalizeSurfaceForm(entry.form()),
                            trimToNull(entry.sourceEntryKey()),
                            entry.metadata()
                    ))
                    .filter(entry -> entry.form() != null)
                    .toList();

            return new ParsedDictionaryImport(
                    trimToNull(payload.name()),
                    trimToNull(payload.description()),
                    payload.tags() == null ? List.of() : payload.tags(),
                    Boolean.TRUE.equals(payload.caseSensitive()),
                    resolveNormalization(payload.unicodeNormalization()),
                    Boolean.TRUE.equals(payload.locked()),
                    entries
            );
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to parse JSON dictionary import", e);
        }
    }

    private boolean looksLikeUtilityPackage(JsonNode root) {
        if (root == null || !root.isObject()) {
            return false;
        }

        JsonNode resources = root.get("resources");
        if (resources == null || !resources.isArray()) {
            return false;
        }

        JsonNode meta = root.get("meta");
        if (meta != null && meta.isObject()) {
            return true;
        }

        if (resources.isEmpty()) {
            return false;
        }

        for (JsonNode resource : resources) {
            if (resource == null || !resource.isObject()) {
                return false;
            }
            if (!resource.has("type") || !resource.has("payload")) {
                return false;
            }
        }

        return true;
    }

    private boolean looksLikeNativeDictionaryPayload(JsonNode root) {
        if (root == null || !root.isObject()) {
            return false;
        }

        boolean hasRecognizedField = false;

        JsonNode name = root.get("name");
        if (name != null) {
            if (!name.isNull() && !name.isTextual()) {
                return false;
            }
            hasRecognizedField = true;
        }

        JsonNode description = root.get("description");
        if (description != null) {
            if (!description.isNull() && !description.isTextual()) {
                return false;
            }
            hasRecognizedField = true;
        }

        JsonNode tags = root.get("tags");
        if (tags != null) {
            if (!tags.isNull() && !tags.isArray()) {
                return false;
            }
            if (tags.isArray()) {
                for (JsonNode tag : tags) {
                    if (tag != null && !tag.isNull() && !tag.isTextual()) {
                        return false;
                    }
                }
            }
            hasRecognizedField = true;
        }

        JsonNode caseSensitive = root.get("caseSensitive");
        if (caseSensitive != null) {
            if (!caseSensitive.isNull() && !caseSensitive.isBoolean()) {
                return false;
            }
            hasRecognizedField = true;
        }

        JsonNode unicodeNormalization = root.get("unicodeNormalization");
        if (unicodeNormalization != null) {
            if (!unicodeNormalization.isNull() && !unicodeNormalization.isTextual()) {
                return false;
            }
            hasRecognizedField = true;
        }

        JsonNode entries = root.get("entries");
        if (entries != null) {
            if (!entries.isNull() && !entries.isArray()) {
                return false;
            }
            hasRecognizedField = true;
        }

        return hasRecognizedField;
    }

    private List<ImportedEntry> parseJsonArrayEntries(JsonNode root) {
        List<ImportedEntry> entries = new ArrayList<>();
        for (JsonNode node : root) {
            if (node == null || node.isNull()) {
                continue;
            }
            if (node.isTextual()) {
                String form = normalizeSurfaceForm(node.asText());
                if (form != null) {
                    entries.add(new ImportedEntry(form, null, null));
                }
                continue;
            }
            if (node.isObject()) {
                String form = firstNonBlank(
                        textValue(node.get("form")),
                        textValue(node.get("value")),
                        textValue(node.get("text")),
                        textValue(node.get("surfaceForm")),
                        textValue(node.get("display"))
                );
                form = normalizeSurfaceForm(form);
                if (form == null) {
                    continue;
                }
                entries.add(new ImportedEntry(
                        form,
                        trimToNull(textValue(node.get("sourceEntryKey"))),
                        node
                ));
            }
        }
        return entries;
    }

    private List<ImportedEntry> parseJsonObjectEntries(JsonNode root) {
        List<ImportedEntry> entries = new ArrayList<>();
        root.fields().forEachRemaining(field -> {
            String form = normalizeSurfaceForm(field.getKey());
            if (form == null) {
                return;
            }
            JsonNode value = field.getValue();
            JsonNode metadata = (value != null && value.isContainerNode()) ? value : null;
            entries.add(new ImportedEntry(form, null, metadata));
        });
        return entries;
    }

    private String textValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private ParsedDictionaryImport parseTei(String content) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(content)));
            document.getDocumentElement().normalize();

            List<ImportedEntry> entries = new ArrayList<>();
            NodeList entryNodes = document.getElementsByTagNameNS("*", "entry");
            for (int i = 0; i < entryNodes.getLength(); i++) {
                Element entryElement = (Element) entryNodes.item(i);
                String entryId = trimToNull(entryElement.getAttribute("xml:id"));
                if (entryId == null) {
                    entryId = trimToNull(entryElement.getAttribute("id"));
                }
                String headword = null;
                NodeList orthNodes = entryElement.getElementsByTagNameNS("*", "orth");
                for (int j = 0; j < orthNodes.getLength(); j++) {
                    Element orthElement = (Element) orthNodes.item(j);
                    String form = normalizeSurfaceForm(orthElement.getTextContent());
                    if (form == null) {
                        continue;
                    }
                    if (headword == null) {
                        headword = form;
                    }
                    Map<String, String> metadata = new LinkedHashMap<>();
                    if (entryId != null) {
                        metadata.put("entryId", entryId);
                    }
                    metadata.put("headword", headword);
                    entries.add(new ImportedEntry(
                            form,
                            entryId,
                            objectMapper.valueToTree(metadata)
                    ));
                }
            }
            return new ParsedDictionaryImport(null, null, List.of(), false, "NFC", false, entries);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse TEI dictionary import", e);
        }
    }

    private List<DictionaryDto.Suggestion> suggestForNormalizedToken(ControlledDictionary dictionary,
                                                                     String normalizedToken,
                                                                     Integer requestedLimit) {
        DictionaryIndex index = getDictionaryIndex(dictionary);
        int limit = requestedLimit == null ? DEFAULT_SUGGEST_LIMIT : Math.clamp(requestedLimit, 1, 20);
        int maxDistance = maxSuggestionDistance(normalizedToken);

        return index.tree().search(normalizedToken, maxDistance).stream()
                .sorted(Comparator
                        .comparingInt(SuggestionCandidate::distance)
                        .thenComparing(SuggestionCandidate::normalized, String.CASE_INSENSITIVE_ORDER))
                .flatMap(candidate -> index.forms().getOrDefault(candidate.normalized(), Set.of()).stream()
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .map(display -> new DictionaryDto.Suggestion(display, candidate.normalized(), candidate.distance())))
                .distinct()
                .limit(limit)
                .toList();
    }

    private int maxSuggestionDistance(String normalizedToken) {
        int length = normalizedToken == null ? 0 : normalizedToken.codePointCount(0, normalizedToken.length());
        if (length <= 4) return 1;
        if (length <= 8) return 2;
        return 3;
    }

    private DictionaryIndex getDictionaryIndex(ControlledDictionary dictionary) {
        return dictionaryIndexCache.get(dictionary.getId(), ignored -> buildDictionaryIndex(dictionary));
    }

    private DictionaryIndex buildDictionaryIndex(ControlledDictionary dictionary) {
        ControlledDictionary resolved = dictionaryRepository.findById(dictionary.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Dictionary not found: " + dictionary.getId()));
        List<ControlledDictionaryEntry> entries = entryRepository.findByDictionaryIdOrderBySurfaceFormAsc(resolved.getId());
        Map<String, Set<String>> formsByNormalized = new LinkedHashMap<>();
        BKTree tree = new BKTree();

        for (ControlledDictionaryEntry entry : entries) {
            String normalized = trimToNull(entry.getNormalizedValue());
            String display = trimToNull(entry.getSurfaceForm());
            if (normalized == null || display == null) {
                continue;
            }
            formsByNormalized.computeIfAbsent(normalized, ignored -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)).add(display);
            tree.add(normalized);
        }

        return new DictionaryIndex(formsByNormalized, tree);
    }

    private List<ProjectTokenAnalysis> analyzeSources(String workspaceId,
                                                      List<DictionaryDto.ProjectScope> sources,
                                                      VariantSelection variantSelection,
                                                      ControlledDictionary dictionary) {
        if (sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("At least one source is required");
        }

        Map<String, MutableProjectTokenAnalysis> mergedByProject = new LinkedHashMap<>();

        for (DictionaryDto.ProjectScope source : sources) {
            if (source == null || trimToNull(source.projectId()) == null) {
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

            TokenExtractionResult extraction = extractTokens(textRows, variantSelection, dictionary);

            MutableProjectTokenAnalysis merged = mergedByProject.computeIfAbsent(projectId, ignored -> new MutableProjectTokenAnalysis());
            merged.projectName = project.getName();
            merged.pageIds.addAll(pageScope.pageIds());
            merged.pageNamesById.putAll(pageScope.pageNamesById());
            extraction.tokens().forEach((normalized, stats) -> merged.tokens.merge(normalized, stats, TokenStats::merge));
        }

        return mergedByProject.entrySet().stream()
                .map(entry -> new ProjectTokenAnalysis(
                        entry.getKey(),
                        entry.getValue().projectName,
                        new LinkedHashSet<>(entry.getValue().pageIds),
                        new LinkedHashMap<>(entry.getValue().pageNamesById),
                        new LinkedHashMap<>(entry.getValue().tokens)
                ))
                .toList();
    }

    private TokenExtractionResult extractTokens(List<PageTextContent> textRows,
                                               VariantSelection variantSelection,
                                               ControlledDictionary dictionary) {
        Map<String, TokenStats> tokens = new LinkedHashMap<>();

        for (PageTextContent row : textRows) {
            if (row == null || !matchesVariantSelection(row.getVariantIndex(), variantSelection)) {
                continue;
            }
            String text = row.getTextContent();
            if (trimToNull(text) == null) {
                continue;
            }

            String pageId = row.getPage() != null ? row.getPage().getId() : null;
            List<String> extractedTokens = tokenize(text);
            for (String token : extractedTokens) {
                String normalized = normalizeForDictionary(dictionary, token);
                tokens.merge(normalized, new TokenStats(token, normalized, 1, pageId == null ? Set.of() : Set.of(pageId)), TokenStats::merge);
            }
        }

        return new TokenExtractionResult(tokens);
    }

    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return tokens;
        }

        StringBuilder current = new StringBuilder();
        ArrayDeque<Integer> codepoints = text.codePoints().collect(ArrayDeque::new, ArrayDeque::add, ArrayDeque::addAll);

        while (!codepoints.isEmpty()) {
            int cp = codepoints.removeFirst();
            if (Character.isLetterOrDigit(cp)) {
                current.appendCodePoint(cp);
                continue;
            }

            boolean joiner = isTokenJoiner(cp);
            Integer next = codepoints.peekFirst();
            if (joiner
                    && !current.isEmpty()
                    && Character.isLetterOrDigit(current.codePointBefore(current.length()))
                    && next != null
                    && Character.isLetterOrDigit(next)) {
                current.appendCodePoint(cp);
                continue;
            }

            flushToken(current, tokens);
        }

        flushToken(current, tokens);
        return tokens;
    }

    private boolean isTokenJoiner(int cp) {
        return cp == '\'' || cp == '’' || cp == '-' || cp == '‐' || cp == '‑' || cp == '‒' || cp == '–' || cp == '—';
    }

    private void flushToken(StringBuilder current, List<String> tokens) {
        if (current.isEmpty()) {
            return;
        }
        String token = current.toString();
        current.setLength(0);
        if (token.codePoints().anyMatch(Character::isLetterOrDigit)) {
            tokens.add(token);
        }
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
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));
            return new PageScopeResolution(allPageIds, pageNamesById, true);
        }

        Set<String> normalizedRequested = requestedPageIds.stream()
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalizedRequested.isEmpty()) {
            return resolvePageScope(projectId, List.of());
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
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        return new PageScopeResolution(normalizedRequested, pageNamesById, false);
    }

    private VariantSelection resolveVariantSelection(DictionaryDto.VariantScope scope,
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

        DictionaryDto.VariantScope resolvedScope = scope == null ? DictionaryDto.VariantScope.ALL : scope;
        if (resolvedScope == DictionaryDto.VariantScope.PRIMARY) {
            return new VariantSelection(VariantSelectionMode.PRIMARY_COMPAT, null);
        }

        return new VariantSelection(VariantSelectionMode.ALL, null);
    }

    private boolean matchesVariantSelection(Integer rowVariantIndex, VariantSelection selection) {
        return switch (selection.mode()) {
            case ALL -> true;
            case PRIMARY_COMPAT -> rowVariantIndex == null || rowVariantIndex == 0;
            case SPECIFIC_INDEX -> rowVariantIndex != null && rowVariantIndex.equals(selection.variantIndex());
            case UNINDEXED_ONLY -> rowVariantIndex == null;
        };
    }

    private void applyDictionaryMetadata(ControlledDictionary dictionary, DictionaryDto.CreateOrUpdateRequest request) {
        dictionary.setDescription(trimToNull(request.description()));
        dictionary.setTags(normalizeTags(request.tags()));
        dictionary.setCaseSensitive(Boolean.TRUE.equals(request.caseSensitive()));
        dictionary.setUnicodeNormalization(resolveNormalization(request.unicodeNormalization()));
        dictionary.setLocked(Boolean.TRUE.equals(request.locked()));
    }

    private ControlledDictionary requireDictionaryInWorkspace(String workspaceId, String dictionaryId) {
        return dictionaryRepository.findByIdAndLibraryWorkspaceId(dictionaryId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Dictionary not found: " + dictionaryId));
    }

    private void clearDictionaryAssignments(String workspaceId, String dictionaryId) {
        List<Project> projects = projectRepository.findByLibraryWorkspaceIdAndDictionaryId(workspaceId, dictionaryId);
        if (!projects.isEmpty()) {
            for (Project project : projects) {
                project.setDictionary(null);
            }
            projectRepository.saveAll(projects);
        }

        PersonalWorkspace personalWorkspace = personalWorkspaceRepository.findById(workspaceId).orElse(null);
        if (personalWorkspace != null
                && personalWorkspace.getDictionary() != null
                && Objects.equals(personalWorkspace.getDictionary().getId(), dictionaryId)) {
            personalWorkspace.setDictionary(null);
            personalWorkspaceRepository.save(personalWorkspace);
            return;
        }

        TeamWorkspace teamWorkspace = teamWorkspaceRepository.findById(workspaceId).orElse(null);
        if (teamWorkspace != null
                && teamWorkspace.getDictionary() != null
                && Objects.equals(teamWorkspace.getDictionary().getId(), dictionaryId)) {
            teamWorkspace.setDictionary(null);
            teamWorkspaceRepository.save(teamWorkspace);
        }
    }

    private void ensureUniqueNormalizedValue(String dictionaryId, String normalizedValue, String currentEntryId) {
        entryRepository.findByDictionaryIdAndNormalizedValue(dictionaryId, normalizedValue)
                .filter(existing -> !Objects.equals(existing.getId(), currentEntryId))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("An entry with the same normalized value already exists in this dictionary");
                });
    }

    private String normalizeSurfaceForm(String value) {
        return trimToNull(value);
    }

    private String normalizeForDictionary(ControlledDictionary dictionary, String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        normalized = Normalizer.normalize(normalized, toNormalizerForm(dictionary.getUnicodeNormalization()));
        if (!dictionary.isCaseSensitive()) {
            normalized = normalized.toLowerCase(Locale.ROOT);
        }
        return normalized;
    }

    private Normalizer.Form toNormalizerForm(String normalization) {
        return switch (resolveNormalization(normalization)) {
            case "NFD" -> Normalizer.Form.NFD;
            case "NFKC" -> Normalizer.Form.NFKC;
            case "NFKD" -> Normalizer.Form.NFKD;
            default -> Normalizer.Form.NFC;
        };
    }

    private String resolveNormalization(String normalization) {
        String value = trimToNull(normalization);
        if (value == null) {
            return "NFC";
        }
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "NFD", "NFKC", "NFKD", "NFC" -> value.toUpperCase(Locale.ROOT);
            default -> "NFC";
        };
    }

    private Set<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return tags.stream()
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<String> sortedTags(Collection<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return tags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private String toMetadataJson(JsonNode metadata) {
        if (metadata == null || metadata.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize dictionary entry metadata", e);
        }
    }

    private JsonNode readMetadata(String metadataJson) {
        String value = trimToNull(metadataJson);
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String deriveDictionaryName(String originalFilename) {
        String fileName = trimToNull(originalFilename);
        if (fileName == null) {
            return "Imported Dictionary";
        }
        int dot = fileName.lastIndexOf('.');
        String baseName = dot > 0 ? fileName.substring(0, dot) : fileName;
        return trimToNull(baseName) == null ? "Imported Dictionary" : baseName.trim();
    }

    private boolean looksDelimited(String content, char delimiter) {
        if (content == null || content.isBlank()) {
            return false;
        }
        return content.lines().limit(3).anyMatch(line -> line.indexOf(delimiter) >= 0);
    }

    private void invalidateDictionaryCache(String dictionaryId) {
        if (dictionaryId != null) {
            dictionaryIndexCache.invalidate(dictionaryId);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private DictionaryDto.SummaryResponse toSummaryResponse(ControlledDictionary dictionary, String userId) {
        long entryCount = entryRepository.countByDictionaryId(dictionary.getId());
        return new DictionaryDto.SummaryResponse(
                dictionary.getId(),
                dictionary.getName(),
                dictionary.getDescription(),
                sortedTags(dictionary.getTags()),
                dictionary.isCaseSensitive(),
                dictionary.getUnicodeNormalization(),
                dictionary.isLocked(),
                entryCount,
                dictionary.getCreated(),
                dictionary.getUpdated(),
                authorizationPolicyService.resolveWorkspaceResourceCapabilities(dictionary.getLibrary().getWorkspaceId(), userId)
        );
    }

    private DictionaryDto.Response toResponse(ControlledDictionary dictionary, String userId) {
        long entryCount = entryRepository.countByDictionaryId(dictionary.getId());
        return new DictionaryDto.Response(
                dictionary.getId(),
                dictionary.getName(),
                dictionary.getDescription(),
                sortedTags(dictionary.getTags()),
                dictionary.isCaseSensitive(),
                dictionary.getUnicodeNormalization(),
                dictionary.isLocked(),
                entryCount,
                dictionary.getCreated(),
                dictionary.getUpdated(),
                authorizationPolicyService.resolveWorkspaceResourceCapabilities(dictionary.getLibrary().getWorkspaceId(), userId)
        );
    }

    private DictionaryDto.EntryResponse toEntryResponse(ControlledDictionaryEntry entry) {
        return new DictionaryDto.EntryResponse(
                entry.getId(),
                entry.getSurfaceForm(),
                entry.getNormalizedValue(),
                entry.getSourceEntryKey(),
                readMetadata(entry.getMetadataJson()),
                entry.getCreated(),
                entry.getUpdated()
        );
    }

    private record ParsedDictionaryImport(
            String name,
            String description,
            List<String> tags,
            Boolean caseSensitive,
            String unicodeNormalization,
            Boolean locked,
            List<ImportedEntry> entries
    ) {
    }

    private record ImportedEntry(String form, String sourceEntryKey, JsonNode metadata) {
    }

    private record ImportApplyResult(int importedCount, int skippedCount, List<String> warnings) {
    }

    private record DictionaryIndex(Map<String, Set<String>> forms, BKTree tree) {
        boolean contains(String normalized) {
            return forms.containsKey(normalized);
        }
    }

    private record ProjectTokenAnalysis(
            String projectId,
            String projectName,
            Set<String> pageIds,
            Map<String, String> pageNamesById,
            Map<String, TokenStats> tokens
    ) {
    }

    private record TokenExtractionResult(Map<String, TokenStats> tokens) {
    }

    private record TokenStats(String displayValue, String normalizedValue, int occurrenceCount, Set<String> pageIds) {
        private static TokenStats merge(TokenStats left, TokenStats right) {
            Set<String> mergedPages = new LinkedHashSet<>(left.pageIds());
            mergedPages.addAll(right.pageIds());
            return new TokenStats(
                    left.displayValue() != null ? left.displayValue() : right.displayValue(),
                    left.normalizedValue() != null ? left.normalizedValue() : right.normalizedValue(),
                    left.occurrenceCount() + right.occurrenceCount(),
                    mergedPages
            );
        }
    }

    private record PageScopeResolution(Set<String> pageIds, Map<String, String> pageNamesById, boolean allPagesSelected) {
    }

    private record VariantSelection(VariantSelectionMode mode, Integer variantIndex) {
    }

    private enum VariantSelectionMode {
        ALL,
        PRIMARY_COMPAT,
        SPECIFIC_INDEX,
        UNINDEXED_ONLY
    }

    private static class MutableProjectTokenAnalysis {
        private String projectName;
        private final Set<String> pageIds = new LinkedHashSet<>();
        private final Map<String, String> pageNamesById = new LinkedHashMap<>();
        private final Map<String, TokenStats> tokens = new LinkedHashMap<>();
    }

    private record SuggestionCandidate(String normalized, int distance) {
    }

    private static class BKTree {
        private Node root;

        void add(String value) {
            if (value == null || value.isBlank()) {
                return;
            }
            if (root == null) {
                root = new Node(value);
                return;
            }
            root.add(value);
        }

        List<SuggestionCandidate> search(String target, int maxDistance) {
            if (root == null || target == null || target.isBlank()) {
                return List.of();
            }
            List<SuggestionCandidate> results = new ArrayList<>();
            root.search(target, maxDistance, results);
            return results;
        }

        private static class Node {
            private final String value;
            private final Map<Integer, Node> children = new HashMap<>();

            private Node(String value) {
                this.value = value;
            }

            private void add(String candidate) {
                int distance = levenshtein(value, candidate);
                Node child = children.get(distance);
                if (child == null) {
                    children.put(distance, new Node(candidate));
                    return;
                }
                child.add(candidate);
            }

            private void search(String target, int maxDistance, List<SuggestionCandidate> results) {
                int distance = levenshtein(value, target);
                if (distance <= maxDistance) {
                    results.add(new SuggestionCandidate(value, distance));
                }

                int lower = distance - maxDistance;
                int upper = distance + maxDistance;
                for (Map.Entry<Integer, Node> child : children.entrySet()) {
                    if (child.getKey() >= lower && child.getKey() <= upper) {
                        child.getValue().search(target, maxDistance, results);
                    }
                }
            }
        }
    }

    private static int levenshtein(String left, String right) {
        if (Objects.equals(left, right)) {
            return 0;
        }
        if (left == null || left.isEmpty()) {
            return right == null ? 0 : right.codePointCount(0, right.length());
        }
        if (right == null || right.isEmpty()) {
            return left.codePointCount(0, left.length());
        }

        int[] leftCps = left.codePoints().toArray();
        int[] rightCps = right.codePoints().toArray();
        int[] prev = new int[rightCps.length + 1];
        int[] curr = new int[rightCps.length + 1];

        for (int j = 0; j <= rightCps.length; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= leftCps.length; i++) {
            curr[0] = i;
            for (int j = 1; j <= rightCps.length; j++) {
                int cost = leftCps[i - 1] == rightCps[j - 1] ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost
                );
            }
            int[] swap = prev;
            prev = curr;
            curr = swap;
        }
        return prev[rightCps.length];
    }
}

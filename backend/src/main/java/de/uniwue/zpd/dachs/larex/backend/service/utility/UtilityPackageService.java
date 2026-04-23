package de.uniwue.zpd.dachs.larex.backend.service.utility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.uniwue.zpd.dachs.larex.backend.dto.CodecDto;
import de.uniwue.zpd.dachs.larex.backend.dto.DictionaryDto;
import de.uniwue.zpd.dachs.larex.backend.dto.KeyboardItemDto;
import de.uniwue.zpd.dachs.larex.backend.dto.NormalizationProfileDto;
import de.uniwue.zpd.dachs.larex.backend.dto.UtilityPackageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.ValidationRulesetDto;
import de.uniwue.zpd.dachs.larex.backend.dto.VirtualKeyboardDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Codec;
import de.uniwue.zpd.dachs.larex.backend.entity.ControlledDictionary;
import de.uniwue.zpd.dachs.larex.backend.entity.ControlledDictionaryEntry;
import de.uniwue.zpd.dachs.larex.backend.entity.LabelSet;
import de.uniwue.zpd.dachs.larex.backend.entity.NormalizationProfile;
import de.uniwue.zpd.dachs.larex.backend.entity.TagSet;
import de.uniwue.zpd.dachs.larex.backend.entity.ValidationRuleset;
import de.uniwue.zpd.dachs.larex.backend.entity.VirtualKeyboard;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.AbstractWorkspace;
import de.uniwue.zpd.dachs.larex.backend.repository.codec.CodecRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.dictionary.ControlledDictionaryEntryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.dictionary.ControlledDictionaryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.keyboard.VirtualKeyboardRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.label.LabelSetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.normalization.NormalizationProfileRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.tag.TagSetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.validation.ValidationRulesetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceQueryService;
import de.uniwue.zpd.dachs.larex.backend.service.codec.CodecService;
import de.uniwue.zpd.dachs.larex.backend.service.dictionary.DictionaryService;
import de.uniwue.zpd.dachs.larex.backend.service.keyboard.VirtualKeyboardService;
import de.uniwue.zpd.dachs.larex.backend.service.label.LabelSetService;
import de.uniwue.zpd.dachs.larex.backend.service.normalization.NormalizationProfileService;
import de.uniwue.zpd.dachs.larex.backend.service.tag.TagSetService;
import de.uniwue.zpd.dachs.larex.backend.service.validation.ValidationRulesetService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import de.uniwue.zpd.dachs.larex.backend.util.JsonNodeUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

@Service
@Transactional
public class UtilityPackageService {

    private final WorkspaceAccessService workspaceAccessService;
    private final WorkspaceQueryService workspaceQueryService;
    private final CodecRepository codecRepository;
    private final ControlledDictionaryEntryRepository dictionaryEntryRepository;
    private final ControlledDictionaryRepository dictionaryRepository;
    private final LabelSetRepository labelSetRepository;
    private final TagSetRepository tagSetRepository;
    private final NormalizationProfileRepository normalizationProfileRepository;
    private final ValidationRulesetRepository validationRulesetRepository;
    private final VirtualKeyboardRepository virtualKeyboardRepository;
    private final CodecService codecService;
    private final DictionaryService dictionaryService;
    private final LabelSetService labelSetService;
    private final TagSetService tagSetService;
    private final NormalizationProfileService normalizationProfileService;
    private final ValidationRulesetService validationRulesetService;
    private final VirtualKeyboardService virtualKeyboardService;
    private final ObjectMapper objectMapper;

    public UtilityPackageService(WorkspaceAccessService workspaceAccessService,
                                 WorkspaceQueryService workspaceQueryService,
                                 CodecRepository codecRepository,
                                 ControlledDictionaryEntryRepository dictionaryEntryRepository,
                                 ControlledDictionaryRepository dictionaryRepository,
                                 LabelSetRepository labelSetRepository,
                                 TagSetRepository tagSetRepository,
                                 NormalizationProfileRepository normalizationProfileRepository,
                                 ValidationRulesetRepository validationRulesetRepository,
                                 VirtualKeyboardRepository virtualKeyboardRepository,
                                 CodecService codecService,
                                 DictionaryService dictionaryService,
                                 LabelSetService labelSetService,
                                 TagSetService tagSetService,
                                 NormalizationProfileService normalizationProfileService,
                                 ValidationRulesetService validationRulesetService,
                                 VirtualKeyboardService virtualKeyboardService,
                                 ObjectMapper objectMapper) {
        this.workspaceAccessService = workspaceAccessService;
        this.workspaceQueryService = workspaceQueryService;
        this.codecRepository = codecRepository;
        this.dictionaryEntryRepository = dictionaryEntryRepository;
        this.dictionaryRepository = dictionaryRepository;
        this.labelSetRepository = labelSetRepository;
        this.tagSetRepository = tagSetRepository;
        this.normalizationProfileRepository = normalizationProfileRepository;
        this.validationRulesetRepository = validationRulesetRepository;
        this.virtualKeyboardRepository = virtualKeyboardRepository;
        this.codecService = codecService;
        this.dictionaryService = dictionaryService;
        this.labelSetService = labelSetService;
        this.tagSetService = tagSetService;
        this.normalizationProfileService = normalizationProfileService;
        this.validationRulesetService = validationRulesetService;
        this.virtualKeyboardService = virtualKeyboardService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public byte[] exportUtilityPackage(String workspaceId,
                                       String userId,
                                       UtilityPackageDto.ExportRequest request) throws IOException {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        UtilityPackageDto.UtilityPackage utilityPackage = buildUtilityPackage(workspaceId, request);
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(utilityPackage);
    }

    @Transactional(readOnly = true)
    public UtilityPackageDto.UtilityPackage buildUtilityPackage(String workspaceId,
                                                                UtilityPackageDto.ExportRequest request) {
        String workspaceName = workspaceQueryService.findWorkspaceById(workspaceId)
                .map(AbstractWorkspace::getName)
                .orElse(workspaceId);

        Map<UtilityPackageDto.UtilityType, Set<String>> selectors = parseSelectors(request == null ? null : request.selectors());
        boolean includeAll = request == null || request.includeAllResolved() || selectors.isEmpty();

        List<UtilityPackageDto.UtilityResource> resources = new ArrayList<>();

        if (includeAll || selectors.containsKey(UtilityPackageDto.UtilityType.CODEC)) {
            Collection<Codec> codecs = includeAll
                    ? codecRepository.findByLibraryWorkspaceId(workspaceId)
                    : codecRepository.findByLibraryWorkspaceId(workspaceId).stream()
                    .filter(c -> selectors.getOrDefault(UtilityPackageDto.UtilityType.CODEC, Set.of()).contains(c.getId()))
                    .toList();
            codecs.stream()
                    .map(this::toCodecResource)
                    .forEach(resources::add);
        }

        if (includeAll || selectors.containsKey(UtilityPackageDto.UtilityType.DICTIONARY)) {
            Collection<ControlledDictionary> dictionaries = includeAll
                    ? dictionaryRepository.findByLibraryWorkspaceId(workspaceId)
                    : dictionaryRepository.findByLibraryWorkspaceId(workspaceId).stream()
                    .filter(d -> selectors.getOrDefault(UtilityPackageDto.UtilityType.DICTIONARY, Set.of()).contains(d.getId()))
                    .toList();
            dictionaries.stream()
                    .map(this::toDictionaryResource)
                    .forEach(resources::add);
        }

        if (includeAll || selectors.containsKey(UtilityPackageDto.UtilityType.LABEL_SET)) {
            Collection<LabelSet> labelSets = includeAll
                    ? labelSetRepository.findByWorkspaceId(workspaceId)
                    : labelSetRepository.findByWorkspaceId(workspaceId).stream()
                    .filter(l -> selectors.getOrDefault(UtilityPackageDto.UtilityType.LABEL_SET, Set.of()).contains(l.getId()))
                    .toList();
            labelSets.stream()
                    .map(this::toLabelSetResource)
                    .forEach(resources::add);
        }

        if (includeAll || selectors.containsKey(UtilityPackageDto.UtilityType.TAG_SET)) {
            Collection<TagSet> tagSets = includeAll
                    ? tagSetRepository.findByWorkspaceId(workspaceId)
                    : tagSetRepository.findByWorkspaceId(workspaceId).stream()
                    .filter(t -> selectors.getOrDefault(UtilityPackageDto.UtilityType.TAG_SET, Set.of()).contains(t.getId()))
                    .toList();
            tagSets.stream()
                    .map(this::toTagSetResource)
                    .forEach(resources::add);
        }

        if (includeAll || selectors.containsKey(UtilityPackageDto.UtilityType.NORMALIZATION_PROFILE)) {
            Collection<NormalizationProfile> profiles = includeAll
                    ? normalizationProfileRepository.findByWorkspaceId(workspaceId)
                    : normalizationProfileRepository.findByWorkspaceId(workspaceId).stream()
                    .filter(p -> selectors.getOrDefault(UtilityPackageDto.UtilityType.NORMALIZATION_PROFILE, Set.of()).contains(p.getId()))
                    .toList();
            profiles.stream()
                    .map(this::toNormalizationProfileResource)
                    .forEach(resources::add);
        }

        if (includeAll || selectors.containsKey(UtilityPackageDto.UtilityType.VALIDATION_RULESET)) {
            Collection<ValidationRuleset> rulesets = includeAll
                    ? validationRulesetRepository.findByWorkspaceId(workspaceId)
                    : validationRulesetRepository.findByWorkspaceId(workspaceId).stream()
                    .filter(r -> selectors.getOrDefault(UtilityPackageDto.UtilityType.VALIDATION_RULESET, Set.of()).contains(r.getId()))
                    .toList();
            rulesets.stream()
                    .map(this::toValidationRulesetResource)
                    .forEach(resources::add);
        }

        if (includeAll || selectors.containsKey(UtilityPackageDto.UtilityType.VIRTUAL_KEYBOARD)) {
            Collection<VirtualKeyboard> keyboards = includeAll
                    ? virtualKeyboardRepository.findByWorkspaceId(workspaceId)
                    : virtualKeyboardRepository.findByWorkspaceId(workspaceId).stream()
                    .filter(v -> selectors.getOrDefault(UtilityPackageDto.UtilityType.VIRTUAL_KEYBOARD, Set.of()).contains(v.getId()))
                    .toList();
            keyboards.stream()
                    .map(this::toVirtualKeyboardResource)
                    .forEach(resources::add);
        }

        resources.sort(Comparator
                .comparing((UtilityPackageDto.UtilityResource r) -> r.type().name())
                .thenComparing(UtilityPackageDto.UtilityResource::name, Comparator.nullsLast(String::compareToIgnoreCase)));

        return new UtilityPackageDto.UtilityPackage(
                new UtilityPackageDto.PackageMeta(
                        "1.0",
                        LocalDateTime.now(),
                        workspaceId,
                        workspaceName
                ),
                resources
        );
    }

    @Transactional(readOnly = true)
    public UtilityPackageDto.UtilityPackage buildProjectUtilitySnapshot(String workspaceId,
                                                                        String codecId,
                                                                        String labelSetId,
                                                                        String dictionaryId,
                                                                        String tagSetId,
                                                                        String normalizationProfileId,
                                                                        String validationRulesetId) {
        List<UtilityPackageDto.ResourceSelector> selectors = new ArrayList<>();
        if (codecId != null && !codecId.isBlank()) {
            selectors.add(new UtilityPackageDto.ResourceSelector(UtilityPackageDto.UtilityType.CODEC, List.of(codecId)));
        }
        if (labelSetId != null && !labelSetId.isBlank()) {
            selectors.add(new UtilityPackageDto.ResourceSelector(UtilityPackageDto.UtilityType.LABEL_SET, List.of(labelSetId)));
        }
        if (dictionaryId != null && !dictionaryId.isBlank()) {
            selectors.add(new UtilityPackageDto.ResourceSelector(UtilityPackageDto.UtilityType.DICTIONARY, List.of(dictionaryId)));
        }
        if (tagSetId != null && !tagSetId.isBlank()) {
            selectors.add(new UtilityPackageDto.ResourceSelector(UtilityPackageDto.UtilityType.TAG_SET, List.of(tagSetId)));
        }
        if (normalizationProfileId != null && !normalizationProfileId.isBlank()) {
            selectors.add(new UtilityPackageDto.ResourceSelector(UtilityPackageDto.UtilityType.NORMALIZATION_PROFILE, List.of(normalizationProfileId)));
        }
        if (validationRulesetId != null && !validationRulesetId.isBlank()) {
            selectors.add(new UtilityPackageDto.ResourceSelector(UtilityPackageDto.UtilityType.VALIDATION_RULESET, List.of(validationRulesetId)));
        }
        return buildUtilityPackage(workspaceId, new UtilityPackageDto.ExportRequest(selectors, false));
    }

    public UtilityPackageDto.ImportResult importUtilityPackageFromContent(String workspaceId,
                                                                           String userId,
                                                                           String content) throws IOException {
        workspaceAccessService.requireAdminAccess(workspaceId, userId);
        return importUtilityPackageFromContentInternal(workspaceId, userId, content);
    }

    public UtilityPackageDto.ImportResult importUtilityPackageFromContentInternal(String workspaceId,
                                                                                   String userId,
                                                                                   String content) throws IOException {
        JsonNode root = objectMapper.readTree(content);
        UtilityPackageDto.UtilityPackage utilityPackage = parsePackageOrLegacy(workspaceId, root);
        return doImportUtilityPackage(workspaceId, userId, utilityPackage);
    }

    public UtilityPackageDto.ImportResult importUtilityPackage(String workspaceId,
                                                                String userId,
                                                                UtilityPackageDto.UtilityPackage utilityPackage) {
        workspaceAccessService.requireAdminAccess(workspaceId, userId);
        return doImportUtilityPackage(workspaceId, userId, utilityPackage);
    }

    public UtilityPackageDto.ImportResult importUtilityPackageInternal(String workspaceId,
                                                                       String userId,
                                                                       UtilityPackageDto.UtilityPackage utilityPackage) {
        return doImportUtilityPackage(workspaceId, userId, utilityPackage);
    }

    private UtilityPackageDto.ImportResult doImportUtilityPackage(String workspaceId,
                                                                  String userId,
                                                                  UtilityPackageDto.UtilityPackage utilityPackage) {
        List<UtilityPackageDto.ImportedResource> resources = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, String> sourceToTarget = new LinkedHashMap<>();

        if (utilityPackage == null || utilityPackage.resources() == null) {
            return new UtilityPackageDto.ImportResult(workspaceId, 0, 0, List.of(), warnings, sourceToTarget);
        }

        for (UtilityPackageDto.UtilityResource resource : utilityPackage.resources()) {
            if (resource == null || resource.type() == null || resource.payload() == null) {
                warnings.add("Skipped invalid utility entry");
                continue;
            }

            UtilityPackageDto.ImportedResource imported = switch (resource.type()) {
                case CODEC -> importCodec(workspaceId, userId, resource);
                case DICTIONARY -> importDictionary(workspaceId, userId, resource);
                case LABEL_SET -> importLabelSet(workspaceId, userId, resource);
                case TAG_SET -> importTagSet(workspaceId, userId, resource);
                case NORMALIZATION_PROFILE -> importNormalizationProfile(workspaceId, userId, resource);
                case VALIDATION_RULESET -> importValidationRuleset(workspaceId, userId, resource);
                case VIRTUAL_KEYBOARD -> importVirtualKeyboard(workspaceId, userId, resource);
            };
            resources.add(imported);
            if (resource.sourceId() != null && imported.targetId() != null) {
                sourceToTarget.put(resource.sourceId(), imported.targetId());
            }
        }

        int reusedCount = (int) resources.stream().filter(r -> "REUSED".equals(r.action())).count();
        int importedCount = resources.size() - reusedCount;

        return new UtilityPackageDto.ImportResult(
                workspaceId,
                importedCount,
                reusedCount,
                resources,
                warnings,
                sourceToTarget
        );
    }

    private UtilityPackageDto.ImportedResource importCodec(String workspaceId,
                                                           String userId,
                                                           UtilityPackageDto.UtilityResource resource) {
        CodecDto.CreateOrUpdateRequest request = objectMapper.convertValue(resource.payload(), CodecDto.CreateOrUpdateRequest.class);
        String sourceName = normalizeName(request.name(), resource.name(), "Imported Codec");

        Optional<Codec> existingOpt = codecRepository.findByNameAndLibraryWorkspaceId(sourceName, workspaceId);
        if (existingOpt.isPresent() && payloadEquals(codecPayload(existingOpt.get()), codecPayloadFromRequest(request, sourceName))) {
            Codec existing = existingOpt.get();
            return new UtilityPackageDto.ImportedResource(
                    UtilityPackageDto.UtilityType.CODEC,
                    resource.sourceId(),
                    existing.getId(),
                    sourceName,
                    existing.getName(),
                    "REUSED",
                    "Identical codec already exists"
            );
        }

        String targetName = existingOpt.isPresent()
                ? uniqueName(sourceName, name -> codecRepository.findByNameAndLibraryWorkspaceId(name, workspaceId).isPresent())
                : sourceName;

        CodecDto.CreateOrUpdateRequest createRequest = new CodecDto.CreateOrUpdateRequest(
                targetName,
                request.description(),
                request.tags(),
                request.codec()
        );

        CodecDto.Response created = codecService.createCodec(userId, workspaceId, createRequest);
        return new UtilityPackageDto.ImportedResource(
                UtilityPackageDto.UtilityType.CODEC,
                resource.sourceId(),
                created.id(),
                sourceName,
                created.name(),
                existingOpt.isPresent() ? "RENAMED_IMPORTED" : "IMPORTED",
                existingOpt.isPresent() ? "Name conflict with different content" : "Created"
        );
    }

    private UtilityPackageDto.ImportedResource importDictionary(String workspaceId,
                                                                String userId,
                                                                UtilityPackageDto.UtilityResource resource) {
        DictionaryDto.PackagePayload payload = objectMapper.convertValue(sanitizeDictionaryPayload(resource.payload()), DictionaryDto.PackagePayload.class);
        String sourceName = normalizeName(payload.name(), resource.name(), "Imported Dictionary");

        Optional<ControlledDictionary> existingOpt = dictionaryRepository.findByNameAndLibraryWorkspaceId(sourceName, workspaceId);
        if (existingOpt.isPresent() && payloadEquals(dictionaryPayload(existingOpt.get()), dictionaryPayloadFromPayload(payload, sourceName))) {
            ControlledDictionary existing = existingOpt.get();
            return new UtilityPackageDto.ImportedResource(
                    UtilityPackageDto.UtilityType.DICTIONARY,
                    resource.sourceId(),
                    existing.getId(),
                    sourceName,
                    existing.getName(),
                    "REUSED",
                    "Identical dictionary already exists"
            );
        }

        String targetName = existingOpt.isPresent()
                ? uniqueName(sourceName, name -> dictionaryRepository.findByNameAndLibraryWorkspaceId(name, workspaceId).isPresent())
                : sourceName;

        String createdId = dictionaryService.importDictionaryFromPackage(userId, workspaceId, targetName, payload);
        return new UtilityPackageDto.ImportedResource(
                UtilityPackageDto.UtilityType.DICTIONARY,
                resource.sourceId(),
                createdId,
                sourceName,
                targetName,
                existingOpt.isPresent() ? "RENAMED_IMPORTED" : "IMPORTED",
                existingOpt.isPresent() ? "Name conflict with different content" : "Created"
        );
    }

    private UtilityPackageDto.ImportedResource importLabelSet(String workspaceId,
                                                              String userId,
                                                              UtilityPackageDto.UtilityResource resource) {
        ObjectNode requestNode = sanitizeLabelPayload(resource.payload());
        ObjectNode meta = ensureObject(requestNode, "meta");
        String sourceName = normalizeName(meta.path("name").asText(null), resource.name(), "Imported Label Set");

        Optional<LabelSet> existingOpt = labelSetRepository.findByNameAndWorkspaceId(sourceName, workspaceId);
        if (existingOpt.isPresent() && payloadEquals(labelSetPayload(existingOpt.get()), requestNode)) {
            LabelSet existing = existingOpt.get();
            return new UtilityPackageDto.ImportedResource(
                    UtilityPackageDto.UtilityType.LABEL_SET,
                    resource.sourceId(),
                    existing.getId(),
                    sourceName,
                    existing.getName(),
                    "REUSED",
                    "Identical label set already exists"
            );
        }

        String targetName = existingOpt.isPresent()
                ? uniqueName(sourceName, name -> labelSetRepository.findByNameAndWorkspaceId(name, workspaceId).isPresent())
                : sourceName;
        meta.put("name", targetName);

        var created = labelSetService.createLabelSet(userId, workspaceId, requestNode);

        return new UtilityPackageDto.ImportedResource(
                UtilityPackageDto.UtilityType.LABEL_SET,
                resource.sourceId(),
                created.id(),
                sourceName,
                created.meta().name(),
                existingOpt.isPresent() ? "RENAMED_IMPORTED" : "IMPORTED",
                existingOpt.isPresent() ? "Name conflict with different content" : "Created"
        );
    }

    private UtilityPackageDto.ImportedResource importTagSet(String workspaceId,
                                                            String userId,
                                                            UtilityPackageDto.UtilityResource resource) {
        ObjectNode requestNode = sanitizeTagPayload(resource.payload());
        ObjectNode meta = ensureObject(requestNode, "meta");
        String sourceName = normalizeName(meta.path("name").asText(null), resource.name(), "Imported Tag Set");

        Optional<TagSet> existingOpt = tagSetRepository.findByNameAndWorkspaceId(sourceName, workspaceId);
        if (existingOpt.isPresent() && payloadEquals(tagSetPayload(existingOpt.get()), requestNode)) {
            TagSet existing = existingOpt.get();
            return new UtilityPackageDto.ImportedResource(
                    UtilityPackageDto.UtilityType.TAG_SET,
                    resource.sourceId(),
                    existing.getId(),
                    sourceName,
                    existing.getName(),
                    "REUSED",
                    "Identical tag set already exists"
            );
        }

        String targetName = existingOpt.isPresent()
                ? uniqueName(sourceName, name -> tagSetRepository.findByNameAndWorkspaceId(name, workspaceId).isPresent())
                : sourceName;
        meta.put("name", targetName);

        var created = tagSetService.createTagSet(userId, workspaceId, requestNode);

        return new UtilityPackageDto.ImportedResource(
                UtilityPackageDto.UtilityType.TAG_SET,
                resource.sourceId(),
                created.id(),
                sourceName,
                created.meta().name(),
                existingOpt.isPresent() ? "RENAMED_IMPORTED" : "IMPORTED",
                existingOpt.isPresent() ? "Name conflict with different content" : "Created"
        );
    }

    private UtilityPackageDto.ImportedResource importNormalizationProfile(String workspaceId,
                                                                          String userId,
                                                                          UtilityPackageDto.UtilityResource resource) {
        NormalizationProfileDto.CreateOrUpdateRequest request = objectMapper.convertValue(
                sanitizeNormalizationProfilePayload(resource.payload()),
                NormalizationProfileDto.CreateOrUpdateRequest.class
        );
        String sourceName = normalizeName(request.name(), resource.name(), "Imported Normalization Profile");

        Optional<NormalizationProfile> existingOpt = normalizationProfileRepository.findByNameAndWorkspaceId(sourceName, workspaceId);
        if (existingOpt.isPresent() && payloadEquals(normalizationProfilePayload(existingOpt.get()), normalizationProfilePayloadFromRequest(request, sourceName))) {
            NormalizationProfile existing = existingOpt.get();
            return new UtilityPackageDto.ImportedResource(
                    UtilityPackageDto.UtilityType.NORMALIZATION_PROFILE,
                    resource.sourceId(),
                    existing.getId(),
                    sourceName,
                    existing.getName(),
                    "REUSED",
                    "Identical normalization profile already exists"
            );
        }

        String targetName = existingOpt.isPresent()
                ? uniqueName(sourceName, name -> normalizationProfileRepository.findByNameAndWorkspaceId(name, workspaceId).isPresent())
                : sourceName;

        NormalizationProfileDto.CreateOrUpdateRequest createRequest = new NormalizationProfileDto.CreateOrUpdateRequest(
                targetName,
                request.description(),
                request.tags(),
                request.unicodeNormalization(),
                request.collapseWhitespace(),
                request.trimText(),
                request.dehyphenateLineBreaks(),
                request.mapLongSToS(),
                request.expandCommonLigatures(),
                request.normalizeQuotes(),
                request.normalizeDashes(),
                request.normalizeEllipsis(),
                request.replacementRules()
        );

        NormalizationProfileDto.Response created = normalizationProfileService.createProfile(userId, workspaceId, createRequest);
        return new UtilityPackageDto.ImportedResource(
                UtilityPackageDto.UtilityType.NORMALIZATION_PROFILE,
                resource.sourceId(),
                created.id(),
                sourceName,
                created.name(),
                existingOpt.isPresent() ? "RENAMED_IMPORTED" : "IMPORTED",
                existingOpt.isPresent() ? "Name conflict with different content" : "Created"
        );
    }

    private UtilityPackageDto.ImportedResource importValidationRuleset(String workspaceId,
                                                                       String userId,
                                                                       UtilityPackageDto.UtilityResource resource) {
        ValidationRulesetDto.CreateOrUpdateRequest request = objectMapper.convertValue(
                sanitizeValidationRulesetPayload(resource.payload()),
                ValidationRulesetDto.CreateOrUpdateRequest.class
        );
        String sourceName = normalizeName(request.name(), resource.name(), "Imported Validation Ruleset");

        Optional<ValidationRuleset> existingOpt = validationRulesetRepository.findByNameAndWorkspaceId(sourceName, workspaceId);
        if (existingOpt.isPresent() && payloadEquals(validationRulesetPayload(existingOpt.get()), validationRulesetPayloadFromRequest(request, sourceName))) {
            ValidationRuleset existing = existingOpt.get();
            return new UtilityPackageDto.ImportedResource(
                    UtilityPackageDto.UtilityType.VALIDATION_RULESET,
                    resource.sourceId(),
                    existing.getId(),
                    sourceName,
                    existing.getName(),
                    "REUSED",
                    "Identical validation ruleset already exists"
            );
        }

        String targetName = existingOpt.isPresent()
                ? uniqueName(sourceName, name -> validationRulesetRepository.findByNameAndWorkspaceId(name, workspaceId).isPresent())
                : sourceName;

        ValidationRulesetDto.CreateOrUpdateRequest createRequest = new ValidationRulesetDto.CreateOrUpdateRequest(
                targetName,
                request.description(),
                request.tags(),
                request.rules()
        );

        ValidationRulesetDto.Response created = validationRulesetService.createRuleset(userId, workspaceId, createRequest);
        return new UtilityPackageDto.ImportedResource(
                UtilityPackageDto.UtilityType.VALIDATION_RULESET,
                resource.sourceId(),
                created.id(),
                sourceName,
                created.name(),
                existingOpt.isPresent() ? "RENAMED_IMPORTED" : "IMPORTED",
                existingOpt.isPresent() ? "Name conflict with different content" : "Created"
        );
    }

    private UtilityPackageDto.ImportedResource importVirtualKeyboard(String workspaceId,
                                                                     String userId,
                                                                     UtilityPackageDto.UtilityResource resource) {
        VirtualKeyboardDto dto = objectMapper.convertValue(sanitizeVirtualKeyboardPayload(resource.payload()), VirtualKeyboardDto.class);
        String sourceName = normalizeName(dto.getName(), resource.name(), "Imported Keyboard");

        Optional<VirtualKeyboard> existingOpt = virtualKeyboardRepository.findByNameAndWorkspaceId(sourceName, workspaceId);
        if (existingOpt.isPresent() && payloadEquals(virtualKeyboardPayload(existingOpt.get()), virtualKeyboardPayloadFromDto(dto, sourceName))) {
            VirtualKeyboard existing = existingOpt.get();
            return new UtilityPackageDto.ImportedResource(
                    UtilityPackageDto.UtilityType.VIRTUAL_KEYBOARD,
                    resource.sourceId(),
                    existing.getId(),
                    sourceName,
                    existing.getName(),
                    "REUSED",
                    "Identical virtual keyboard already exists"
            );
        }

        String targetName = existingOpt.isPresent()
                ? uniqueName(sourceName, name -> virtualKeyboardRepository.findByNameAndWorkspaceId(name, workspaceId).isPresent())
                : sourceName;

        dto.setId(null);
        dto.setName(targetName);
        if (dto.getItems() != null) {
            dto.getItems().forEach(item -> item.setId(null));
        }

        VirtualKeyboardDto created = virtualKeyboardService.createKeyboard(userId, workspaceId, dto);
        return new UtilityPackageDto.ImportedResource(
                UtilityPackageDto.UtilityType.VIRTUAL_KEYBOARD,
                resource.sourceId(),
                created.getId(),
                sourceName,
                created.getName(),
                existingOpt.isPresent() ? "RENAMED_IMPORTED" : "IMPORTED",
                existingOpt.isPresent() ? "Name conflict with different content" : "Created"
        );
    }

    private UtilityPackageDto.UtilityPackage parsePackageOrLegacy(String workspaceId, JsonNode root) {
        if (root != null && root.has("resources")) {
            return objectMapper.convertValue(root, UtilityPackageDto.UtilityPackage.class);
        }

        UtilityPackageDto.UtilityType type = detectLegacyType(root);
        if (type == null) {
            throw new IllegalArgumentException("Unsupported utility import payload");
        }

        String name = extractLegacyName(root, type);
        UtilityPackageDto.UtilityResource resource = new UtilityPackageDto.UtilityResource(
                type,
                null,
                name,
                null,
                null,
                normalizeLegacyPayload(type, root)
        );

        String workspaceName = workspaceQueryService.findWorkspaceById(workspaceId)
                .map(AbstractWorkspace::getName)
                .orElse(workspaceId);

        return new UtilityPackageDto.UtilityPackage(
                new UtilityPackageDto.PackageMeta("1.0", LocalDateTime.now(), workspaceId, workspaceName),
                List.of(resource)
        );
    }

    private UtilityPackageDto.UtilityType detectLegacyType(JsonNode root) {
        if (root == null || root.isNull() || !root.isObject()) {
            return null;
        }
        if (root.has("codec") && root.has("name")) {
            return UtilityPackageDto.UtilityType.CODEC;
        }
        if (root.has("labels") && root.has("meta")) {
            return UtilityPackageDto.UtilityType.LABEL_SET;
        }
        if (root.has("items") && root.has("cols") && root.has("rows")) {
            return UtilityPackageDto.UtilityType.VIRTUAL_KEYBOARD;
        }
        if (root.has("tags") && root.has("meta") && root.path("tags").isArray()) {
            JsonNode first = root.path("tags").isEmpty() ? null : root.path("tags").get(0);
            if (first == null || first.has("title") || first.has("children")) {
                return UtilityPackageDto.UtilityType.TAG_SET;
            }
        }
        if (root.has("unicodeNormalization") && root.has("collapseWhitespace")) {
            return UtilityPackageDto.UtilityType.NORMALIZATION_PROFILE;
        }
        if (root.has("rules") && root.path("rules").isArray()) {
            return UtilityPackageDto.UtilityType.VALIDATION_RULESET;
        }
        return null;
    }

    private String extractLegacyName(JsonNode root, UtilityPackageDto.UtilityType type) {
        if (root == null) {
            return null;
        }
        return switch (type) {
            case CODEC, DICTIONARY, NORMALIZATION_PROFILE, VALIDATION_RULESET, VIRTUAL_KEYBOARD -> root.path("name").asText(null);
            case LABEL_SET, TAG_SET -> root.path("meta").path("name").asText(null);
        };
    }

    private JsonNode normalizeLegacyPayload(UtilityPackageDto.UtilityType type, JsonNode root) {
        return switch (type) {
            case CODEC -> sanitizeCodecPayload(root);
            case DICTIONARY -> sanitizeDictionaryPayload(root);
            case LABEL_SET -> sanitizeLabelPayload(root);
            case TAG_SET -> sanitizeTagPayload(root);
            case NORMALIZATION_PROFILE -> sanitizeNormalizationProfilePayload(root);
            case VALIDATION_RULESET -> sanitizeValidationRulesetPayload(root);
            case VIRTUAL_KEYBOARD -> sanitizeVirtualKeyboardPayload(root);
        };
    }

    private UtilityPackageDto.UtilityResource toCodecResource(Codec codec) {
        return new UtilityPackageDto.UtilityResource(
                UtilityPackageDto.UtilityType.CODEC,
                codec.getId(),
                codec.getName(),
                codec.getCreated(),
                codec.getUpdated(),
                codecPayload(codec)
        );
    }

    private UtilityPackageDto.UtilityResource toDictionaryResource(ControlledDictionary dictionary) {
        return new UtilityPackageDto.UtilityResource(
                UtilityPackageDto.UtilityType.DICTIONARY,
                dictionary.getId(),
                dictionary.getName(),
                dictionary.getCreated(),
                dictionary.getUpdated(),
                dictionaryPayload(dictionary)
        );
    }

    private UtilityPackageDto.UtilityResource toLabelSetResource(LabelSet labelSet) {
        return new UtilityPackageDto.UtilityResource(
                UtilityPackageDto.UtilityType.LABEL_SET,
                labelSet.getId(),
                labelSet.getName(),
                labelSet.getCreated(),
                labelSet.getUpdated(),
                labelSetPayload(labelSet)
        );
    }

    private UtilityPackageDto.UtilityResource toTagSetResource(TagSet tagSet) {
        return new UtilityPackageDto.UtilityResource(
                UtilityPackageDto.UtilityType.TAG_SET,
                tagSet.getId(),
                tagSet.getName(),
                tagSet.getCreated(),
                tagSet.getUpdated(),
                tagSetPayload(tagSet)
        );
    }

    private UtilityPackageDto.UtilityResource toNormalizationProfileResource(NormalizationProfile profile) {
        return new UtilityPackageDto.UtilityResource(
                UtilityPackageDto.UtilityType.NORMALIZATION_PROFILE,
                profile.getId(),
                profile.getName(),
                profile.getCreated(),
                profile.getUpdated(),
                normalizationProfilePayload(profile)
        );
    }

    private UtilityPackageDto.UtilityResource toValidationRulesetResource(ValidationRuleset ruleset) {
        return new UtilityPackageDto.UtilityResource(
                UtilityPackageDto.UtilityType.VALIDATION_RULESET,
                ruleset.getId(),
                ruleset.getName(),
                ruleset.getCreated(),
                ruleset.getUpdated(),
                validationRulesetPayload(ruleset)
        );
    }

    private UtilityPackageDto.UtilityResource toVirtualKeyboardResource(VirtualKeyboard keyboard) {
        return new UtilityPackageDto.UtilityResource(
                UtilityPackageDto.UtilityType.VIRTUAL_KEYBOARD,
                keyboard.getId(),
                keyboard.getName(),
                null,
                null,
                virtualKeyboardPayload(keyboard)
        );
    }

    private JsonNode codecPayload(Codec codec) {
        List<String> codecChars = codec.getCharacters() == null ? List.of() : codec.getCharacters().stream()
                .filter(Objects::nonNull)
                .filter(v -> !v.isBlank())
                .sorted()
                .toList();

        List<String> tags = codec.getTags() == null ? List.of() : codec.getTags().stream()
                .filter(Objects::nonNull)
                .filter(v -> !v.isBlank())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        return codecPayloadFromRequest(new CodecDto.CreateOrUpdateRequest(
                codec.getName(),
                codec.getDescription(),
                tags,
                codecChars
        ), codec.getName());
    }

    private JsonNode codecPayloadFromRequest(CodecDto.CreateOrUpdateRequest request, String nameOverride) {
        String name = normalizeName(nameOverride, request.name(), "Codec");
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", name);
        node.put("description", request.description() == null ? "" : request.description());

        ArrayNode tags = objectMapper.createArrayNode();
        if (request.tags() != null) {
            request.tags().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(v -> !v.isBlank())
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(tags::add);
        }
        node.set("tags", tags);

        ArrayNode codec = objectMapper.createArrayNode();
        if (request.codec() != null) {
            request.codec().stream()
                    .filter(Objects::nonNull)
                    .filter(v -> !v.isEmpty())
                    .sorted()
                    .forEach(codec::add);
        }
        node.set("codec", codec);
        return node;
    }

    private JsonNode labelSetPayload(LabelSet labelSet) {
        ObjectNode node = sanitizeLabelPayload(labelSet.getDefinition());
        ObjectNode meta = ensureObject(node, "meta");
        meta.put("name", labelSet.getName());
        meta.put("description", labelSet.getDescription() == null ? "" : labelSet.getDescription());

        ArrayNode tags = objectMapper.createArrayNode();
        if (labelSet.getTags() != null) {
            labelSet.getTags().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(v -> !v.isBlank())
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(tags::add);
        }
        meta.set("tags", tags);
        meta.put("isSystem", labelSet.isSystem());
        return node;
    }

    private JsonNode dictionaryPayload(ControlledDictionary dictionary) {
        List<DictionaryDto.PackageEntry> entries = dictionaryEntryRepository.findByDictionaryIdOrderBySurfaceFormAsc(dictionary.getId()).stream()
                .map(this::toDictionaryPackageEntry)
                .toList();
        DictionaryDto.PackagePayload payload = new DictionaryDto.PackagePayload(
                dictionary.getName(),
                dictionary.getDescription(),
                dictionary.getTags() == null ? List.of() : dictionary.getTags().stream()
                        .filter(Objects::nonNull)
                        .filter(v -> !v.isBlank())
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList(),
                dictionary.isCaseSensitive(),
                dictionary.getUnicodeNormalization(),
                dictionary.isLocked(),
                entries
        );
        return dictionaryPayloadFromPayload(payload, dictionary.getName());
    }

    private DictionaryDto.PackageEntry toDictionaryPackageEntry(ControlledDictionaryEntry entry) {
        JsonNode metadata = null;
        if (entry.getMetadataJson() != null && !entry.getMetadataJson().isBlank()) {
            try {
                metadata = objectMapper.readTree(entry.getMetadataJson());
            } catch (IOException ignored) {
                metadata = null;
            }
        }
        return new DictionaryDto.PackageEntry(entry.getSurfaceForm(), entry.getSourceEntryKey(), metadata);
    }

    private JsonNode dictionaryPayloadFromPayload(DictionaryDto.PackagePayload payload, String nameOverride) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", normalizeName(nameOverride, payload.name(), "Dictionary"));
        node.put("description", payload.description() == null ? "" : payload.description());
        node.put("caseSensitive", Boolean.TRUE.equals(payload.caseSensitive()));
        node.put("unicodeNormalization", payload.unicodeNormalization() == null ? "NFC" : payload.unicodeNormalization());

        ArrayNode tags = objectMapper.createArrayNode();
        if (payload.tags() != null) {
            payload.tags().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(v -> !v.isBlank())
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(tags::add);
        }
        node.set("tags", tags);

        ArrayNode entries = objectMapper.createArrayNode();
        if (payload.entries() != null) {
            payload.entries().stream()
                    .sorted(Comparator.comparing(DictionaryDto.PackageEntry::form, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                    .forEach(entry -> {
                        ObjectNode entryNode = objectMapper.createObjectNode();
                        entryNode.put("form", entry.form());
                        if (entry.sourceEntryKey() != null) {
                            entryNode.put("sourceEntryKey", entry.sourceEntryKey());
                        }
                        if (entry.metadata() != null) {
                            entryNode.set("metadata", sortNode(entry.metadata()));
                        }
                        entries.add(entryNode);
                    });
        }
        node.set("entries", entries);
        return node;
    }

    private JsonNode tagSetPayload(TagSet tagSet) {
        ObjectNode node = sanitizeTagPayload(tagSet.getDefinition());
        ObjectNode meta = ensureObject(node, "meta");
        meta.put("name", tagSet.getName());
        meta.put("description", tagSet.getDescription() == null ? "" : tagSet.getDescription());

        ArrayNode tags = objectMapper.createArrayNode();
        if (tagSet.getTags() != null) {
            tagSet.getTags().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(v -> !v.isBlank())
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(tags::add);
        }
        meta.set("tags", tags);
        return node;
    }

    private JsonNode normalizationProfilePayload(NormalizationProfile profile) {
        return normalizationProfilePayloadFromRequest(new NormalizationProfileDto.CreateOrUpdateRequest(
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
                        .toList()
        ), profile.getName());
    }

    private JsonNode normalizationProfilePayloadFromRequest(NormalizationProfileDto.CreateOrUpdateRequest request, String nameOverride) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", normalizeName(nameOverride, request.name(), "Normalization Profile"));
        node.put("description", request.description() == null ? "" : request.description());
        node.put("unicodeNormalization", request.unicodeNormalization() == null ? "NFC" : request.unicodeNormalization());
        node.put("collapseWhitespace", !Boolean.FALSE.equals(request.collapseWhitespace()));
        node.put("trimText", !Boolean.FALSE.equals(request.trimText()));
        node.put("dehyphenateLineBreaks", Boolean.TRUE.equals(request.dehyphenateLineBreaks()));
        node.put("mapLongSToS", Boolean.TRUE.equals(request.mapLongSToS()));
        node.put("expandCommonLigatures", Boolean.TRUE.equals(request.expandCommonLigatures()));
        node.put("normalizeQuotes", Boolean.TRUE.equals(request.normalizeQuotes()));
        node.put("normalizeDashes", Boolean.TRUE.equals(request.normalizeDashes()));
        node.put("normalizeEllipsis", Boolean.TRUE.equals(request.normalizeEllipsis()));
        ArrayNode tags = objectMapper.createArrayNode();
        if (request.tags() != null) {
            request.tags().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(v -> !v.isBlank())
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(tags::add);
        }
        node.set("tags", tags);

        ArrayNode replacementRules = objectMapper.createArrayNode();
        if (request.replacementRules() != null) {
            request.replacementRules().forEach(rule -> {
                if (rule == null) {
                    return;
                }
                ObjectNode ruleNode = objectMapper.createObjectNode();
                ruleNode.put("search", rule.search() == null ? "" : rule.search());
                ruleNode.put("replacement", rule.replacement() == null ? "" : rule.replacement());
                ruleNode.put("regex", Boolean.TRUE.equals(rule.regex()));
                replacementRules.add(ruleNode);
            });
        }
        node.set("replacementRules", replacementRules);
        return node;
    }

    private JsonNode validationRulesetPayload(ValidationRuleset ruleset) {
        return validationRulesetPayloadFromRequest(new ValidationRulesetDto.CreateOrUpdateRequest(
                ruleset.getName(),
                ruleset.getDescription(),
                ruleset.getTags(),
                validationRulesetService.readRules(ruleset)
        ), ruleset.getName());
    }

    private JsonNode validationRulesetPayloadFromRequest(ValidationRulesetDto.CreateOrUpdateRequest request, String nameOverride) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", normalizeName(nameOverride, request.name(), "Validation Ruleset"));
        node.put("description", request.description() == null ? "" : request.description());
        ArrayNode tags = objectMapper.createArrayNode();
        if (request.tags() != null) {
            request.tags().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(v -> !v.isBlank())
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(tags::add);
        }
        node.set("tags", tags);

        ArrayNode rules = objectMapper.createArrayNode();
        if (request.rules() != null) {
            request.rules().stream()
                    .sorted(Comparator.comparing(ValidationRulesetDto.Rule::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                    .forEach(rule -> {
                        ObjectNode ruleNode = objectMapper.createObjectNode();
                        putNullable(ruleNode, "id", rule.id());
                        ruleNode.put("name", rule.name());
                        putNullable(ruleNode, "description", rule.description());
                        ruleNode.put("severity", rule.severity() == null ? ValidationRulesetDto.Severity.WARNING.name() : rule.severity().name());
                        ruleNode.put("pattern", rule.pattern());
                        putNullable(ruleNode, "flags", rule.flags());
                        putNullable(ruleNode, "message", rule.message());
                        rules.add(ruleNode);
                    });
        }
        node.set("rules", rules);
        return node;
    }

    private JsonNode virtualKeyboardPayload(VirtualKeyboard keyboard) {
        VirtualKeyboardDto dto = new VirtualKeyboardDto(keyboard);
        return virtualKeyboardPayloadFromDto(dto, keyboard.getName());
    }

    private JsonNode virtualKeyboardPayloadFromDto(VirtualKeyboardDto dto, String nameOverride) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", normalizeName(nameOverride, dto.getName(), "Keyboard"));
        node.put("description", dto.getDescription() == null ? "" : dto.getDescription());
        node.put("cols", dto.getCols());
        node.put("rows", dto.getRows());

        ArrayNode tags = objectMapper.createArrayNode();
        if (dto.getTags() != null) {
            dto.getTags().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(v -> !v.isBlank())
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(tags::add);
        }
        node.set("tags", tags);

        ArrayNode items = objectMapper.createArrayNode();
        if (dto.getItems() != null) {
            dto.getItems().stream()
                    .sorted(Comparator.comparingInt(KeyboardItemDto::getY)
                            .thenComparingInt(KeyboardItemDto::getX)
                            .thenComparingInt(KeyboardItemDto::getW))
                    .forEach(item -> {
                        ObjectNode itemNode = objectMapper.createObjectNode();
                        itemNode.put("x", item.getX());
                        itemNode.put("y", item.getY());
                        itemNode.put("w", item.getW());
                        itemNode.put("char", item.getChar());
                        if (item.getShiftChar() != null) {
                            itemNode.put("shiftChar", item.getShiftChar());
                        }
                        if (item.getColorClass() != null) {
                            itemNode.put("colorClass", item.getColorClass());
                        }
                        if (item.getTextClass() != null) {
                            itemNode.put("textClass", item.getTextClass());
                        }
                        if (item.getDescription() != null) {
                            itemNode.put("description", item.getDescription());
                        }
                        if (item.getShiftDescription() != null) {
                            itemNode.put("shiftDescription", item.getShiftDescription());
                        }
                        items.add(itemNode);
                    });
        }
        node.set("items", items);
        return node;
    }

    private static void putNullable(ObjectNode node, String key, String value) {
        if (value == null) {
            node.putNull(key);
        } else {
            node.put(key, value);
        }
    }

    private ObjectNode sanitizeCodecPayload(JsonNode payload) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", payload.path("name").asText(""));
        node.put("description", payload.path("description").asText(""));

        ArrayNode tags = objectMapper.createArrayNode();
        if (payload.path("tags").isArray()) {
            payload.path("tags").forEach(tag -> tags.add(tag.asText()));
        }
        node.set("tags", tags);

        ArrayNode chars = objectMapper.createArrayNode();
        if (payload.path("codec").isArray()) {
            payload.path("codec").forEach(ch -> chars.add(ch.asText()));
        }
        node.set("codec", chars);
        return node;
    }

    private ObjectNode sanitizeDictionaryPayload(JsonNode payload) {
        ObjectNode root = ensureObject(payload);
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", root.path("name").asText(""));
        node.put("description", root.path("description").asText(""));
        node.put("caseSensitive", root.path("caseSensitive").asBoolean(false));
        node.put("unicodeNormalization", root.path("unicodeNormalization").asText("NFC"));
        node.put("locked", root.path("locked").asBoolean(false));
        node.set("tags", root.path("tags").isArray() ? root.path("tags").deepCopy() : objectMapper.createArrayNode());
        node.set("entries", root.path("entries").isArray() ? root.path("entries").deepCopy() : objectMapper.createArrayNode());
        return node;
    }

    private ObjectNode sanitizeLabelPayload(JsonNode payload) {
        JsonNode sanitized = JsonNodeUtils.removeFieldRecursively(payload, "icon").node();
        ObjectNode root = ensureObject(sanitized);

        ObjectNode node = objectMapper.createObjectNode();
        node.set("meta", ensureObject(root.path("meta")).deepCopy());

        if (root.path("labels").isArray()) {
            node.set("labels", root.path("labels").deepCopy());
        } else {
            node.set("labels", objectMapper.createArrayNode());
        }
        return node;
    }

    private ObjectNode sanitizeTagPayload(JsonNode payload) {
        JsonNode sanitized = JsonNodeUtils.removeFieldRecursively(payload, "icon").node();
        ObjectNode root = ensureObject(sanitized);

        ObjectNode node = objectMapper.createObjectNode();
        node.set("meta", ensureObject(root.path("meta")).deepCopy());

        if (root.path("tags").isArray()) {
            node.set("tags", root.path("tags").deepCopy());
        } else {
            node.set("tags", objectMapper.createArrayNode());
        }
        return node;
    }

    private ObjectNode sanitizeNormalizationProfilePayload(JsonNode payload) {
        ObjectNode root = ensureObject(payload);
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", root.path("name").asText(""));
        node.put("description", root.path("description").asText(""));
        node.put("unicodeNormalization", root.path("unicodeNormalization").asText("NFC"));
        node.put("collapseWhitespace", root.path("collapseWhitespace").asBoolean(true));
        node.put("trimText", root.path("trimText").asBoolean(true));
        node.put("dehyphenateLineBreaks", root.path("dehyphenateLineBreaks").asBoolean(false));
        node.put("mapLongSToS", root.path("mapLongSToS").asBoolean(false));
        node.put("expandCommonLigatures", root.path("expandCommonLigatures").asBoolean(false));
        node.put("normalizeQuotes", root.path("normalizeQuotes").asBoolean(false));
        node.put("normalizeDashes", root.path("normalizeDashes").asBoolean(false));
        node.put("normalizeEllipsis", root.path("normalizeEllipsis").asBoolean(false));
        node.set("tags", root.path("tags").isArray() ? root.path("tags").deepCopy() : objectMapper.createArrayNode());
        node.set("replacementRules", root.path("replacementRules").isArray() ? root.path("replacementRules").deepCopy() : objectMapper.createArrayNode());
        return node;
    }

    private ObjectNode sanitizeValidationRulesetPayload(JsonNode payload) {
        ObjectNode root = ensureObject(payload);
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", root.path("name").asText(""));
        node.put("description", root.path("description").asText(""));
        node.set("tags", root.path("tags").isArray() ? root.path("tags").deepCopy() : objectMapper.createArrayNode());
        node.set("rules", root.path("rules").isArray() ? root.path("rules").deepCopy() : objectMapper.createArrayNode());
        return node;
    }

    private ObjectNode sanitizeVirtualKeyboardPayload(JsonNode payload) {
        ObjectNode root = ensureObject(payload);
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", root.path("name").asText(""));
        node.put("description", root.path("description").asText(""));
        node.put("cols", root.path("cols").asInt(0));
        node.put("rows", root.path("rows").asInt(0));
        node.set("tags", root.path("tags").isArray() ? root.path("tags").deepCopy() : objectMapper.createArrayNode());
        node.set("items", root.path("items").isArray() ? root.path("items").deepCopy() : objectMapper.createArrayNode());
        return node;
    }

    private Map<UtilityPackageDto.UtilityType, Set<String>> parseSelectors(List<UtilityPackageDto.ResourceSelector> selectors) {
        Map<UtilityPackageDto.UtilityType, Set<String>> byType = new EnumMap<>(UtilityPackageDto.UtilityType.class);
        if (selectors == null) {
            return byType;
        }

        for (UtilityPackageDto.ResourceSelector selector : selectors) {
            if (selector == null || selector.type() == null) {
                continue;
            }
            Set<String> ids = byType.computeIfAbsent(selector.type(), key -> new TreeSet<>());
            if (selector.ids() != null) {
                selector.ids().stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(v -> !v.isBlank())
                        .forEach(ids::add);
            }
        }
        return byType;
    }

    private boolean payloadEquals(JsonNode left, JsonNode right) {
        JsonNode leftSorted = sortNode(left);
        JsonNode rightSorted = sortNode(right);
        return leftSorted.equals(rightSorted);
    }

    private JsonNode sortNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return objectMapper.nullNode();
        }
        if (node.isObject()) {
            ObjectNode source = (ObjectNode) node;
            ObjectNode sorted = objectMapper.createObjectNode();
            List<String> fieldNames = new ArrayList<>();
            source.fieldNames().forEachRemaining(fieldNames::add);
            fieldNames.stream().sorted(String::compareTo).forEach(fieldName -> sorted.set(fieldName, sortNode(source.get(fieldName))));
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode sorted = objectMapper.createArrayNode();
            node.forEach(child -> sorted.add(sortNode(child)));
            return sorted;
        }
        return node;
    }

    private String uniqueName(String baseName, Predicate<String> exists) {
        String normalizedBase = normalizeName(baseName, null, "Imported");
        String candidate = normalizedBase + " (imported)";
        if (!exists.test(candidate)) {
            return candidate;
        }

        int index = 2;
        while (index < 10_000) {
            String next = normalizedBase + " (imported " + index + ")";
            if (!exists.test(next)) {
                return next;
            }
            index++;
        }

        return normalizedBase + " (imported " + LocalDateTime.now().toString().replace(':', '-') + ")";
    }

    private String normalizeName(String preferred, String fallback, String defaultName) {
        String resolved = preferred;
        if (resolved == null || resolved.isBlank()) {
            resolved = fallback;
        }
        if (resolved == null || resolved.isBlank()) {
            resolved = defaultName;
        }
        return resolved.trim();
    }

    private ObjectNode ensureObject(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            return objectNode;
        }
        return objectMapper.createObjectNode();
    }

    private ObjectNode ensureObject(ObjectNode node, String field) {
        JsonNode child = node.get(field);
        if (child instanceof ObjectNode objectNode) {
            return objectNode;
        }
        ObjectNode created = objectMapper.createObjectNode();
        node.set(field, created);
        return created;
    }
}

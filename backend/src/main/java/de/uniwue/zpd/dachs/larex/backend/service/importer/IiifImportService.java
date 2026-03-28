package de.uniwue.zpd.dachs.larex.backend.service.importer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.uniwue.zpd.dachs.larex.backend.dto.IiifImportDto;
import de.uniwue.zpd.dachs.larex.backend.entity.IiifImportJob;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.exception.ResourceNotFoundException;
import de.uniwue.zpd.dachs.larex.backend.repository.importing.IiifImportJobRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageImageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaGuardService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class IiifImportService {

    static final String IIIF_IMAGE_VARIANT = "iiif";
    static final String EXTERNAL_SOURCE_TYPE = "IIIF_CANVAS";
    private static final long UNKNOWN_IMAGE_SIZE_ESTIMATE_BYTES = 10L * 1024L * 1024L;
    private static final Duration PREVIEW_CACHE_TTL = Duration.ofMinutes(15);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<IiifJobCanvasPayload>> JOB_PAYLOAD_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<IiifImportDto.ItemResult>> ITEM_RESULT_LIST_TYPE = new TypeReference<>() {};

    private final ProjectRepository projectRepository;
    private final PageRepository pageRepository;
    private final PageImageRepository pageImageRepository;
    private final IiifImportJobRepository iiifImportJobRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final WorkspaceQuotaGuardService workspaceQuotaGuardService;
    private final AsyncIiifImportProcessor asyncIiifImportProcessor;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Cache<String, IiifPreviewSession> previewCache;

    public IiifImportService(ProjectRepository projectRepository,
                             PageRepository pageRepository,
                             PageImageRepository pageImageRepository,
                             IiifImportJobRepository iiifImportJobRepository,
                             WorkspaceAccessService workspaceAccessService,
                             WorkspaceQuotaGuardService workspaceQuotaGuardService,
                             AsyncIiifImportProcessor asyncIiifImportProcessor,
                             ObjectMapper objectMapper) {
        this.projectRepository = projectRepository;
        this.pageRepository = pageRepository;
        this.pageImageRepository = pageImageRepository;
        this.iiifImportJobRepository = iiifImportJobRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.workspaceQuotaGuardService = workspaceQuotaGuardService;
        this.asyncIiifImportProcessor = asyncIiifImportProcessor;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(HTTP_TIMEOUT)
                .build();
        this.previewCache = Caffeine.newBuilder()
                .expireAfterWrite(PREVIEW_CACHE_TTL)
                .maximumSize(256)
                .build();
    }

    public IiifImportDto.PreviewResponse previewFromManifestUrl(String workspaceId,
                                                                String projectId,
                                                                String userId,
                                                                IiifImportDto.PreviewRequest request) throws IOException {
        Project project = requireProjectManageAccess(workspaceId, projectId, userId);
        String manifestUrl = normalizeHttpUrl(request.manifestUrl());
        byte[] manifestBytes = fetchBytes(manifestUrl, "application/json, application/ld+json;q=0.9, */*;q=0.8");
        return buildPreview(project, userId, IiifImportJob.SourceType.MANIFEST_URL, manifestUrl, manifestUrl, manifestBytes);
    }

    public IiifImportDto.PreviewResponse previewFromManifestFile(String workspaceId,
                                                                 String projectId,
                                                                 String userId,
                                                                 MultipartFile file) throws IOException {
        Project project = requireProjectManageAccess(workspaceId, projectId, userId);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Manifest file is required");
        }
        String sourceName = file.getOriginalFilename() == null ? "manifest.json" : file.getOriginalFilename();
        return buildPreview(project, userId, IiifImportJob.SourceType.MANIFEST_FILE, sourceName, null, file.getBytes());
    }

    public IiifImportDto.JobResponse startImportJob(String workspaceId,
                                                    String projectId,
                                                    String userId,
                                                    IiifImportDto.StartJobRequest request) {
        Project project = requireProjectManageAccess(workspaceId, projectId, userId);
        IiifPreviewSession previewSession = Optional.ofNullable(previewCache.getIfPresent(request.previewToken()))
                .orElseThrow(() -> new IllegalArgumentException("IIIF preview has expired. Preview the manifest again."));

        if (!Objects.equals(previewSession.workspaceId(), workspaceId)
                || !Objects.equals(previewSession.projectId(), projectId)
                || !Objects.equals(previewSession.userId(), userId)) {
            throw new IllegalArgumentException("IIIF preview does not match this project or user.");
        }

        List<IiifImportJob.Status> activeStatuses = List.of(IiifImportJob.Status.PENDING, IiifImportJob.Status.IMPORTING);
        if (!iiifImportJobRepository.findActiveJobsForProject(projectId, activeStatuses).isEmpty()) {
            throw new IllegalArgumentException("An IIIF import is already running for this project.");
        }

        List<IiifJobCanvasPayload> payloads = materializeJobPayloads(project, previewSession, request.resolutions());
        long estimatedBytes = payloads.stream()
                .filter(payload -> !"KEEP_EXISTING".equals(payload.action()))
                .mapToLong(payload -> payload.estimatedBytes() == null ? UNKNOWN_IMAGE_SIZE_ESTIMATE_BYTES : payload.estimatedBytes())
                .sum();
        long reservedBytes = workspaceQuotaGuardService.reserveBytesOrThrow(workspaceId, estimatedBytes, "iiif-import-job");

        try {
            IiifImportJob job = new IiifImportJob();
            job.setProjectId(projectId);
            job.setWorkspaceId(workspaceId);
            job.setCreatedByUserId(userId);
            job.setSourceType(previewSession.sourceType());
            job.setSourceReference(previewSession.sourceReference());
            job.setStatus(IiifImportJob.Status.PENDING);
            job.setTotalCanvases(payloads.size());
            job.setEstimatedStorageBytes(estimatedBytes);
            job.setReservedBytes(reservedBytes);
            job.setManifestSummaryJson(writeJson(previewSession.manifest()));
            job.setWarningsJson(writeJson(previewSession.warnings()));
            job.setCanvasPayloadJson(writeJson(payloads));
            job.setResultsJson("[]");
            job.appendToLog("IIIF import job created");
            job = iiifImportJobRepository.save(job);

            triggerImportAfterCommit(job.getId());
            return toJobResponse(job);
        } catch (RuntimeException e) {
            workspaceQuotaGuardService.releaseReservation(workspaceId, reservedBytes);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public IiifImportDto.JobResponse getImportJob(String workspaceId, String projectId, String userId, String jobId) {
        requireProjectManageAccess(workspaceId, projectId, userId);
        IiifImportJob job = iiifImportJobRepository.findByIdAndWorkspaceIdAndProjectId(jobId, workspaceId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("IIIF import job", jobId));
        return toJobResponse(job);
    }

    public IiifImportDto.JobResponse cancelImportJob(String workspaceId, String projectId, String userId, String jobId) {
        requireProjectManageAccess(workspaceId, projectId, userId);
        IiifImportJob job = iiifImportJobRepository.findByIdAndWorkspaceIdAndProjectId(jobId, workspaceId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("IIIF import job", jobId));
        if (job.getStatus() == IiifImportJob.Status.COMPLETED
                || job.getStatus() == IiifImportJob.Status.FAILED
                || job.getStatus() == IiifImportJob.Status.CANCELLED) {
            return toJobResponse(job);
        }
        job.setStatus(IiifImportJob.Status.CANCELLED);
        job.appendToLog("Cancellation requested");
        return toJobResponse(iiifImportJobRepository.save(job));
    }

    @Transactional(readOnly = true)
    public IiifImportJob requireJob(String jobId) {
        return iiifImportJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("IIIF import job", jobId));
    }

    @Transactional(readOnly = true)
    public List<IiifJobCanvasPayload> readJobPayloads(IiifImportJob job) {
        return readJson(job.getCanvasPayloadJson(), JOB_PAYLOAD_LIST_TYPE, List.of());
    }

    public void saveJobResults(IiifImportJob job, List<IiifImportDto.ItemResult> results) {
        job.setResultsJson(writeJson(results));
        iiifImportJobRepository.save(job);
    }

    public void saveJob(IiifImportJob job) {
        iiifImportJobRepository.save(job);
    }

    public List<IiifImportDto.ItemResult> readJobResults(IiifImportJob job) {
        return readJson(job.getResultsJson(), ITEM_RESULT_LIST_TYPE, List.of());
    }

    private IiifImportDto.PreviewResponse buildPreview(Project project,
                                                       String userId,
                                                       IiifImportJob.SourceType sourceType,
                                                       String sourceReference,
                                                       String sourceUrl,
                                                       byte[] manifestBytes) throws IOException {
        JsonNode root = objectMapper.readTree(manifestBytes);
        ParsedManifest parsedManifest = parseManifest(root, sourceType, sourceReference, sourceUrl);
        List<IiifPreviewCanvas> canvases = enrichCanvases(project, parsedManifest);
        List<String> warnings = new ArrayList<>(parsedManifest.warnings());
        int unknownSizeCanvasCount = (int) canvases.stream().filter(c -> c.importable() && c.estimatedBytes() == null).count();
        long estimatedStorageBytes = canvases.stream()
                .filter(IiifPreviewCanvas::importable)
                .mapToLong(canvas -> canvas.estimatedBytes() == null ? UNKNOWN_IMAGE_SIZE_ESTIMATE_BYTES : canvas.estimatedBytes())
                .sum();
        if (unknownSizeCanvasCount > 0) {
            warnings.add(unknownSizeCanvasCount + " canvas image sizes could not be determined; using a default estimate of "
                    + formatBytes(UNKNOWN_IMAGE_SIZE_ESTIMATE_BYTES) + " each for quota preflight.");
        }

        IiifPreviewSession session = new IiifPreviewSession(
                project.getLibrary().getWorkspaceId(),
                project.getId(),
                userId,
                sourceType,
                sourceReference,
                parsedManifest.manifest(),
                estimatedStorageBytes,
                unknownSizeCanvasCount,
                List.copyOf(warnings),
                List.copyOf(canvases)
        );

        String previewToken = UUID.randomUUID().toString();
        previewCache.put(previewToken, session);

        List<IiifImportDto.CanvasPreview> previewCanvases = canvases.stream()
                .map(canvas -> new IiifImportDto.CanvasPreview(
                        canvas.canvasId(),
                        canvas.canvasLabel(),
                        canvas.index(),
                        canvas.derivedPageName(),
                        canvas.importable(),
                        canvas.imageUrl(),
                        canvas.estimatedBytes(),
                        canvas.warnings(),
                        canvas.existingPageId() == null ? null : new IiifImportDto.Conflict(
                                canvas.canvasId(),
                                canvas.existingIiifImage() ? "IMAGE_VARIANT_EXISTS" : "PAGE_NAME_EXISTS",
                                canvas.derivedPageName(),
                                canvas.existingPageId(),
                                canvas.existingPageName(),
                                canvas.existingIiifImage(),
                                canvas.existingIiifImage()
                                        ? "Page name already exists and already has an imported IIIF image."
                                        : "Page name already exists."
                        )
                ))
                .toList();

        return new IiifImportDto.PreviewResponse(
                previewToken,
                parsedManifest.manifest(),
                previewCanvases.size(),
                (int) previewCanvases.stream().filter(IiifImportDto.CanvasPreview::importable).count(),
                estimatedStorageBytes,
                unknownSizeCanvasCount,
                List.copyOf(warnings),
                previewCanvases
        );
    }

    private List<IiifJobCanvasPayload> materializeJobPayloads(Project project,
                                                              IiifPreviewSession session,
                                                              List<IiifImportDto.Resolution> resolutions) {
        Map<String, IiifImportDto.Resolution> resolutionByCanvasId = (resolutions == null ? List.<IiifImportDto.Resolution>of() : resolutions).stream()
                .collect(Collectors.toMap(IiifImportDto.Resolution::canvasId, resolution -> resolution, (left, _right) -> left, LinkedHashMap::new));

        Set<String> existingNames = pageRepository.findPageNamesByProjectId(project.getId()).stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> createdNames = new LinkedHashSet<>();
        List<IiifJobCanvasPayload> payloads = new ArrayList<>();

        for (IiifPreviewCanvas canvas : session.canvases()) {
            if (!canvas.importable()) {
                continue;
            }

            if (canvas.existingPageId() != null) {
                IiifImportDto.Resolution resolution = resolutionByCanvasId.get(canvas.canvasId());
                if (resolution == null) {
                    throw new IllegalArgumentException("Missing resolution for conflicting canvas: " + canvas.canvasLabel());
                }
                String action = normalizeResolutionAction(resolution.action());
                switch (action) {
                    case "KEEP_EXISTING" -> payloads.add(new IiifJobCanvasPayload(
                            canvas.canvasId(),
                            canvas.canvasLabel(),
                            canvas.index(),
                            canvas.derivedPageName(),
                            canvas.existingPageName(),
                            null,
                            action,
                            canvas.imageUrl(),
                            canvas.estimatedBytes(),
                            canvas.existingPageId(),
                            canvas.canvasId(),
                            buildExternalSourceMetadata(session.manifest(), canvas)
                    ));
                    case "REPLACE" -> payloads.add(new IiifJobCanvasPayload(
                            canvas.canvasId(),
                            canvas.canvasLabel(),
                            canvas.index(),
                            canvas.derivedPageName(),
                            canvas.existingPageName(),
                            null,
                            action,
                            canvas.imageUrl(),
                            canvas.estimatedBytes(),
                            canvas.existingPageId(),
                            canvas.canvasId(),
                            buildExternalSourceMetadata(session.manifest(), canvas)
                    ));
                    case "RENAME" -> {
                        String renamedPageName = normalizePageNameInput(resolution.pageName());
                        String lowerRenamed = renamedPageName.toLowerCase(Locale.ROOT);
                        if (existingNames.contains(lowerRenamed) || createdNames.contains(lowerRenamed)) {
                            throw new IllegalArgumentException("Renamed page already exists: " + renamedPageName);
                        }
                        createdNames.add(lowerRenamed);
                        payloads.add(new IiifJobCanvasPayload(
                                canvas.canvasId(),
                                canvas.canvasLabel(),
                                canvas.index(),
                                canvas.derivedPageName(),
                                renamedPageName,
                                buildPageDescription(session.manifest(), canvas),
                                action,
                                canvas.imageUrl(),
                                canvas.estimatedBytes(),
                                null,
                                canvas.canvasId(),
                                buildExternalSourceMetadata(session.manifest(), canvas)
                        ));
                    }
                    default -> throw new IllegalArgumentException("Unsupported IIIF resolution action: " + resolution.action());
                }
                continue;
            }

            String lowerDerived = canvas.derivedPageName().toLowerCase(Locale.ROOT);
            if (existingNames.contains(lowerDerived) || createdNames.contains(lowerDerived)) {
                throw new IllegalArgumentException("Project pages changed since preview. Preview the manifest again.");
            }
            createdNames.add(lowerDerived);
            payloads.add(new IiifJobCanvasPayload(
                    canvas.canvasId(),
                    canvas.canvasLabel(),
                    canvas.index(),
                    canvas.derivedPageName(),
                    canvas.derivedPageName(),
                    buildPageDescription(session.manifest(), canvas),
                    "IMPORT",
                    canvas.imageUrl(),
                    canvas.estimatedBytes(),
                    null,
                    canvas.canvasId(),
                    buildExternalSourceMetadata(session.manifest(), canvas)
            ));
        }

        return payloads;
    }

    private List<IiifPreviewCanvas> enrichCanvases(Project project, ParsedManifest parsedManifest) {
        Set<String> lowerPageNames = parsedManifest.canvases().stream()
                .map(ParsedCanvas::derivedPageName)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, Page> existingPagesByLowerName = pageRepository.findByProjectIdAndLowerNameIn(project.getId(), lowerPageNames).stream()
                .collect(Collectors.toMap(page -> page.getName().toLowerCase(Locale.ROOT), page -> page));
        Map<String, Boolean> existingIiifImagesByPageId = resolveExistingIiifImageFlags(
                existingPagesByLowerName.values().stream().map(Page::getId).toList()
        );

        List<IiifPreviewCanvas> enriched = new ArrayList<>();
        for (ParsedCanvas canvas : parsedManifest.canvases()) {
            Page existingPage = existingPagesByLowerName.get(canvas.derivedPageName().toLowerCase(Locale.ROOT));
            boolean existingIiifImage = existingPage != null && existingIiifImagesByPageId.getOrDefault(existingPage.getId(), false);
            Long estimatedBytes = canvas.importable() ? estimateRemoteSize(canvas.imageUrl()) : null;
            enriched.add(new IiifPreviewCanvas(
                    canvas.canvasId(),
                    canvas.canvasLabel(),
                    canvas.index(),
                    canvas.derivedPageName(),
                    canvas.importable(),
                    canvas.imageUrl(),
                    estimatedBytes,
                    canvas.warnings(),
                    existingPage == null ? null : existingPage.getId(),
                    existingPage == null ? null : existingPage.getName(),
                    existingIiifImage
            ));
        }
        return enriched;
    }

    private Map<String, Boolean> resolveExistingIiifImageFlags(Collection<String> pageIds) {
        if (pageIds == null || pageIds.isEmpty()) {
            return Map.of();
        }
        Map<String, Boolean> flags = new HashMap<>();
        for (Object[] row : pageImageRepository.findByPageIds(pageIds)) {
            if (row.length < 2) continue;
            String pageId = String.valueOf(row[0]);
            PageImage image = (PageImage) row[1];
            if (IIIF_IMAGE_VARIANT.equalsIgnoreCase(image.getVariant())) {
                flags.put(pageId, true);
            }
        }
        return flags;
    }

    private ParsedManifest parseManifest(JsonNode root,
                                         IiifImportJob.SourceType sourceType,
                                         String sourceReference,
                                         String sourceUrl) {
        String presentationVersion = detectPresentationVersion(root);
        if (presentationVersion == null) {
            throw new IllegalArgumentException("Unsupported IIIF manifest. Only Presentation API v2 and v3 manifests are supported.");
        }

        String manifestId = extractNodeId(root);
        String manifestLabel = localizedText(root.path("label"));
        if (manifestLabel == null) {
            manifestLabel = sourceType == IiifImportJob.SourceType.MANIFEST_FILE ? sourceReference : manifestId;
        }
        String provider = extractProvider(root);
        String thumbnailUrl = extractThumbnailUrl(root, presentationVersion);
        IiifImportDto.ManifestSummary manifest = new IiifImportDto.ManifestSummary(
                manifestId,
                sourceUrl,
                sourceType.name(),
                sourceReference,
                manifestLabel,
                provider,
                thumbnailUrl,
                presentationVersion
        );

        List<String> warnings = new ArrayList<>();
        List<ParsedCanvas> canvases = "3".equals(presentationVersion)
                ? parseV3Canvases(root, manifestLabel, warnings)
                : parseV2Canvases(root, manifestLabel, warnings);

        return new ParsedManifest(manifest, warnings, canvases);
    }

    private List<ParsedCanvas> parseV3Canvases(JsonNode root, String manifestLabel, List<String> warnings) {
        JsonNode items = root.path("items");
        if (!items.isArray()) {
            throw new IllegalArgumentException("IIIF v3 manifest does not contain a canvas list.");
        }

        List<ParsedCanvas> canvases = new ArrayList<>();
        Map<String, Integer> nameCounts = new LinkedHashMap<>();
        int index = 1;
        for (JsonNode canvasNode : items) {
            String canvasId = extractNodeId(canvasNode);
            String canvasLabel = localizedText(canvasNode.path("label"));
            List<String> canvasWarnings = new ArrayList<>();
            String imageUrl = extractV3ImageUrl(canvasNode, canvasWarnings);
            String derivedPageName = uniquePageName(buildPageNameCandidate(canvasLabel, manifestLabel, index), nameCounts, index);
            boolean importable = imageUrl != null;
            if (!importable) {
                canvasWarnings.add("No downloadable canvas image was found.");
                warnings.add("Canvas " + index + " has no downloadable image and will be skipped.");
            }
            canvases.add(new ParsedCanvas(
                    canvasId,
                    canvasLabel == null ? "Canvas " + index : canvasLabel,
                    index,
                    derivedPageName,
                    importable,
                    imageUrl,
                    List.copyOf(canvasWarnings)
            ));
            index++;
        }
        return canvases;
    }

    private List<ParsedCanvas> parseV2Canvases(JsonNode root, String manifestLabel, List<String> warnings) {
        JsonNode sequences = root.path("sequences");
        if (!sequences.isArray() || sequences.isEmpty()) {
            throw new IllegalArgumentException("IIIF v2 manifest does not contain a sequence list.");
        }

        List<ParsedCanvas> canvases = new ArrayList<>();
        Map<String, Integer> nameCounts = new LinkedHashMap<>();
        int index = 1;
        for (JsonNode sequence : sequences) {
            JsonNode sequenceCanvases = sequence.path("canvases");
            if (!sequenceCanvases.isArray()) continue;
            for (JsonNode canvasNode : sequenceCanvases) {
                String canvasId = extractNodeId(canvasNode);
                String canvasLabel = localizedText(canvasNode.path("label"));
                List<String> canvasWarnings = new ArrayList<>();
                String imageUrl = extractV2ImageUrl(canvasNode, canvasWarnings);
                String derivedPageName = uniquePageName(buildPageNameCandidate(canvasLabel, manifestLabel, index), nameCounts, index);
                boolean importable = imageUrl != null;
                if (!importable) {
                    canvasWarnings.add("No downloadable canvas image was found.");
                    warnings.add("Canvas " + index + " has no downloadable image and will be skipped.");
                }
                canvases.add(new ParsedCanvas(
                        canvasId,
                        canvasLabel == null ? "Canvas " + index : canvasLabel,
                        index,
                        derivedPageName,
                        importable,
                        imageUrl,
                        List.copyOf(canvasWarnings)
                ));
                index++;
            }
        }
        return canvases;
    }

    private String extractV3ImageUrl(JsonNode canvasNode, List<String> warnings) {
        for (JsonNode annotationPage : iterable(canvasNode.path("items"))) {
            for (JsonNode annotation : iterable(annotationPage.path("items"))) {
                JsonNode body = annotation.path("body");
                if (body.isArray()) {
                    for (JsonNode bodyCandidate : body) {
                        String candidate = chooseV3BodyImageUrl(bodyCandidate);
                        if (candidate != null) return candidate;
                    }
                    continue;
                }
                String candidate = chooseV3BodyImageUrl(body);
                if (candidate != null) return candidate;
            }
        }
        warnings.add("Canvas has no image annotation body.");
        return null;
    }

    private String chooseV3BodyImageUrl(JsonNode body) {
        if (body == null || body.isMissingNode() || body.isNull()) {
            return null;
        }
        if ("Image".equalsIgnoreCase(body.path("type").asText()) && hasText(extractNodeId(body))) {
            return extractNodeId(body);
        }
        String serviceUrl = extractImageServiceUrl(body);
        if (serviceUrl != null) {
            return buildImageServiceUrl(serviceUrl, "3");
        }
        String bodyId = extractNodeId(body);
        return hasText(bodyId) ? bodyId : null;
    }

    private String extractV2ImageUrl(JsonNode canvasNode, List<String> warnings) {
        for (JsonNode imageNode : iterable(canvasNode.path("images"))) {
            JsonNode resource = imageNode.path("resource");
            String direct = extractNodeId(resource);
            if (hasText(direct)) {
                return direct;
            }
            String serviceUrl = extractImageServiceUrl(resource);
            if (serviceUrl != null) {
                return buildImageServiceUrl(serviceUrl, "2");
            }
        }
        warnings.add("Canvas has no IIIF image resource.");
        return null;
    }

    private String detectPresentationVersion(JsonNode root) {
        if (root == null || root.isMissingNode()) return null;
        String context = root.path("@context").asText("");
        if (context.contains("/presentation/3")) return "3";
        if (context.contains("/presentation/2")) return "2";
        if (root.has("items")) return "3";
        if (root.has("sequences")) return "2";
        return null;
    }

    private String extractProvider(JsonNode root) {
        String provider = joinTexts(root.path("provider"));
        if (provider != null) return provider;
        return localizedText(root.path("attribution"));
    }

    private String extractThumbnailUrl(JsonNode root, String presentationVersion) {
        JsonNode thumbnail = root.path("thumbnail");
        if (thumbnail.isMissingNode() || thumbnail.isNull()) return null;
        if (thumbnail.isArray()) {
            for (JsonNode item : thumbnail) {
                String candidate = extractThumbnailCandidate(item, presentationVersion);
                if (candidate != null) return candidate;
            }
            return null;
        }
        return extractThumbnailCandidate(thumbnail, presentationVersion);
    }

    private String extractThumbnailCandidate(JsonNode node, String presentationVersion) {
        String direct = extractNodeId(node);
        if (hasText(direct)) return direct;
        String serviceUrl = extractImageServiceUrl(node);
        return serviceUrl == null ? null : buildImageServiceUrl(serviceUrl, presentationVersion);
    }

    private Long estimateRemoteSize(String imageUrl) {
        if (!hasText(imageUrl)) {
            return null;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(imageUrl))
                    .timeout(HTTP_TIMEOUT)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .header("User-Agent", "LAREX IIIF Import")
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return parseContentLength(response.headers());
        } catch (Exception ignored) {
            return null;
        }
    }

    private byte[] fetchBytes(String url, String acceptHeader) throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(url))
                    .timeout(HTTP_TIMEOUT)
                    .GET()
                    .header("Accept", acceptHeader)
                    .header("User-Agent", "LAREX IIIF Import")
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalArgumentException(buildManifestFetchStatusMessage(response.statusCode()));
            }
            return response.body();
        } catch (HttpTimeoutException e) {
            throw new IllegalArgumentException("Timed out while fetching the IIIF manifest URL.");
        } catch (ConnectException e) {
            throw new IllegalArgumentException("Could not reach the IIIF manifest URL. The remote server refused or dropped the connection.");
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Could not resolve the host name for the IIIF manifest URL.");
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not fetch the IIIF manifest URL because of a network error.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching IIIF manifest", e);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid IIIF manifest URL");
        }
    }

    private String buildManifestFetchStatusMessage(int statusCode) {
        return switch (statusCode) {
            case 401 -> "The IIIF manifest server requires authentication (HTTP 401). Public, unauthenticated manifests only are supported.";
            case 403 -> "The IIIF manifest server denied access (HTTP 403). Public, unauthenticated manifests only are supported.";
            case 404 -> "The IIIF manifest URL returned HTTP 404. Check that the manifest URL is correct.";
            default -> "Failed to fetch the IIIF manifest URL: HTTP " + statusCode;
        };
    }

    private Long parseContentLength(HttpHeaders headers) {
        Optional<String> header = headers.firstValue("content-length");
        if (header.isEmpty()) return null;
        try {
            long value = Long.parseLong(header.get());
            return value > 0 ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Project requireProjectManageAccess(String workspaceId, String projectId, String userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        if (!Objects.equals(project.getLibrary().getWorkspaceId(), workspaceId)) {
            throw new IllegalArgumentException("Project does not belong to workspace.");
        }
        workspaceAccessService.requireManageProjectsAccess(workspaceId, userId);
        return project;
    }

    private String normalizeHttpUrl(String rawUrl) {
        if (!hasText(rawUrl)) {
            throw new IllegalArgumentException("Manifest URL is required");
        }
        String trimmed = rawUrl.trim();
        try {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException("Only HTTP and HTTPS manifest URLs are supported.");
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Manifest URL is invalid.");
        }
        return trimmed;
    }

    private String normalizeResolutionAction(String rawAction) {
        if (!hasText(rawAction)) {
            throw new IllegalArgumentException("Resolution action is required.");
        }
        return rawAction.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizePageNameInput(String rawName) {
        String normalized = sanitizePageName(rawName);
        if (!hasText(normalized)) {
            throw new IllegalArgumentException("A renamed page must have a non-empty name.");
        }
        return normalized;
    }

    private String buildExternalSourceMetadata(IiifImportDto.ManifestSummary manifest, IiifPreviewCanvas canvas) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("manifestId", manifest.id());
        metadata.put("manifestUrl", manifest.sourceUrl());
        metadata.put("manifestLabel", manifest.label());
        metadata.put("provider", manifest.provider());
        metadata.put("thumbnailUrl", manifest.thumbnailUrl());
        metadata.put("presentationVersion", manifest.presentationVersion());
        metadata.put("canvasId", canvas.canvasId());
        metadata.put("canvasLabel", canvas.canvasLabel());
        metadata.put("canvasIndex", canvas.index());
        metadata.put("imageUrl", canvas.imageUrl());
        metadata.put("sourceType", manifest.sourceType());
        metadata.put("sourceName", manifest.sourceName());
        return writeJson(metadata);
    }

    private String buildPageDescription(IiifImportDto.ManifestSummary manifest, IiifPreviewCanvas canvas) {
        List<String> fragments = new ArrayList<>();
        if (hasText(manifest.label())) {
            fragments.add("Imported from IIIF manifest: " + manifest.label());
        }
        if (hasText(canvas.canvasLabel()) && !Objects.equals(canvas.canvasLabel(), canvas.derivedPageName())) {
            fragments.add("Canvas label: " + canvas.canvasLabel());
        }
        return fragments.isEmpty() ? "Imported from IIIF manifest" : String.join("\n", fragments);
    }

    private String buildImageServiceUrl(String serviceUrl, String presentationVersion) {
        String base = trimTrailingSlash(serviceUrl);
        if ("2".equals(presentationVersion)) {
            return base + "/full/full/0/default.jpg";
        }
        return base + "/full/max/0/default.jpg";
    }

    private String buildPageNameCandidate(String canvasLabel, String manifestLabel, int index) {
        String candidate = sanitizePageName(canvasLabel);
        if (hasText(candidate)) return candidate;
        String manifestCandidate = sanitizePageName(manifestLabel);
        if (hasText(manifestCandidate)) {
            return manifestCandidate + "-" + String.format(Locale.ROOT, "%04d", index);
        }
        return "canvas-" + String.format(Locale.ROOT, "%04d", index);
    }

    private String uniquePageName(String baseName, Map<String, Integer> counts, int index) {
        String normalized = hasText(baseName) ? baseName : "canvas-" + String.format(Locale.ROOT, "%04d", index);
        String key = normalized.toLowerCase(Locale.ROOT);
        int count = counts.getOrDefault(key, 0) + 1;
        counts.put(key, count);
        if (count == 1) {
            return normalized;
        }
        return normalized + "-" + count;
    }

    private String sanitizePageName(String input) {
        if (input == null) return null;
        String sanitized = input.trim()
                .replaceAll("[\\\\/:*?\"<>|]+", " ")
                .replaceAll("\\p{Cntrl}+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (sanitized.length() > 255) {
            sanitized = sanitized.substring(0, 255).trim();
        }
        return sanitized.isBlank() ? null : sanitized;
    }

    private String extractImageServiceUrl(JsonNode node) {
        JsonNode service = node.path("service");
        if (service.isMissingNode() || service.isNull()) return null;
        if (service.isArray()) {
            for (JsonNode item : service) {
                String id = extractNodeId(item);
                if (hasText(id)) return id;
            }
            return null;
        }
        return extractNodeId(service);
    }

    private String extractNodeId(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        if (node.hasNonNull("id")) return node.path("id").asText(null);
        if (node.hasNonNull("@id")) return node.path("@id").asText(null);
        return null;
    }

    private String localizedText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        if (node.isTextual()) return blankToNull(node.asText());
        if (node.isArray()) {
            for (JsonNode item : node) {
                String text = localizedText(item);
                if (text != null) return text;
            }
            return null;
        }
        if (node.isObject()) {
            if (node.hasNonNull("@value")) return blankToNull(node.path("@value").asText());
            if (node.hasNonNull("value")) return localizedText(node.path("value"));
            for (JsonNode value : iterable(node)) {
                String text = localizedText(value);
                if (text != null) return text;
            }
        }
        return null;
    }

    private String joinTexts(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        List<String> texts = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                String text = localizedText(item.path("label"));
                if (text == null) text = localizedText(item);
                if (text != null) texts.add(text);
            }
        } else {
            String text = localizedText(node);
            if (text != null) texts.add(text);
        }
        if (texts.isEmpty()) return null;
        return texts.stream().distinct().collect(Collectors.joining(", "));
    }

    private Iterable<JsonNode> iterable(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Collections.emptyList();
        }
        return node::elements;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String trimTrailingSlash(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize IIIF import state", e);
        }
    }

    private <T> T readJson(String json, TypeReference<T> typeReference, T fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            return fallback;
        }
    }

    private IiifImportDto.JobResponse toJobResponse(IiifImportJob job) {
        IiifImportDto.ManifestSummary manifest = null;
        if (job.getManifestSummaryJson() != null && !job.getManifestSummaryJson().isBlank()) {
            try {
                manifest = objectMapper.readValue(job.getManifestSummaryJson(), IiifImportDto.ManifestSummary.class);
            } catch (JsonProcessingException ignored) {
                // Keep null when stale or invalid.
            }
        }
        List<String> warnings = readJson(job.getWarningsJson(), STRING_LIST_TYPE, List.of());
        List<IiifImportDto.ItemResult> results = readJson(job.getResultsJson(), ITEM_RESULT_LIST_TYPE, List.of());

        return new IiifImportDto.JobResponse(
                job.getId(),
                job.getProjectId(),
                job.getWorkspaceId(),
                job.getSourceType() == null ? null : job.getSourceType().name(),
                job.getSourceReference(),
                job.getStatus().name(),
                job.getTotalCanvases(),
                job.getProcessedCanvases(),
                job.getSkippedCanvases(),
                job.getFailedCanvases(),
                job.getProgressPercent(),
                job.getEstimatedStorageBytes(),
                manifest,
                warnings,
                results,
                job.getErrorMessage(),
                job.getCreated(),
                job.getUpdated(),
                job.getCompletedAt()
        );
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unit = 0;
        double value = bytes;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }

    private void triggerImportAfterCommit(String jobId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    asyncIiifImportProcessor.processImportJob(jobId);
                }
            });
            return;
        }

        asyncIiifImportProcessor.processImportJob(jobId);
    }

    private record ParsedManifest(
            IiifImportDto.ManifestSummary manifest,
            List<String> warnings,
            List<ParsedCanvas> canvases
    ) {}

    private record ParsedCanvas(
            String canvasId,
            String canvasLabel,
            int index,
            String derivedPageName,
            boolean importable,
            String imageUrl,
            List<String> warnings
    ) {}
}

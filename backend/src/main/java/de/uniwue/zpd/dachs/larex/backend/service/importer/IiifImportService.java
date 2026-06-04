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
import de.uniwue.zpd.dachs.larex.backend.service.page.PageOrderService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaGuardService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
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
import java.util.Iterator;
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
    private static final int MAX_COLLECTION_MANIFESTS = 50;
    private static final int MAX_REMOTE_SIZE_PROBES = 50;
    private static final int MAX_HTTP_ATTEMPTS = 3;
    private static final Duration PREVIEW_CACHE_TTL = Duration.ofMinutes(15);
    private static final Duration PREVIEW_JOB_CACHE_TTL = Duration.ofMinutes(30);
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
    private final PageOrderService pageOrderService;
    private final AsyncIiifImportProcessor asyncIiifImportProcessor;
    private final IiifRemoteRequestThrottler iiifRemoteRequestThrottler;
    private final ObjectMapper objectMapper;
    private final TaskExecutor previewTaskExecutor;
    private final HttpClient httpClient;
    private final Cache<String, IiifPreviewSession> previewCache;
    private final Cache<String, IiifPreviewJobState> previewJobCache;

    public IiifImportService(ProjectRepository projectRepository,
                             PageRepository pageRepository,
                             PageImageRepository pageImageRepository,
                             IiifImportJobRepository iiifImportJobRepository,
                             WorkspaceAccessService workspaceAccessService,
                             WorkspaceQuotaGuardService workspaceQuotaGuardService,
                             PageOrderService pageOrderService,
                             AsyncIiifImportProcessor asyncIiifImportProcessor,
                             IiifRemoteRequestThrottler iiifRemoteRequestThrottler,
                             ObjectMapper objectMapper,
                             @Qualifier("importTaskExecutor") TaskExecutor previewTaskExecutor) {
        this.projectRepository = projectRepository;
        this.pageRepository = pageRepository;
        this.pageImageRepository = pageImageRepository;
        this.iiifImportJobRepository = iiifImportJobRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.workspaceQuotaGuardService = workspaceQuotaGuardService;
        this.pageOrderService = pageOrderService;
        this.asyncIiifImportProcessor = asyncIiifImportProcessor;
        this.iiifRemoteRequestThrottler = iiifRemoteRequestThrottler;
        this.objectMapper = objectMapper;
        this.previewTaskExecutor = previewTaskExecutor;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(HTTP_TIMEOUT)
                .build();
        this.previewCache = Caffeine.newBuilder()
                .expireAfterWrite(PREVIEW_CACHE_TTL)
                .maximumSize(256)
                .build();
        this.previewJobCache = Caffeine.newBuilder()
                .expireAfterWrite(PREVIEW_JOB_CACHE_TTL)
                .maximumSize(256)
                .build();
    }

    public IiifImportDto.PreviewJobResponse startPreviewJobFromManifestUrl(String workspaceId,
                                                                           String projectId,
                                                                           String userId,
                                                                           IiifImportDto.PreviewRequest request) {
        Project project = requireProjectManageAccess(workspaceId, projectId, userId);
        String manifestUrl = normalizeHttpUrl(request.manifestUrl());
        IiifPreviewJobState state = createPreviewJobState(project, userId);
        previewTaskExecutor.execute(() -> processPreviewJob(state.id(), project.getId(), userId,
                IiifImportJob.SourceType.MANIFEST_URL, manifestUrl, manifestUrl, null));
        return state.toResponse();
    }

    public IiifImportDto.PreviewJobResponse startPreviewJobFromManifestFile(String workspaceId,
                                                                            String projectId,
                                                                            String userId,
                                                                            MultipartFile file) throws IOException {
        Project project = requireProjectManageAccess(workspaceId, projectId, userId);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Manifest file is required");
        }
        String sourceName = file.getOriginalFilename() == null ? "manifest.json" : file.getOriginalFilename();
        byte[] manifestBytes = file.getBytes();
        IiifPreviewJobState state = createPreviewJobState(project, userId);
        previewTaskExecutor.execute(() -> processPreviewJob(state.id(), project.getId(), userId,
                IiifImportJob.SourceType.MANIFEST_FILE, sourceName, null, manifestBytes));
        return state.toResponse();
    }

    @Transactional(readOnly = true)
    public IiifImportDto.PreviewJobResponse getPreviewJob(String workspaceId, String projectId, String userId, String previewJobId) {
        requireProjectManageAccess(workspaceId, projectId, userId);
        IiifPreviewJobState state = Optional.ofNullable(previewJobCache.getIfPresent(previewJobId))
                .orElseThrow(() -> new ResourceNotFoundException("IIIF preview job", previewJobId));
        state.validateOwnership(workspaceId, projectId, userId);
        return state.toResponse();
    }

    public IiifImportDto.PreviewResponse previewFromManifestUrl(String workspaceId,
                                                                String projectId,
                                                                String userId,
                                                                IiifImportDto.PreviewRequest request) throws IOException {
        Project project = requireProjectManageAccess(workspaceId, projectId, userId);
        String manifestUrl = normalizeHttpUrl(request.manifestUrl());
        byte[] manifestBytes = fetchBytes(manifestUrl, "application/json, application/ld+json;q=0.9, */*;q=0.8");
        return buildPreview(
                workspaceId,
                project.getId(),
                userId,
                IiifImportJob.SourceType.MANIFEST_URL,
                manifestUrl,
                manifestUrl,
                manifestBytes,
                null
        );
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
        return buildPreview(
                workspaceId,
                project.getId(),
                userId,
                IiifImportJob.SourceType.MANIFEST_FILE,
                sourceName,
                null,
                file.getBytes(),
                null
        );
    }

    private IiifPreviewJobState createPreviewJobState(Project project, String userId) {
        IiifPreviewJobState state = new IiifPreviewJobState(
                UUID.randomUUID().toString(),
                project.getLibrary().getWorkspaceId(),
                project.getId(),
                userId
        );
        previewJobCache.put(state.id(), state);
        return state;
    }

    private void processPreviewJob(String previewJobId,
                                   String projectId,
                                   String userId,
                                   IiifImportJob.SourceType sourceType,
                                   String sourceReference,
                                   String sourceUrl,
                                   byte[] manifestBytes) {
        IiifPreviewJobState state = Optional.ofNullable(previewJobCache.getIfPresent(previewJobId)).orElse(null);
        if (state == null) {
            return;
        }

        try {
            byte[] resolvedBytes = manifestBytes;
            if (resolvedBytes == null) {
                state.onPhase("Fetching IIIF manifest");
                resolvedBytes = fetchBytes(sourceReference, "application/json, application/ld+json;q=0.9, */*;q=0.8");
            }
            state.onPhase("Parsing IIIF resource");
            buildPreview(
                    state.workspaceId,
                    projectId,
                    userId,
                    sourceType,
                    sourceReference,
                    sourceUrl,
                    resolvedBytes,
                    new PreviewJobProgressSink(state)
            );
        } catch (Exception e) {
            String message = e.getMessage() == null || e.getMessage().isBlank()
                    ? "Failed to build the IIIF preview."
                    : e.getMessage();
            state.fail(message);
        }
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

        List<IiifJobCanvasPayload> payloads = materializeJobPayloads(project, previewSession, request.selectedCanvasIds(), request.resolutions());
        return createImportJob(
                workspaceId,
                projectId,
                userId,
                previewSession.sourceType(),
                previewSession.sourceReference(),
                previewSession.manifest(),
                previewSession.warnings(),
                payloads,
                "IIIF import job created"
        );
    }

    public IiifImportDto.JobResponse retryFailedImportJob(String workspaceId,
                                                          String projectId,
                                                          String userId,
                                                          String jobId) {
        Project project = requireProjectManageAccess(workspaceId, projectId, userId);
        IiifImportJob sourceJob = iiifImportJobRepository.findByIdAndWorkspaceIdAndProjectId(jobId, workspaceId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("IIIF import job", jobId));

        List<IiifImportJob.Status> activeStatuses = List.of(IiifImportJob.Status.PENDING, IiifImportJob.Status.IMPORTING);
        if (!iiifImportJobRepository.findActiveJobsForProject(projectId, activeStatuses).isEmpty()) {
            throw new IllegalArgumentException("An IIIF import is already running for this project.");
        }

        List<IiifJobCanvasPayload> originalPayloads = readJobPayloads(sourceJob);
        if (originalPayloads.isEmpty()) {
            throw new IllegalArgumentException("The selected IIIF import job does not contain any canvases to retry.");
        }

        Set<String> failedCanvasIds = readJobResults(sourceJob).stream()
                .filter(result -> "FAILED".equalsIgnoreCase(result.status()))
                .map(IiifImportDto.ItemResult::canvasId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (failedCanvasIds.isEmpty()) {
            throw new IllegalArgumentException("This IIIF import job has no failed canvases to retry.");
        }

        List<IiifJobCanvasPayload> retryPayloads = originalPayloads.stream()
                .filter(payload -> failedCanvasIds.contains(payload.canvasId()))
                .toList();

        if (retryPayloads.isEmpty()) {
            throw new IllegalArgumentException("Could not resolve failed canvases for retry.");
        }

        IiifImportDto.ManifestSummary manifest = sourceJob.getManifestSummaryJson() == null || sourceJob.getManifestSummaryJson().isBlank()
                ? null
                : readJson(sourceJob.getManifestSummaryJson(), new TypeReference<IiifImportDto.ManifestSummary>() {}, null);
        List<String> warnings = readJson(sourceJob.getWarningsJson(), STRING_LIST_TYPE, List.of());

        return createImportJob(
                project.getLibrary().getWorkspaceId(),
                project.getId(),
                userId,
                sourceJob.getSourceType(),
                sourceJob.getSourceReference(),
                manifest,
                warnings,
                retryPayloads,
                "IIIF retry job created from failed canvases of job " + sourceJob.getId()
        );
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

    private IiifImportDto.JobResponse createImportJob(String workspaceId,
                                                      String projectId,
                                                      String userId,
                                                      IiifImportJob.SourceType sourceType,
                                                      String sourceReference,
                                                      IiifImportDto.ManifestSummary manifest,
                                                      List<String> warnings,
                                                      List<IiifJobCanvasPayload> payloads,
                                                      String logMessage) {
        if (payloads.isEmpty()) {
            throw new IllegalArgumentException("No IIIF canvases are selected for import.");
        }

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
            job.setSourceType(sourceType);
            job.setSourceReference(sourceReference);
            job.setStatus(IiifImportJob.Status.PENDING);
            job.setTotalCanvases(payloads.size());
            job.setEstimatedStorageBytes(estimatedBytes);
            job.setReservedBytes(reservedBytes);
            job.setManifestSummaryJson(manifest == null ? null : writeJson(manifest));
            job.setWarningsJson(writeJson(warnings == null ? List.of() : warnings));
            job.setCanvasPayloadJson(writeJson(payloads));
            job.setResultsJson("[]");
            job.appendToLog(logMessage);
            job = iiifImportJobRepository.save(job);

            triggerImportAfterCommit(job.getId());
            return toJobResponse(job);
        } catch (RuntimeException e) {
            workspaceQuotaGuardService.releaseReservation(workspaceId, reservedBytes);
            throw e;
        }
    }

    private IiifImportDto.PreviewResponse buildPreview(String workspaceId,
                                                       String projectId,
                                                       String userId,
                                                       IiifImportJob.SourceType sourceType,
                                                       String sourceReference,
                                                       String sourceUrl,
                                                       byte[] manifestBytes,
                                                       PreviewProgressSink progressSink) throws IOException {
        JsonNode root = objectMapper.readTree(manifestBytes);
        ParsedManifest parsedManifest = parseManifest(root, sourceType, sourceReference, sourceUrl, progressSink);
        List<String> warnings = new ArrayList<>(parsedManifest.warnings());
        boolean probeRemoteSizes = parsedManifest.canvases().stream().filter(ParsedCanvas::importable).count() <= MAX_REMOTE_SIZE_PROBES;
        if (!probeRemoteSizes) {
            warnings.add("Skipped remote image size probing for this preview because it contains more than "
                    + MAX_REMOTE_SIZE_PROBES
                    + " importable canvases. Quota preflight uses default estimates to avoid rate limiting.");
        }
        int importableCanvasCount = (int) parsedManifest.canvases().stream().filter(ParsedCanvas::importable).count();
        if (progressSink != null) {
            progressSink.onManifestParsed(parsedManifest.manifest(), parsedManifest.canvases().size(), importableCanvasCount, warnings);
        }
        List<IiifPreviewCanvas> canvases = enrichCanvases(projectId, parsedManifest, probeRemoteSizes, progressSink);
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
                workspaceId,
                projectId,
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
                .map(this::toCanvasPreview)
                .toList();

        IiifImportDto.PreviewResponse response = new IiifImportDto.PreviewResponse(
                previewToken,
                parsedManifest.manifest(),
                previewCanvases.size(),
                importableCanvasCount,
                estimatedStorageBytes,
                unknownSizeCanvasCount,
                List.copyOf(warnings),
                previewCanvases
        );
        if (progressSink != null) {
            progressSink.onCompleted(response);
        }
        return response;
    }

    private List<IiifJobCanvasPayload> materializeJobPayloads(Project project,
                                                              IiifPreviewSession session,
                                                              List<String> selectedCanvasIds,
                                                              List<IiifImportDto.Resolution> resolutions) {
        Map<String, IiifImportDto.Resolution> resolutionByCanvasId = (resolutions == null ? List.<IiifImportDto.Resolution>of() : resolutions).stream()
                .collect(Collectors.toMap(IiifImportDto.Resolution::canvasId, resolution -> resolution, (left, _right) -> left, LinkedHashMap::new));
        Set<String> selectedCanvasIdSet = normalizeSelectedCanvasIds(session.canvases(), selectedCanvasIds);
        Iterator<Integer> appendSortOrders = pageOrderService.reserveAppendSortOrders(
                project.getId(),
                countCreatedPages(session.canvases(), selectedCanvasIdSet, resolutionByCanvasId)
        ).iterator();

        Set<String> existingNames = pageRepository.findPageNamesByProjectId(project.getId()).stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> createdNames = new LinkedHashSet<>();
        List<IiifJobCanvasPayload> payloads = new ArrayList<>();

        for (IiifPreviewCanvas canvas : session.canvases()) {
            if (!canvas.importable()) {
                continue;
            }
            if (!selectedCanvasIdSet.contains(canvas.canvasId())) {
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
                            null,
                            canvas.derivedPageName(),
                            canvas.existingPageName(),
                            null,
                            action,
                            canvas.imageUrl(),
                            canvas.estimatedBytes(),
                            canvas.existingPageId(),
                            canvas.canvasId(),
                            buildExternalSourceMetadata(session.manifest(), canvas),
                            canvas.thumbnailUrl(),
                            canvas.sourceManifestId(),
                            canvas.sourceManifestLabel(),
                            canvas.sourceManifestUrl()
                    ));
                    case "REPLACE" -> payloads.add(new IiifJobCanvasPayload(
                            canvas.canvasId(),
                            canvas.canvasLabel(),
                            canvas.index(),
                            null,
                            canvas.derivedPageName(),
                            canvas.existingPageName(),
                            null,
                            action,
                            canvas.imageUrl(),
                            canvas.estimatedBytes(),
                            canvas.existingPageId(),
                            canvas.canvasId(),
                            buildExternalSourceMetadata(session.manifest(), canvas),
                            canvas.thumbnailUrl(),
                            canvas.sourceManifestId(),
                            canvas.sourceManifestLabel(),
                            canvas.sourceManifestUrl()
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
                                nextSortOrder(appendSortOrders),
                                canvas.derivedPageName(),
                                renamedPageName,
                                buildPageDescription(session.manifest(), canvas),
                                action,
                                canvas.imageUrl(),
                                canvas.estimatedBytes(),
                                null,
                                canvas.canvasId(),
                                buildExternalSourceMetadata(session.manifest(), canvas),
                                canvas.thumbnailUrl(),
                                canvas.sourceManifestId(),
                                canvas.sourceManifestLabel(),
                                canvas.sourceManifestUrl()
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
                    nextSortOrder(appendSortOrders),
                    canvas.derivedPageName(),
                    canvas.derivedPageName(),
                    buildPageDescription(session.manifest(), canvas),
                    "IMPORT",
                    canvas.imageUrl(),
                    canvas.estimatedBytes(),
                    null,
                    canvas.canvasId(),
                    buildExternalSourceMetadata(session.manifest(), canvas),
                    canvas.thumbnailUrl(),
                    canvas.sourceManifestId(),
                    canvas.sourceManifestLabel(),
                    canvas.sourceManifestUrl()
            ));
        }

        return payloads;
    }

    private int countCreatedPages(List<IiifPreviewCanvas> canvases,
                                  Set<String> selectedCanvasIdSet,
                                  Map<String, IiifImportDto.Resolution> resolutionByCanvasId) {
        int count = 0;
        for (IiifPreviewCanvas canvas : canvases) {
            if (!canvas.importable() || !selectedCanvasIdSet.contains(canvas.canvasId())) {
                continue;
            }
            if (canvas.existingPageId() == null) {
                count++;
                continue;
            }
            IiifImportDto.Resolution resolution = resolutionByCanvasId.get(canvas.canvasId());
            if (resolution == null) {
                throw new IllegalArgumentException("Missing resolution for conflicting canvas: " + canvas.canvasLabel());
            }
            if ("RENAME".equals(normalizeResolutionAction(resolution.action()))) {
                count++;
            }
        }
        return count;
    }

    private Integer nextSortOrder(Iterator<Integer> appendSortOrders) {
        if (!appendSortOrders.hasNext()) {
            throw new IllegalStateException("Missing reserved page sort order for IIIF import");
        }
        return appendSortOrders.next();
    }

    private Set<String> normalizeSelectedCanvasIds(List<IiifPreviewCanvas> canvases, List<String> selectedCanvasIds) {
        Set<String> importableCanvasIds = canvases.stream()
                .filter(IiifPreviewCanvas::importable)
                .map(IiifPreviewCanvas::canvasId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (selectedCanvasIds == null || selectedCanvasIds.isEmpty()) {
            return importableCanvasIds;
        }

        Set<String> normalized = selectedCanvasIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!importableCanvasIds.containsAll(normalized)) {
            throw new IllegalArgumentException("Selected canvases are no longer valid. Preview the manifest again.");
        }
        return normalized;
    }

    private List<IiifPreviewCanvas> enrichCanvases(String projectId,
                                                   ParsedManifest parsedManifest,
                                                   boolean probeRemoteSizes,
                                                   PreviewProgressSink progressSink) {
        Set<String> lowerPageNames = parsedManifest.canvases().stream()
                .map(ParsedCanvas::derivedPageName)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, Page> existingPagesByLowerName = pageRepository.findByProjectIdAndLowerNameIn(projectId, lowerPageNames).stream()
                .collect(Collectors.toMap(page -> page.getName().toLowerCase(Locale.ROOT), page -> page));
        Map<String, Boolean> existingIiifImagesByPageId = resolveExistingIiifImageFlags(
                existingPagesByLowerName.values().stream().map(Page::getId).toList()
        );

        List<IiifPreviewCanvas> enriched = new ArrayList<>();
        for (ParsedCanvas canvas : parsedManifest.canvases()) {
            Page existingPage = existingPagesByLowerName.get(canvas.derivedPageName().toLowerCase(Locale.ROOT));
            boolean existingIiifImage = existingPage != null && existingIiifImagesByPageId.getOrDefault(existingPage.getId(), false);
            Long estimatedBytes = canvas.importable() && probeRemoteSizes ? estimateRemoteSize(canvas.imageUrl()) : null;
            IiifPreviewCanvas previewCanvas = new IiifPreviewCanvas(
                    canvas.canvasId(),
                    canvas.canvasLabel(),
                    canvas.index(),
                    canvas.derivedPageName(),
                    canvas.importable(),
                    canvas.imageUrl(),
                    canvas.thumbnailUrl(),
                    estimatedBytes,
                    canvas.warnings(),
                    canvas.sourceManifestId(),
                    canvas.sourceManifestLabel(),
                    canvas.sourceManifestUrl(),
                    existingPage == null ? null : existingPage.getId(),
                    existingPage == null ? null : existingPage.getName(),
                    existingIiifImage
            );
            enriched.add(previewCanvas);
            if (progressSink != null) {
                progressSink.onCanvasProcessed(toCanvasPreview(previewCanvas));
            }
        }
        return enriched;
    }

    private IiifImportDto.CanvasPreview toCanvasPreview(IiifPreviewCanvas canvas) {
        return new IiifImportDto.CanvasPreview(
                canvas.canvasId(),
                canvas.canvasLabel(),
                canvas.index(),
                canvas.derivedPageName(),
                canvas.importable(),
                canvas.imageUrl(),
                canvas.thumbnailUrl(),
                canvas.estimatedBytes(),
                canvas.warnings(),
                canvas.sourceManifestLabel(),
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
        );
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
                                         String sourceUrl,
                                         PreviewProgressSink progressSink) throws IOException {
        String presentationVersion = detectPresentationVersion(root);
        if (presentationVersion == null) {
            throw new IllegalArgumentException("Unsupported IIIF resource. Only Presentation API v2 and v3 manifests and collections are supported.");
        }

        String resourceType = detectIiifResourceType(root);
        if (resourceType == null) {
            throw new IllegalArgumentException("Unsupported IIIF resource. Only manifests and collections are supported.");
        }

        String manifestId = extractNodeId(root);
        String manifestLabel = localizedText(root.path("label"));
        if (manifestLabel == null) {
            manifestLabel = sourceType == IiifImportJob.SourceType.MANIFEST_FILE ? sourceReference : manifestId;
        }

        List<String> warnings = new ArrayList<>();
        Map<String, Integer> globalNameCounts = new LinkedHashMap<>();
        int manifestCount = 1;
        List<ParsedCanvas> canvases;

        if ("COLLECTION".equals(resourceType)) {
            ParsedCollectionResult collectionResult = parseCollectionCanvases(root, warnings, globalNameCounts, sourceUrl, progressSink);
            canvases = collectionResult.canvases();
            manifestCount = collectionResult.manifestCount();
            if (manifestCount <= 0) {
                throw new IllegalArgumentException("The IIIF collection does not contain any importable manifests.");
            }
            warnings.add("Expanded IIIF collection into " + manifestCount + " manifests.");
        } else {
            canvases = parseManifestCanvases(
                    root,
                    manifestId,
                    manifestLabel,
                    sourceUrl,
                    presentationVersion,
                    warnings,
                    globalNameCounts
            );
        }

        String provider = extractProvider(root);
        String thumbnailUrl = extractThumbnailUrl(root, presentationVersion);
        IiifImportDto.ManifestSummary manifest = new IiifImportDto.ManifestSummary(
                manifestId,
                sourceUrl,
                sourceType.name(),
                sourceReference,
                resourceType,
                manifestLabel,
                provider,
                thumbnailUrl,
                presentationVersion,
                manifestCount
        );

        return new ParsedManifest(manifest, warnings, canvases);
    }

    private ParsedCollectionResult parseCollectionCanvases(JsonNode root,
                                                           List<String> warnings,
                                                           Map<String, Integer> globalNameCounts,
                                                           String sourceUrl,
                                                           PreviewProgressSink progressSink) throws IOException {
        Set<String> visitedUrls = new LinkedHashSet<>();
        if (hasText(sourceUrl)) {
            visitedUrls.add(sourceUrl);
        }
        if (progressSink != null) {
            progressSink.onPhase("Expanding IIIF collection");
        }
        return collectCollectionCanvases(root, warnings, globalNameCounts, visitedUrls, 0, progressSink);
    }

    private ParsedCollectionResult collectCollectionCanvases(JsonNode collectionNode,
                                                             List<String> warnings,
                                                             Map<String, Integer> globalNameCounts,
                                                             Set<String> visitedUrls,
                                                             int depth,
                                                             PreviewProgressSink progressSink) throws IOException {
        if (depth > 4) {
            throw new IllegalArgumentException("IIIF collection nesting is too deep.");
        }

        List<JsonNode> members = extractCollectionMembers(collectionNode);
        if (members.isEmpty()) {
            throw new IllegalArgumentException("IIIF collection does not contain any manifests.");
        }

        List<ParsedCanvas> canvases = new ArrayList<>();
        int manifestCount = 0;

        for (JsonNode member : members) {
            JsonNode resolvedMember = resolveCollectionMember(member, warnings, visitedUrls);
            if (resolvedMember == null) {
                continue;
            }

            String memberType = detectIiifResourceType(resolvedMember);
            if ("COLLECTION".equals(memberType)) {
                ParsedCollectionResult nested = collectCollectionCanvases(resolvedMember, warnings, globalNameCounts, visitedUrls, depth + 1, progressSink);
                canvases.addAll(nested.canvases());
                manifestCount += nested.manifestCount();
                continue;
            }

            if (!"MANIFEST".equals(memberType)) {
                String memberUrl = extractNodeId(resolvedMember);
                warnings.add("Skipped unsupported IIIF collection member" + (hasText(memberUrl) ? ": " + memberUrl : "."));
                continue;
            }

            if (manifestCount >= MAX_COLLECTION_MANIFESTS) {
                throw new IllegalArgumentException("IIIF collection exceeds the maximum supported size of " + MAX_COLLECTION_MANIFESTS + " manifests.");
            }

            String memberPresentationVersion = detectPresentationVersion(resolvedMember);
            if (memberPresentationVersion == null) {
                warnings.add("Skipped collection member because its IIIF Presentation version is unsupported.");
                continue;
            }

            String manifestId = extractNodeId(resolvedMember);
            String manifestLabel = localizedText(resolvedMember.path("label"));
            if (manifestLabel == null) {
                manifestLabel = manifestId;
            }

            canvases.addAll(parseManifestCanvases(
                    resolvedMember,
                    manifestId,
                    manifestLabel,
                    manifestId,
                    memberPresentationVersion,
                    warnings,
                    globalNameCounts
            ));
            manifestCount++;
            if (progressSink != null) {
                progressSink.onPhase("Expanded " + manifestCount + " IIIF manifest" + (manifestCount == 1 ? "" : "s"));
            }
        }

        return new ParsedCollectionResult(List.copyOf(canvases), manifestCount);
    }

    private JsonNode resolveCollectionMember(JsonNode member, List<String> warnings, Set<String> visitedUrls) throws IOException {
        if (member == null || member.isMissingNode() || member.isNull()) {
            return null;
        }

        if (member.has("items") || member.has("sequences") || member.has("manifests") || member.has("members") || member.has("collections")) {
            return member;
        }

        String memberUrl = extractNodeId(member);
        if (!hasText(memberUrl)) {
            warnings.add("Skipped a collection member without a manifest URL.");
            return null;
        }
        if (!visitedUrls.add(memberUrl)) {
            warnings.add("Skipped already visited IIIF collection member: " + memberUrl);
            return null;
        }

        try {
            byte[] bytes = fetchBytes(memberUrl, "application/json, application/ld+json;q=0.9, */*;q=0.8");
            return objectMapper.readTree(bytes);
        } catch (IllegalArgumentException | IOException e) {
            warnings.add("Skipped collection member " + memberUrl + ": " + e.getMessage());
            return null;
        }
    }

    private List<JsonNode> extractCollectionMembers(JsonNode root) {
        List<JsonNode> members = new ArrayList<>();
        addArrayChildren(root.path("items"), members);
        addArrayChildren(root.path("manifests"), members);
        addArrayChildren(root.path("members"), members);
        addArrayChildren(root.path("collections"), members);
        return members;
    }

    private void addArrayChildren(JsonNode node, List<JsonNode> target) {
        if (node == null || !node.isArray()) {
            return;
        }
        for (JsonNode child : node) {
            target.add(child);
        }
    }

    private List<ParsedCanvas> parseManifestCanvases(JsonNode root,
                                                     String manifestId,
                                                     String manifestLabel,
                                                     String manifestUrl,
                                                     String presentationVersion,
                                                     List<String> warnings,
                                                     Map<String, Integer> nameCounts) {
        return "3".equals(presentationVersion)
                ? parseV3Canvases(root, manifestId, manifestLabel, manifestUrl, warnings, nameCounts)
                : parseV2Canvases(root, manifestId, manifestLabel, manifestUrl, warnings, nameCounts);
    }

    private List<ParsedCanvas> parseV3Canvases(JsonNode root,
                                               String manifestId,
                                               String manifestLabel,
                                               String manifestUrl,
                                               List<String> warnings,
                                               Map<String, Integer> nameCounts) {
        JsonNode items = root.path("items");
        if (!items.isArray()) {
            throw new IllegalArgumentException("IIIF v3 manifest does not contain a canvas list.");
        }

        List<ParsedCanvas> canvases = new ArrayList<>();
        int index = nameCounts.values().stream().mapToInt(Integer::intValue).sum() + 1;
        for (JsonNode canvasNode : items) {
            String canvasId = extractNodeId(canvasNode);
            String canvasLabel = localizedText(canvasNode.path("label"));
            List<String> canvasWarnings = new ArrayList<>();
            ParsedImageChoice imageChoice = extractV3ImageChoice(canvasNode, canvasWarnings);
            String imageUrl = imageChoice == null ? null : imageChoice.imageUrl();
            String thumbnailUrl = imageChoice == null ? null : imageChoice.thumbnailUrl();
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
                    thumbnailUrl,
                    List.copyOf(canvasWarnings),
                    manifestId,
                    manifestLabel,
                    manifestUrl
            ));
            index++;
        }
        return canvases;
    }

    private List<ParsedCanvas> parseV2Canvases(JsonNode root,
                                               String manifestId,
                                               String manifestLabel,
                                               String manifestUrl,
                                               List<String> warnings,
                                               Map<String, Integer> nameCounts) {
        JsonNode sequences = root.path("sequences");
        if (!sequences.isArray() || sequences.isEmpty()) {
            throw new IllegalArgumentException("IIIF v2 manifest does not contain a sequence list.");
        }

        List<ParsedCanvas> canvases = new ArrayList<>();
        int index = nameCounts.values().stream().mapToInt(Integer::intValue).sum() + 1;
        for (JsonNode sequence : sequences) {
            JsonNode sequenceCanvases = sequence.path("canvases");
            if (!sequenceCanvases.isArray()) continue;
            for (JsonNode canvasNode : sequenceCanvases) {
                String canvasId = extractNodeId(canvasNode);
                String canvasLabel = localizedText(canvasNode.path("label"));
                List<String> canvasWarnings = new ArrayList<>();
                ParsedImageChoice imageChoice = extractV2ImageChoice(canvasNode, canvasWarnings);
                String imageUrl = imageChoice == null ? null : imageChoice.imageUrl();
                String thumbnailUrl = imageChoice == null ? null : imageChoice.thumbnailUrl();
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
                        thumbnailUrl,
                        List.copyOf(canvasWarnings),
                        manifestId,
                        manifestLabel,
                        manifestUrl
                ));
                index++;
            }
        }
        return canvases;
    }

    private ParsedImageChoice extractV3ImageChoice(JsonNode canvasNode, List<String> warnings) {
        for (JsonNode annotationPage : iterable(canvasNode.path("items"))) {
            for (JsonNode annotation : iterable(annotationPage.path("items"))) {
                JsonNode body = annotation.path("body");
                if (body.isArray()) {
                    for (JsonNode bodyCandidate : body) {
                        ParsedImageChoice candidate = chooseV3BodyImageChoice(bodyCandidate);
                        if (candidate != null) return candidate;
                    }
                    continue;
                }
                ParsedImageChoice candidate = chooseV3BodyImageChoice(body);
                if (candidate != null) return candidate;
            }
        }
        warnings.add("Canvas has no image annotation body.");
        return null;
    }

    private ParsedImageChoice chooseV3BodyImageChoice(JsonNode body) {
        if (body == null || body.isMissingNode() || body.isNull()) {
            return null;
        }

        String serviceUrl = extractImageServiceUrl(body);
        String bodyId = extractNodeId(body);
        if ("Image".equalsIgnoreCase(body.path("type").asText()) && hasText(bodyId)) {
            return new ParsedImageChoice(bodyId, serviceUrl == null ? bodyId : buildImageServiceThumbnailUrl(serviceUrl));
        }
        if (serviceUrl != null) {
            return new ParsedImageChoice(buildImageServiceUrl(serviceUrl, "3"), buildImageServiceThumbnailUrl(serviceUrl));
        }
        return hasText(bodyId) ? new ParsedImageChoice(bodyId, bodyId) : null;
    }

    private ParsedImageChoice extractV2ImageChoice(JsonNode canvasNode, List<String> warnings) {
        for (JsonNode imageNode : iterable(canvasNode.path("images"))) {
            JsonNode resource = imageNode.path("resource");
            String serviceUrl = extractImageServiceUrl(resource);
            String direct = extractNodeId(resource);
            if (hasText(direct)) {
                return new ParsedImageChoice(direct, serviceUrl == null ? direct : buildImageServiceThumbnailUrl(serviceUrl));
            }
            if (serviceUrl != null) {
                return new ParsedImageChoice(buildImageServiceUrl(serviceUrl, "2"), buildImageServiceThumbnailUrl(serviceUrl));
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
        if (root.has("sequences")) return "2";
        if (root.has("manifests") || root.has("members") || root.has("collections")) return "2";
        if (root.has("items")) return "3";
        return null;
    }

    private String detectIiifResourceType(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        String type = blankToNull(node.path("type").asText(null));
        if (type == null) {
            type = blankToNull(node.path("@type").asText(null));
        }
        if (type != null) {
            String normalized = type.toLowerCase(Locale.ROOT);
            if (normalized.contains("collection")) return "COLLECTION";
            if (normalized.contains("manifest")) return "MANIFEST";
        }

        if (node.has("sequences")) return "MANIFEST";
        if (node.has("manifests") || node.has("members") || node.has("collections")) return "COLLECTION";
        if (node.has("items")) {
            JsonNode items = node.path("items");
            if (!items.isArray() || items.isEmpty()) {
                return "MANIFEST";
            }
            JsonNode firstItem = items.get(0);
            String firstType = blankToNull(firstItem.path("type").asText(null));
            if (firstType == null) {
                firstType = blankToNull(firstItem.path("@type").asText(null));
            }
            if (firstType != null) {
                String normalizedFirstType = firstType.toLowerCase(Locale.ROOT);
                if (normalizedFirstType.contains("canvas")) return "MANIFEST";
                if (normalizedFirstType.contains("manifest") || normalizedFirstType.contains("collection")) return "COLLECTION";
            }
        }
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
            for (int attempt = 0; attempt < MAX_HTTP_ATTEMPTS; attempt++) {
                iiifRemoteRequestThrottler.awaitRequestSlot(imageUrl);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI(imageUrl))
                        .timeout(HTTP_TIMEOUT)
                        .method("HEAD", HttpRequest.BodyPublishers.noBody())
                        .header("User-Agent", "LAREX IIIF Import")
                        .build();
                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return parseContentLength(response.headers());
                }
                if (response.statusCode() == 429 && attempt < MAX_HTTP_ATTEMPTS - 1) {
                    iiifRemoteRequestThrottler.deferAfterRateLimit(imageUrl, response.headers(), attempt);
                    continue;
                }
                return null;
            }
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private byte[] fetchBytes(String url, String acceptHeader) throws IOException {
        try {
            for (int attempt = 0; attempt < MAX_HTTP_ATTEMPTS; attempt++) {
                iiifRemoteRequestThrottler.awaitRequestSlot(url);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI(url))
                        .timeout(HTTP_TIMEOUT)
                        .GET()
                        .header("Accept", acceptHeader)
                        .header("User-Agent", "LAREX IIIF Import")
                        .build();
                HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return response.body();
                }
                if (response.statusCode() == 429 && attempt < MAX_HTTP_ATTEMPTS - 1) {
                    iiifRemoteRequestThrottler.deferAfterRateLimit(url, response.headers(), attempt);
                    continue;
                }
                throw new IllegalArgumentException(buildManifestFetchStatusMessage(response.statusCode()));
            }
            throw new IllegalArgumentException(buildManifestFetchStatusMessage(429));
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
            case 429 -> "The IIIF server is rate limiting requests (HTTP 429). Please wait a moment and try again.";
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
        metadata.put("resourceType", manifest.resourceType());
        metadata.put("provider", manifest.provider());
        metadata.put("thumbnailUrl", manifest.thumbnailUrl());
        metadata.put("presentationVersion", manifest.presentationVersion());
        metadata.put("manifestCount", manifest.manifestCount());
        metadata.put("canvasId", canvas.canvasId());
        metadata.put("canvasLabel", canvas.canvasLabel());
        metadata.put("canvasIndex", canvas.index());
        metadata.put("imageUrl", canvas.imageUrl());
        metadata.put("canvasThumbnailUrl", canvas.thumbnailUrl());
        metadata.put("sourceType", manifest.sourceType());
        metadata.put("sourceName", manifest.sourceName());
        metadata.put("sourceManifestId", canvas.sourceManifestId());
        metadata.put("sourceManifestLabel", canvas.sourceManifestLabel());
        metadata.put("sourceManifestUrl", canvas.sourceManifestUrl());
        return writeJson(metadata);
    }

    private String buildPageDescription(IiifImportDto.ManifestSummary manifest, IiifPreviewCanvas canvas) {
        List<String> fragments = new ArrayList<>();
        String sourceManifestLabel = hasText(canvas.sourceManifestLabel()) ? canvas.sourceManifestLabel() : manifest.label();
        if (hasText(sourceManifestLabel)) {
            fragments.add("Imported from IIIF manifest: " + sourceManifestLabel);
        }
        if ("COLLECTION".equals(manifest.resourceType()) && hasText(manifest.label()) && !Objects.equals(manifest.label(), sourceManifestLabel)) {
            fragments.add("Collection: " + manifest.label());
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

    private String buildImageServiceThumbnailUrl(String serviceUrl) {
        return trimTrailingSlash(serviceUrl) + "/full/!200,200/0/default.jpg";
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

    private interface PreviewProgressSink {
        void onPhase(String phase);

        void onManifestParsed(IiifImportDto.ManifestSummary manifest, int totalCanvases, int importableCanvasCount, List<String> warnings);

        void onCanvasProcessed(IiifImportDto.CanvasPreview canvas);

        void onCompleted(IiifImportDto.PreviewResponse response);
    }

    private static final class PreviewJobProgressSink implements PreviewProgressSink {

        private final IiifPreviewJobState state;

        private PreviewJobProgressSink(IiifPreviewJobState state) {
            this.state = state;
        }

        @Override
        public void onPhase(String phase) {
            state.onPhase(phase);
        }

        @Override
        public void onManifestParsed(IiifImportDto.ManifestSummary manifest, int totalCanvases, int importableCanvasCount, List<String> warnings) {
            state.onManifestParsed(manifest, totalCanvases, importableCanvasCount, warnings);
        }

        @Override
        public void onCanvasProcessed(IiifImportDto.CanvasPreview canvas) {
            state.onCanvasProcessed(canvas);
        }

        @Override
        public void onCompleted(IiifImportDto.PreviewResponse response) {
            state.complete(response);
        }
    }

    private static final class IiifPreviewJobState {
        private final String id;
        private final String workspaceId;
        private final String projectId;
        private final String userId;
        private final LocalDateTime created = LocalDateTime.now();
        private LocalDateTime updated = created;
        private LocalDateTime completedAt;
        private String status = "PENDING";
        private String phase = "Queued";
        private String previewToken;
        private IiifImportDto.ManifestSummary manifest;
        private int totalCanvases;
        private int importableCanvasCount;
        private int processedCanvases;
        private long estimatedStorageBytes;
        private int unknownSizeCanvasCount;
        private List<String> warnings = new ArrayList<>();
        private List<IiifImportDto.CanvasPreview> canvases = new ArrayList<>();
        private String errorMessage;

        private IiifPreviewJobState(String id, String workspaceId, String projectId, String userId) {
            this.id = id;
            this.workspaceId = workspaceId;
            this.projectId = projectId;
            this.userId = userId;
        }

        private String id() {
            return id;
        }

        private synchronized void validateOwnership(String workspaceId, String projectId, String userId) {
            if (!Objects.equals(this.workspaceId, workspaceId)
                    || !Objects.equals(this.projectId, projectId)
                    || !Objects.equals(this.userId, userId)) {
                throw new IllegalArgumentException("IIIF preview job does not match this project or user.");
            }
        }

        private synchronized void onPhase(String phase) {
            if (isTerminal()) {
                return;
            }
            this.status = "RUNNING";
            this.phase = phase;
            this.updated = LocalDateTime.now();
        }

        private synchronized void onManifestParsed(IiifImportDto.ManifestSummary manifest,
                                                   int totalCanvases,
                                                   int importableCanvasCount,
                                                   List<String> warnings) {
            if (isTerminal()) {
                return;
            }
            this.status = "RUNNING";
            this.phase = "Preparing canvas preview";
            this.manifest = manifest;
            this.totalCanvases = totalCanvases;
            this.importableCanvasCount = importableCanvasCount;
            this.warnings = new ArrayList<>(warnings);
            this.updated = LocalDateTime.now();
        }

        private synchronized void onCanvasProcessed(IiifImportDto.CanvasPreview canvas) {
            if (isTerminal()) {
                return;
            }
            this.canvases.add(canvas);
            this.processedCanvases = this.canvases.size();
            if (canvas.importable()) {
                this.estimatedStorageBytes += canvas.estimatedBytes() == null
                        ? UNKNOWN_IMAGE_SIZE_ESTIMATE_BYTES
                        : canvas.estimatedBytes();
                if (canvas.estimatedBytes() == null) {
                    this.unknownSizeCanvasCount++;
                }
            }
            this.phase = "Preparing canvas preview";
            this.updated = LocalDateTime.now();
        }

        private synchronized void complete(IiifImportDto.PreviewResponse response) {
            this.status = "COMPLETED";
            this.phase = "Completed";
            this.previewToken = response.previewToken();
            this.manifest = response.manifest();
            this.totalCanvases = response.totalCanvases();
            this.importableCanvasCount = response.importableCanvasCount();
            this.processedCanvases = response.totalCanvases();
            this.estimatedStorageBytes = response.estimatedStorageBytes();
            this.unknownSizeCanvasCount = response.unknownSizeCanvasCount();
            this.warnings = new ArrayList<>(response.warnings());
            this.canvases = new ArrayList<>(response.canvases());
            this.errorMessage = null;
            this.completedAt = LocalDateTime.now();
            this.updated = this.completedAt;
        }

        private synchronized void fail(String errorMessage) {
            this.status = "FAILED";
            this.phase = "Failed";
            this.errorMessage = errorMessage;
            this.completedAt = LocalDateTime.now();
            this.updated = this.completedAt;
        }

        private synchronized IiifImportDto.PreviewJobResponse toResponse() {
            int progressPercent = totalCanvases > 0
                    ? Math.min(100, (int) Math.round((processedCanvases * 100.0d) / totalCanvases))
                    : ("COMPLETED".equals(status) ? 100 : 0);
            return new IiifImportDto.PreviewJobResponse(
                    id,
                    status,
                    phase,
                    previewToken,
                    manifest,
                    totalCanvases,
                    importableCanvasCount,
                    processedCanvases,
                    progressPercent,
                    estimatedStorageBytes,
                    unknownSizeCanvasCount,
                    List.copyOf(warnings),
                    List.copyOf(canvases),
                    errorMessage,
                    created,
                    updated,
                    completedAt
            );
        }

        private boolean isTerminal() {
            return "COMPLETED".equals(status) || "FAILED".equals(status);
        }
    }

    private record ParsedManifest(
            IiifImportDto.ManifestSummary manifest,
            List<String> warnings,
            List<ParsedCanvas> canvases
    ) {}

    private record ParsedCollectionResult(
            List<ParsedCanvas> canvases,
            int manifestCount
    ) {}

    private record ParsedImageChoice(
            String imageUrl,
            String thumbnailUrl
    ) {}

    private record ParsedCanvas(
            String canvasId,
            String canvasLabel,
            int index,
            String derivedPageName,
            boolean importable,
            String imageUrl,
            String thumbnailUrl,
            List<String> warnings,
            String sourceManifestId,
            String sourceManifestLabel,
            String sourceManifestUrl
    ) {}
}

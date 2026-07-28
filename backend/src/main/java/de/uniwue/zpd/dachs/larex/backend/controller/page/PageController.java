package de.uniwue.zpd.dachs.larex.backend.controller.page;

import de.uniwue.zpd.dachs.larex.backend.dto.DocumentExportDto;
import de.uniwue.zpd.dachs.larex.backend.dto.PaginatedResponse;
import de.uniwue.zpd.dachs.larex.backend.dto.PageXmlTextDto;
import de.uniwue.zpd.dachs.larex.backend.dto.SubtaskDto;
import de.uniwue.zpd.dachs.larex.backend.dto.TagSetDto;
import de.uniwue.zpd.dachs.larex.backend.dto.PageDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageFilterIndexService;
import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageIndexStatusReadService;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageOrderService;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageService;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageTextConfidenceStatsService;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageWorkflowService;
import de.uniwue.zpd.dachs.larex.backend.service.search.SearchPreviewService;
import de.uniwue.zpd.dachs.larex.backend.service.export.DocumentExportService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaGuardService;
import de.uniwue.zpd.dachs.larex.backend.service.task.SubtaskService;
import de.uniwue.zpd.dachs.larex.backend.service.tag.TagLookupService;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlConversionService;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlRawEditService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.*;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/projects/{projectId}/pages")
public class PageController {

    private static final Logger log = LoggerFactory.getLogger(PageController.class);

    private final PageService pageService;
    private final SubtaskService subtaskService;
    private final PageFilterIndexService pageFilterIndexService;
    private final PageIndexStatusReadService pageIndexStatusReadService;
    private final TagLookupService tagLookupService;
    private final PageXmlRawEditService pageXmlRawEditService;
    private final PageXmlConversionService pageXmlConversionService;
    private final DocumentExportService documentExportService;
    private final WorkspaceQuotaGuardService workspaceQuotaGuardService;
    private final SearchPreviewService searchPreviewService;
    private final PageOrderService pageOrderService;
    private final PageTextConfidenceStatsService pageTextConfidenceStatsService;
    private final PageWorkflowService pageWorkflowService;

    @Value("${file.upload-dir}")
    private String uploadDir;

    public PageController(PageService pageService, SubtaskService subtaskService, PageFilterIndexService pageFilterIndexService,
                          PageIndexStatusReadService pageIndexStatusReadService, TagLookupService tagLookupService,
                          PageXmlRawEditService pageXmlRawEditService,
                          PageXmlConversionService pageXmlConversionService,
                          DocumentExportService documentExportService,
                          WorkspaceQuotaGuardService workspaceQuotaGuardService,
                          SearchPreviewService searchPreviewService,
                          PageOrderService pageOrderService,
                          PageTextConfidenceStatsService pageTextConfidenceStatsService,
                          PageWorkflowService pageWorkflowService) {
        this.pageService = pageService;
        this.subtaskService = subtaskService;
        this.pageFilterIndexService = pageFilterIndexService;
        this.pageIndexStatusReadService = pageIndexStatusReadService;
        this.tagLookupService = tagLookupService;
        this.pageXmlRawEditService = pageXmlRawEditService;
        this.pageXmlConversionService = pageXmlConversionService;
        this.documentExportService = documentExportService;
        this.workspaceQuotaGuardService = workspaceQuotaGuardService;
        this.searchPreviewService = searchPreviewService;
        this.pageOrderService = pageOrderService;
        this.pageTextConfidenceStatsService = pageTextConfidenceStatsService;
        this.pageWorkflowService = pageWorkflowService;
    }

    @GetMapping
    public ResponseEntity<?> getProjectPages(
            @PathVariable String projectId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "sort", required = false, defaultValue = "projectOrder") String sort,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        Map<String, TagSetDto.TagNode> tagLookup = tagLookupService.buildTagLookupForProject(projectId);

        if (page != null && size != null) {
            org.springframework.data.domain.Page<Page> paginated = pageService.getProjectPagesPaginated(
                    projectId,
                    search,
                    tags,
                    sort,
                    page,
                    size,
                    userId
            );

            Map<String, PageDto.PageIndexingStatus> indexingStatuses =
                    pageIndexStatusReadService.resolveStatusesForProjectPages(projectId, paginated.getContent());
            Map<String, PageDto.TextConfidenceStats> textConfidenceStats =
                    pageTextConfidenceStatsService.resolveStats(projectId, paginated.getContent());

            List<PageDto.Response> content = paginated.getContent().stream()
                    .map(p -> mapToResponse(p, tagLookup, indexingStatuses.get(p.getId()), textConfidenceStats.get(p.getId())))
                    .toList();
            return ResponseEntity.ok(new PaginatedResponse<>(content, paginated.getNumber(), paginated.getSize(), paginated.getTotalElements(), paginated.getTotalPages()));
        }

        List<Page> pages;

        if (search != null && !search.trim().isEmpty()) {
            pages = pageService.searchPages(projectId, search, userId);
        } else if (tags != null && !tags.isEmpty()) {
            pages = pageService.getPagesByTags(projectId, tags, userId);
        } else {
            pages = pageService.getProjectPages(projectId, userId);
        }

        Map<String, PageDto.PageIndexingStatus> indexingStatuses =
                pageIndexStatusReadService.resolveStatusesForProjectPages(projectId, pages);
        Map<String, PageDto.TextConfidenceStats> textConfidenceStats =
                pageTextConfidenceStatsService.resolveStats(projectId, pages);

        List<PageDto.Response> response = pages.stream()
                .map(p -> mapToResponse(p, tagLookup, indexingStatuses.get(p.getId()), textConfidenceStats.get(p.getId())))
                .toList();

        return ResponseEntity.ok(response);
    }

    @PutMapping("/sort-order")
    public ResponseEntity<List<PageDto.Response>> updatePageSortOrder(
            @PathVariable String projectId,
            @Valid @RequestBody PageDto.SortOrderRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        Optional<List<Page>> pagesOpt = pageOrderService.reorderProjectPages(projectId, request.pageIds(), userId);
        if (pagesOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<Page> pages = pagesOpt.get();
        Map<String, TagSetDto.TagNode> tagLookup = tagLookupService.buildTagLookupForProject(projectId);
        Map<String, PageDto.PageIndexingStatus> indexingStatuses =
                pageIndexStatusReadService.resolveStatusesForProjectPages(projectId, pages);
        Map<String, PageDto.TextConfidenceStats> textConfidenceStats =
                pageTextConfidenceStatsService.resolveStats(projectId, pages);

        return ResponseEntity.ok(pages.stream()
                .map(page -> mapToResponse(
                        page,
                        tagLookup,
                        indexingStatuses.get(page.getId()),
                        textConfidenceStats.get(page.getId())
                ))
                .toList());
    }

    @PutMapping("/{pageId}/workflow-state")
    public ResponseEntity<PageDto.Response> updateWorkflowState(
            @PathVariable String projectId,
            @PathVariable String pageId,
            @Valid @RequestBody PageDto.WorkflowStateRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        Page page = pageWorkflowService.updateState(projectId, pageId, request.workflowState(), userId);
        Map<String, PageDto.PageIndexingStatus> indexingStatuses =
                pageIndexStatusReadService.resolveStatusesForProjectPages(projectId, List.of(page));
        Map<String, PageDto.TextConfidenceStats> textConfidenceStats =
                pageTextConfidenceStatsService.resolveStats(projectId, List.of(page));
        return ResponseEntity.ok(mapToResponse(page, tagLookupService.buildTagLookupForProject(projectId),
                indexingStatuses.get(pageId), textConfidenceStats.get(pageId)));
    }

    @PutMapping("/bulk/workflow-state")
    public ResponseEntity<List<PageDto.Response>> bulkUpdateWorkflowState(
            @PathVariable String projectId,
            @Valid @RequestBody PageDto.BulkWorkflowStateRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        List<Page> pages = pageWorkflowService.bulkUpdateState(
                projectId, request.pageIds(), request.workflowState(), userId);
        Map<String, PageDto.PageIndexingStatus> indexingStatuses =
                pageIndexStatusReadService.resolveStatusesForProjectPages(projectId, pages);
        Map<String, PageDto.TextConfidenceStats> textConfidenceStats =
                pageTextConfidenceStatsService.resolveStats(projectId, pages);
        Map<String, TagSetDto.TagNode> tagLookup = tagLookupService.buildTagLookupForProject(projectId);
        return ResponseEntity.ok(pages.stream()
                .map(page -> mapToResponse(page, tagLookup, indexingStatuses.get(page.getId()),
                        textConfidenceStats.get(page.getId())))
                .toList());
    }

    // ============================================================================
    // Page Filter Endpoints
    // ============================================================================

    /**
     * Filter pages by multiple criteria with AND/OR logic.
     * Returns only the IDs of matching pages for efficiency.
     */
    @PostMapping("/filter")
    public ResponseEntity<PageDto.FilterResponse> filterPages(
            @PathVariable String projectId,
            @RequestBody PageDto.FilterRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        String operator = request.filterOperator() != null ? request.filterOperator() : "or";
        Set<String> pageIds = pageFilterIndexService.filterPages(
                projectId,
                request.textContent(),
                request.labelIds(),
                operator,
                request.tags(),
                operator,
                request.confidenceMin(),
                request.confidenceMax(),
                request.confidenceElementTypes(),
                request.hasComments()
        );

        return ResponseEntity.ok(new PageDto.FilterResponse(pageIds, pageIds.size()));
    }

    @GetMapping("/index-statuses")
    public ResponseEntity<Map<String, String>> getPageIndexStatuses(
            @PathVariable String projectId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        List<Page> pages = pageService.getProjectPages(projectId, userId);
        Map<String, PageDto.PageIndexingStatus> statuses =
                pageIndexStatusReadService.resolveStatusesForProjectPages(projectId, pages);

        Map<String, String> response = new LinkedHashMap<>();
        for (Page page : pages) {
            PageDto.PageIndexingStatus status = statuses.getOrDefault(page.getId(), PageDto.PageIndexingStatus.NOT_APPLICABLE);
            response.put(page.getId(), status.name());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Get text line IDs that match the text content filter within a page.
     * Used to highlight matching text lines in the UI.
     */
    @GetMapping("/{pageId}/matching-textlines")
    public ResponseEntity<PageDto.MatchingTextLinesResponse> getMatchingTextLines(
            @PathVariable String projectId,
            @PathVariable String pageId,
            @RequestParam String textContent,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        List<String> textLineIds = pageFilterIndexService.getMatchingTextLineIds(pageId, textContent);
        return ResponseEntity.ok(new PageDto.MatchingTextLinesResponse(pageId, textLineIds));
    }

    /**
     * Get text region IDs that match the text content filter within a page.
     * Used to highlight matching text regions in the UI.
     */
    @GetMapping("/{pageId}/matching-textregions")
    public ResponseEntity<PageDto.MatchingTextRegionsResponse> getMatchingTextRegions(
            @PathVariable String projectId,
            @PathVariable String pageId,
            @RequestParam String textContent,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        List<String> regionIds = pageFilterIndexService.getMatchingTextRegionIds(pageId, textContent);
        return ResponseEntity.ok(new PageDto.MatchingTextRegionsResponse(pageId, regionIds));
    }

    /**
     * Get index statistics for a project.
     */
    @GetMapping("/index-stats")
    public ResponseEntity<PageDto.IndexStatsResponse> getIndexStats(
            @PathVariable String projectId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        log.info("Getting index stats for project: {}", projectId);
        try {
            Map<String, Object> stats = pageFilterIndexService.getIndexStats(projectId);
            log.info("Index stats retrieved: {}", stats);
            
            Long totalPages = (Long) stats.get("totalPages");
            Long indexedTextContentPages = (Long) stats.get("indexedTextContentPages");
            Long indexedLabelPages = (Long) stats.get("indexedLabelPages");
            Long pagesNeedingIndex = (Long) stats.get("pagesNeedingIndex");
            
            log.info("Creating response with totalPages={}, indexedTextContentPages={}, indexedLabelPages={}, pagesNeedingIndex={}",
                    totalPages, indexedTextContentPages, indexedLabelPages, pagesNeedingIndex);
            
            return ResponseEntity.ok(new PageDto.IndexStatsResponse(
                    totalPages,
                    indexedTextContentPages,
                    indexedLabelPages,
                    pagesNeedingIndex
            ));
        } catch (Exception e) {
            log.error("Error getting index stats for project {}: {}", projectId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Get available labels with page counts for filter dropdown.
     */
    @GetMapping("/available-labels")
    public ResponseEntity<List<PageDto.LabelWithCount>> getAvailableLabels(
            @PathVariable String projectId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        List<Map<String, Object>> labels = pageFilterIndexService.getAvailableLabelsWithCounts(projectId);
        List<PageDto.LabelWithCount> response = labels.stream()
                .map(m -> new PageDto.LabelWithCount(
                        (String) m.get("labelId"),
                        (Long) m.get("pageCount")
                ))
                .toList();
        return ResponseEntity.ok(response);
    }

    /**
     * Rebuild the search index for all pages in a project.
     */
    @PostMapping("/rebuild-index")
    public ResponseEntity<Map<String, String>> rebuildIndex(
            @PathVariable String projectId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        pageFilterIndexService.rebuildProjectIndex(projectId);
        return ResponseEntity.accepted().body(Map.of("message", "Index rebuild started for project " + projectId));
    }

    @GetMapping("/{pageId}")
    public ResponseEntity<PageDto.Response> getPage(
            @PathVariable String projectId,
            @PathVariable String pageId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        Optional<Page> pageOpt = pageService.getPageById(pageId, userId);
        Map<String, TagSetDto.TagNode> tagLookup = tagLookupService.buildTagLookupForProject(projectId);

        return pageOpt.map(page -> mapToResponse(
                        page,
                        tagLookup,
                        pageIndexStatusReadService.resolveStatusForPage(page),
                        pageTextConfidenceStatsService.resolveStats(projectId, List.of(page)).get(page.getId())
                ))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<PageDto.Response> createPage(
            @PathVariable String projectId,
            @Valid @RequestBody PageDto.CreateOrUpdateRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        Optional<Page> pageOpt = pageService.createPage(
                projectId,
                request.name(),
                request.description(),
                request.tags(),
                userId
        );

        Map<String, TagSetDto.TagNode> tagLookup = tagLookupService.buildTagLookupForProject(projectId);
        return pageOpt.map(page -> mapToResponse(
                        page,
                        tagLookup,
                        pageIndexStatusReadService.resolveStatusForPage(page),
                        pageTextConfidenceStatsService.resolveStats(projectId, List.of(page)).get(page.getId())
                ))
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response))
                .orElse(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    @PutMapping("/{pageId}")
    public ResponseEntity<PageDto.Response> updatePage(
            @PathVariable String projectId,
            @PathVariable String pageId,
            @Valid @RequestBody PageDto.CreateOrUpdateRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        Optional<Page> pageOpt = pageService.updatePage(
                pageId,
                request.name(),
                request.description(),
                request.tags(),
                userId
        );

        Map<String, TagSetDto.TagNode> tagLookup = tagLookupService.buildTagLookupForProject(projectId);
        return pageOpt.map(page -> mapToResponse(
                        page,
                        tagLookup,
                        pageIndexStatusReadService.resolveStatusForPage(page),
                        pageTextConfidenceStatsService.resolveStats(projectId, List.of(page)).get(page.getId())
                ))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{pageId}")
    public ResponseEntity<Void> deletePage(
            @PathVariable String projectId,
            @PathVariable String pageId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        boolean deleted = pageService.deletePage(pageId, userId);

        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @DeleteMapping("/batch")
    public ResponseEntity<Map<String, Object>> deletePages(
            @PathVariable String projectId,
            @RequestBody List<String> pageIds,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        int deletedCount = pageService.deletePages(pageIds, userId);

        return ResponseEntity.ok(Map.of(
                "deletedCount", deletedCount,
                "requestedCount", pageIds.size()
        ));
    }

    @GetMapping("/{pageId}/xml")
    public ResponseEntity<List<PageDto.XmlResponse>> getPageXmlFiles(
            @PathVariable String projectId,
            @PathVariable String pageId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        List<PageXml> xmlFiles = pageService.getPageXmlFiles(pageId, userId);
        List<PageDto.XmlResponse> response = xmlFiles.stream()
                .map(this::mapToXmlResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/annotations/batch")
    public ResponseEntity<PageService.AnnotationDeleteResult> deleteAnnotations(
            @PathVariable String projectId,
            @RequestBody List<String> pageIds,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(pageService.deleteAnnotations(projectId, pageIds, userId));
    }

    @PostMapping("/{pageId}/export")
    public ResponseEntity<?> exportPage(
            @PathVariable String projectId,
            @PathVariable String pageId,
            @RequestBody DocumentExportDto.PageExportRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) throws IOException {
        DocumentExportService.StreamingDocumentExportResult exportResult =
                documentExportService.exportPageStream(projectId, pageId, userId, request);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(exportResult.contentType()));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(exportResult.fileName())
                .build());
        StreamingResponseBody body = exportResult.writer()::write;

        return ResponseEntity.ok()
                .headers(headers)
                .body(body);
    }

    @GetMapping("/{pageId}/xml/{xmlId}/text")
    public ResponseEntity<PageXmlTextDto.XmlTextResponse> getXmlText(
            @PathVariable String projectId,
            @PathVariable String pageId,
            @PathVariable String xmlId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        try {
            PageXmlTextDto.XmlTextResponse response = pageXmlRawEditService.getXmlText(projectId, pageId, xmlId, userId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (UnsupportedOperationException e) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{pageId}/xml/{xmlId}/validate")
    public ResponseEntity<PageXmlTextDto.XmlValidationResult> validateXmlText(
            @PathVariable String projectId,
            @PathVariable String pageId,
            @PathVariable String xmlId,
            @Valid @RequestBody PageXmlTextDto.ValidateRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        try {
            // Authorization and path consistency check.
            pageXmlRawEditService.assertPageXmlAccess(projectId, pageId, xmlId, userId);
            PageXmlTextDto.XmlValidationResult validation = pageXmlRawEditService.validateXmlText(request.xml());
            return ResponseEntity.ok(validation);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (UnsupportedOperationException e) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        }
    }

    @PutMapping("/{pageId}/xml/{xmlId}/text")
    public ResponseEntity<?> saveXmlText(
            @PathVariable String projectId,
            @PathVariable String pageId,
            @PathVariable String xmlId,
            @Valid @RequestBody PageXmlTextDto.SaveRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        try {
            PageXmlTextDto.XmlValidationResult validation = pageXmlRawEditService.saveXmlText(
                    projectId,
                    pageId,
                    xmlId,
                    request.xml(),
                    request.comment(),
                    userId
            );
            if (!validation.valid()) {
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(validation);
            }
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (UnsupportedOperationException e) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private PageDto.XmlResponse mapToXmlResponse(PageXml xml) {
        return new PageDto.XmlResponse(
                xml.getId(),
                xml.getFileName(),
                xml.getFilePath(),
                xml.getMimeType(),
                xml.getFileSize(),
                xml.getVariant(),
                xml.getBaseName(),
                xml.getSchema().name(),
                xml.getSchemaVersion(),
                xml.getCreated(),
                xml.getUpdated()
        );
    }

    @PostMapping("/{pageId}/xml")
    public ResponseEntity<Void> uploadXmlFile(
            @PathVariable String projectId,
            @PathVariable String pageId,
            @RequestParam("file") MultipartFile xmlFile,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        Optional<Page> pageOpt = pageService.getPageById(pageId, userId);
        if (pageOpt.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        String workspaceId = pageOpt.get().getProject().getLibrary().getWorkspaceId();
        long reservedBytes = 0L;
        try {
            reservedBytes = workspaceQuotaGuardService.reserveBytesOrThrow(
                    workspaceId,
                    xmlFile == null ? 0L : xmlFile.getSize(),
                    "page-xml-upload"
            );
            boolean uploaded = pageService.uploadXmlFile(pageId, xmlFile, userId);
            return uploaded ? ResponseEntity.ok().build() : ResponseEntity.badRequest().build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } finally {
            workspaceQuotaGuardService.syncUsageAndReleaseReservation(workspaceId, reservedBytes);
        }
    }

    @PostMapping("/{pageId}/images")
    public ResponseEntity<Void> uploadImages(
            @PathVariable String projectId,
            @PathVariable String pageId,
            @RequestParam("files") List<MultipartFile> images,
            @RequestParam(value = "variants", required = false) List<String> variants,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        Optional<Page> pageOpt = pageService.getPageById(pageId, userId);
        if (pageOpt.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        String workspaceId = pageOpt.get().getProject().getLibrary().getWorkspaceId();
        long reservedBytes = 0L;
        try {
            reservedBytes = workspaceQuotaGuardService.reserveBytesOrThrow(
                    workspaceId,
                    workspaceQuotaGuardService.totalMultipartBytes(images),
                    "page-image-upload"
            );
            boolean uploaded = pageService.uploadImages(pageId, images, variants, userId);
            return uploaded ? ResponseEntity.ok().build() : ResponseEntity.badRequest().build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } finally {
            workspaceQuotaGuardService.syncUsageAndReleaseReservation(workspaceId, reservedBytes);
        }
    }

    @GetMapping("/{pageId}/images")
    public ResponseEntity<List<PageDto.ImageResponse>> getPageImages(
            @PathVariable String projectId,
            @PathVariable String pageId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        List<PageImage> images = pageService.getPageImages(pageId, userId);

        List<PageDto.ImageResponse> response = images.stream()
                .map(this::mapToImageResponse)
                .toList();

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{pageId}/images/batch")
    public ResponseEntity<PageService.ImageDeleteResult> deleteImages(
            @PathVariable String projectId,
            @PathVariable String pageId,
            @RequestBody List<String> imageIds,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(pageService.deleteImages(projectId, pageId, imageIds, userId));
    }

    @GetMapping("/{pageId}/thumbnails")
    public ResponseEntity<Map<String, String>> getPageThumbnails(
            @PathVariable String projectId,
            @PathVariable String pageId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        List<PageImage> images = pageService.getPageImages(pageId, userId);

        Map<String, String> thumbnails = images.stream()
                .filter(img -> img.getThumbnailPath() != null)
                .collect(Collectors.toMap(
                        PageImage::getId,
                        img -> "/api/projects/" + projectId + "/pages/images/" + img.getId() + "/thumbnail"
                ));

        return ResponseEntity.ok(thumbnails);
    }


    private PageDto.Response mapToResponse(Page page,
                                          Map<String, TagSetDto.TagNode> tagLookup,
                                          PageDto.PageIndexingStatus indexingStatus,
                                          PageDto.TextConfidenceStats textConfidenceStats) {
        List<PageDto.ResolvedTag> resolvedTags = null;
        if (tagLookup != null && !tagLookup.isEmpty() && page.getTags() != null) {
            resolvedTags = page.getTags().stream()
                    .map(tagId -> {
                        TagSetDto.TagNode tag = tagLookup.get(tagId);
                        if (tag != null) {
                            return new PageDto.ResolvedTag(tagId, tag.title(), tag.color());
                        }
                        return new PageDto.ResolvedTag(tagId, tagId, null);
                    })
                    .toList();
        }

        String thumbnailUrl = null;
        List<PageImage> images = page.getImages() != null ? new ArrayList<>(page.getImages()) : List.of();
        if (!images.isEmpty()) {
            PageImage img = images.stream()
                    .filter(i -> i.getThumbnailPath() != null)
                    .findFirst()
                    .orElse(images.getFirst());
            thumbnailUrl = "/api/projects/" + page.getProject().getId()
                    + "/pages/images/" + img.getId() + "/thumbnail";
        }

        List<PageDto.ImageVariantPreview> imageVariants = images.stream()
                .map(this::mapToImageVariantPreview)
                .toList();

        return new PageDto.Response(
                page.getId(),
                page.getName(),
                page.getDescription(),
                page.getTags(),
                resolvedTags,
                page.getCreated(),
                page.getUpdated(),
                page.getSortOrder(),
                textConfidenceStats,
                indexingStatus == null || indexingStatus == PageDto.PageIndexingStatus.NOT_APPLICABLE ? 0 : 1,
                images.size(),
                page.getWorkflowState(),
                page.isEffectivelyLocked(),
                page.getEffectiveLockedReason(),
                thumbnailUrl,
                indexingStatus != null ? indexingStatus : PageDto.PageIndexingStatus.NOT_APPLICABLE,
                imageVariants
        );
    }

    private PageDto.ImageVariantPreview mapToImageVariantPreview(PageImage image) {
        return new PageDto.ImageVariantPreview(
                image.getId(),
                image.getFileName(),
                image.getVariant()
        );
    }

    private PageDto.ImageResponse mapToImageResponse(PageImage image) {
        return new PageDto.ImageResponse(
                image.getId(),
                image.getFileName(),
                image.getFilePath(),
                image.getMimeType(),
                image.getFileSize(),
                image.getVariant(),
                image.getBaseName(),
                image.getThumbnailPath(),
                image.getCreated()
        );
    }

    @GetMapping("/{pageId}/images/grouped")
    public ResponseEntity<Map<String, List<PageDto.ImageResponse>>> getPageImagesGroupedByBaseName(
            @PathVariable String projectId,
            @PathVariable String pageId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        Map<String, List<PageImage>> groupedImages = pageService.getPageImagesGroupedByBaseName(pageId, userId);
        
        Map<String, List<PageDto.ImageResponse>> response = groupedImages.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .map(this::mapToImageResponse)
                                .toList()
                ));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/images/{imageId}/transfer")
    public ResponseEntity<Void> transferImageToPage(
            @PathVariable String projectId,
            @PathVariable String imageId,
            @RequestParam String targetPageId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        boolean success = pageService.transferImageToPage(imageId, targetPageId, userId);
        
        return success ?
            ResponseEntity.ok().build() :
            ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @GetMapping("/images/{imageId}/blob")
    public ResponseEntity<Resource> getImageBlob(
            @PathVariable String projectId,
            @PathVariable String imageId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        try {
            // Get the image by ID
            PageImage image = pageService.getImageById(imageId, userId);

            if (image == null) {
                return ResponseEntity.notFound().build();
            }

            // Use FileSystemResource to serve the file directly without loading into memory
            Path filePath = Paths.get(uploadDir).resolve(image.getFilePath());
            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }

            FileSystemResource resource = new FileSystemResource(filePath);

            // Return the file as Resource with appropriate content type
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(image.getMimeType()))
                    .header("Cache-Control", "public, max-age=3600")
                    .header("Content-Length", String.valueOf(resource.contentLength()))
                    .header("Accept-Ranges", "bytes")
                    .body(resource);

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/images/{imageId}/export")
    public ResponseEntity<Resource> exportImage(
            @PathVariable String projectId,
            @PathVariable String imageId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        try {
            PageImage image = pageService.getImageById(imageId, userId);
            if (image == null) {
                return ResponseEntity.notFound().build();
            }

            Path filePath = Paths.get(uploadDir).resolve(image.getFilePath());
            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }

            FileSystemResource resource = new FileSystemResource(filePath);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(image.getMimeType()));
            headers.setContentLength(resource.contentLength());
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename(sanitizeFileName(image.getFileName(), "image"))
                    .build());

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/xml/{xmlId}/blob")
    public ResponseEntity<?> getXmlBlob(
            @PathVariable String projectId,
            @PathVariable String xmlId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return streamXml(xmlId, userId, false, null);
    }

    @GetMapping("/xml/{xmlId}/export")
    public ResponseEntity<?> exportXml(
            @PathVariable String projectId,
            @PathVariable String xmlId,
            @RequestParam(required = false) String targetPageXmlVersion,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return streamXml(xmlId, userId, true, targetPageXmlVersion);
    }

    private ResponseEntity<?> streamXml(String xmlId, String userId, boolean asAttachment, String targetPageXmlVersion) {
        try {
            PageXml xml = pageService.getXmlById(xmlId, userId);
            if (xml == null) {
                return ResponseEntity.notFound().build();
            }

            Path filePath = Paths.get(uploadDir).resolve(xml.getFilePath());
            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }

            FileSystemResource resource = new FileSystemResource(filePath);
            HttpHeaders headers = new HttpHeaders();
            MediaType contentType = resolveXmlContentType(xml);

            headers.setContentType(contentType);
            if (asAttachment) {
                headers.setContentDisposition(ContentDisposition.attachment()
                        .filename(sanitizeFileName(xml.getFileName(), "document.xml"))
                        .build());
            } else {
                headers.setContentDisposition(ContentDisposition.inline()
                        .filename(sanitizeFileName(xml.getFileName(), "document.xml"))
                        .build());
            }

            if (asAttachment && xml.getSchema() == XmlSchema.PAGE_XML) {
                String normalizedTarget = pageXmlConversionService.normalizeTargetVersion(targetPageXmlVersion);
                StreamingResponseBody body = outputStream ->
                        pageXmlConversionService.writeFileToVersion(filePath, normalizedTarget, outputStream);
                return ResponseEntity.ok()
                        .headers(headers)
                        .body(body);
            }

            headers.setContentLength(resource.contentLength());
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(resource);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to export XML {}: {}", xmlId, e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            log.error("I/O failure while exporting XML {}", xmlId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private MediaType resolveXmlContentType(PageXml xml) {
        if (xml.getMimeType() == null || xml.getMimeType().isBlank()) {
            return MediaType.APPLICATION_XML;
        }
        try {
            return MediaType.parseMediaType(xml.getMimeType());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid XML mime type '{}' for xml {}", xml.getMimeType(), xml.getId());
            return MediaType.APPLICATION_XML;
        }
    }

    @GetMapping("/images/{imageId}/thumbnail")
    public ResponseEntity<Resource> getImageThumbnail(
            @PathVariable String projectId,
            @PathVariable String imageId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        try {
            PageImage image = pageService.getImageById(imageId, userId);

            if (image == null) {
                return ResponseEntity.notFound().build();
            }

            // Try to serve thumbnail, fallback to full image if not available
            String pathToServe = image.getThumbnailPath() != null ? image.getThumbnailPath() : image.getFilePath();
            Path filePath = Paths.get(uploadDir).resolve(pathToServe);

            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }

            FileSystemResource resource = new FileSystemResource(filePath);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(image.getMimeType()))
                    .header("Cache-Control", "public, max-age=86400")
                    .header("Content-Length", String.valueOf(resource.contentLength()))
                    .body(resource);

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{pageId}/text-preview")
    public ResponseEntity<Resource> getTextPreview(
            @PathVariable String projectId,
            @PathVariable String pageId,
            @RequestParam(required = false) String textLineId,
            @RequestParam(required = false) String regionId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        SearchPreviewService.PreviewImage preview = searchPreviewService.getTextPreview(
                projectId,
                pageId,
                textLineId,
                regionId,
                userId
        );
        if (preview == null) {
            return ResponseEntity.notFound().build();
        }

        ByteArrayResource resource = new ByteArrayResource(preview.bytes());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(preview.mediaType()))
                .header("Cache-Control", "public, max-age=600")
                .contentLength(preview.bytes().length)
                .body(resource);
    }

    @GetMapping("/subtask-summary")
    public ResponseEntity<Map<String, Long>> getSubtaskSummaryForPages(
            @PathVariable String projectId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        // Get all pages for this project
        List<Page> pages = pageService.getProjectPages(projectId, userId);
        List<String> pageIds = pages.stream().map(Page::getId).toList();

        // Get open subtask counts for current user
        Map<String, Long> summary = subtaskService.getOpenSubtaskCountsForPages(pageIds, userId);

        return ResponseEntity.ok(summary);
    }

    @GetMapping("/subtasks/open")
    public ResponseEntity<Map<String, List<SubtaskDto.Response>>> getOpenSubtasksForPages(
            @PathVariable String projectId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        List<Page> pages = pageService.getProjectPages(projectId, userId);
        List<String> pageIds = pages.stream().map(Page::getId).toList();

        Map<String, List<SubtaskDto.Response>> subtasks = subtaskService.getOpenSubtasksForPages(pageIds, userId);

        return ResponseEntity.ok(subtasks);
    }

    private String sanitizeFileName(String value, String fallback) {
        if (value == null) {
            return fallback;
        }

        String sanitized = value.trim()
                .replaceAll("[\\\\/:*?\"<>|]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return sanitized.isBlank() ? fallback : sanitized;
    }
}

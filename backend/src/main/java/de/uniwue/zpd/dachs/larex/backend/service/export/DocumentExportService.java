package de.uniwue.zpd.dachs.larex.backend.service.export;

import de.uniwue.zpd.dachs.larex.backend.dto.DocumentExportDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.application.AnnotationProcessingService;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageOrderService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlConversionService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DocumentExportService {

    private static final Comparator<PageImage> PAGE_IMAGE_COMPARATOR =
            Comparator.comparing((PageImage image) -> image.getVariant() == null ? "" : image.getVariant(), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(PageImage::getFileName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(PageImage::getId);

    private final ProjectRepository projectRepository;
    private final PageXmlRepository pageXmlRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final AnnotationProcessingService annotationProcessingService;
    private final PageXmlConversionService pageXmlConversionService;
    private final PageOrderService pageOrderService;
    private final TextDocumentExportWriter textDocumentExportWriter;
    private final AltoExportWriter altoExportWriter;
    private final SpreadsheetExportWriter spreadsheetExportWriter;

    @Value("${file.upload-dir}")
    private String uploadDir;

    public DocumentExportService(ProjectRepository projectRepository,
                                 PageXmlRepository pageXmlRepository,
                                 WorkspaceAccessService workspaceAccessService,
                                 AnnotationProcessingService annotationProcessingService,
                                 PageXmlConversionService pageXmlConversionService,
                                 PageOrderService pageOrderService,
                                 TextDocumentExportWriter textDocumentExportWriter,
                                 AltoExportWriter altoExportWriter,
                                 SpreadsheetExportWriter spreadsheetExportWriter) {
        this.projectRepository = projectRepository;
        this.pageXmlRepository = pageXmlRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.annotationProcessingService = annotationProcessingService;
        this.pageXmlConversionService = pageXmlConversionService;
        this.pageOrderService = pageOrderService;
        this.textDocumentExportWriter = textDocumentExportWriter;
        this.altoExportWriter = altoExportWriter;
        this.spreadsheetExportWriter = spreadsheetExportWriter;
    }

    public DocumentExportResult exportPage(String projectId,
                                           String pageId,
                                           String userId,
                                           DocumentExportDto.PageExportRequest request) throws IOException {
        StreamingDocumentExportResult streamingResult = exportPageStream(projectId, pageId, userId, request);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        streamingResult.writer().write(outputStream);
        return new DocumentExportResult(streamingResult.fileName(), streamingResult.contentType(), outputStream.toByteArray());
    }

    public StreamingDocumentExportResult exportPageStream(String projectId,
                                                          String pageId,
                                                          String userId,
                                                          DocumentExportDto.PageExportRequest request) throws IOException {
        DocumentExportDto.ExportFormat format = requireSupportedPageFormat(request);
        Project project = projectRepository.findWithAssociationsById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        workspaceAccessService.requireWorkspaceAccess(project.getLibrary().getWorkspaceId(), userId);

        Page page = project.getPages().stream()
                .filter(candidate -> Objects.equals(candidate.getId(), pageId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Page not found: " + pageId));

        if (format == DocumentExportDto.ExportFormat.PAGE_XML) {
            return exportPageXmlStream(page, request == null ? null : request.targetPageXmlVersion());
        }

        ExportOptions options = ExportOptions.fromPageRequest(request);
        List<ExportPage> exportPages = preparePages(project, List.of(page), options.pdfImageVariantSelection());
        return renderExportStream(project, exportPages, options, true);
    }

    public StreamingDocumentExportResult exportPageXmlStream(String projectId,
                                                             String pageId,
                                                             String userId,
                                                             String targetPageXmlVersion) throws IOException {
        Project project = projectRepository.findWithAssociationsById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        workspaceAccessService.requireWorkspaceAccess(project.getLibrary().getWorkspaceId(), userId);

        Page page = project.getPages().stream()
                .filter(candidate -> Objects.equals(candidate.getId(), pageId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Page not found: " + pageId));

        return exportPageXmlStream(page, targetPageXmlVersion);
    }

    private StreamingDocumentExportResult exportPageXmlStream(Page page, String targetPageXmlVersion) throws IOException {
        PageXml pageXml = pageXmlRepository.findByPage_Id(page.getId())
                .filter(xml -> xml.getSchema() == XmlSchema.PAGE_XML)
                .orElse(null);
        if (pageXml == null) {
            throw new IllegalArgumentException("No PAGE XML file found for page: " + page.getId());
        }

        Path xmlPath = resolveUploadPath(pageXml.getFilePath());
        if (xmlPath == null || !Files.exists(xmlPath)) {
            throw new IllegalArgumentException("PAGE XML file not found for page: " + page.getId());
        }

        String normalizedTarget = pageXmlConversionService.normalizeTargetVersion(targetPageXmlVersion);
        String fileName = DocumentExportFileNames.sanitizeFileName(
                pageXml.getFileName(),
                DocumentExportFileNames.sanitizeFileName(page.getName(), "page") + ".xml"
        );
        String contentType = pageXml.getMimeType() == null || pageXml.getMimeType().isBlank()
                ? DocumentExportDto.ExportFormat.PAGE_XML.getContentType()
                : pageXml.getMimeType();
        return new StreamingDocumentExportResult(
                fileName,
                contentType,
                outputStream -> pageXmlConversionService.writeFileToVersion(xmlPath, normalizedTarget, outputStream)
        );
    }

    public DocumentExportResult exportProject(String workspaceId,
                                              String projectId,
                                              String userId,
                                              DocumentExportDto.ProjectExportRequest request) throws IOException {
        StreamingDocumentExportResult streamingResult = exportProjectStream(workspaceId, projectId, userId, request);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        streamingResult.writer().write(outputStream);
        return new DocumentExportResult(streamingResult.fileName(), streamingResult.contentType(), outputStream.toByteArray());
    }

    public StreamingDocumentExportResult exportProjectStream(String workspaceId,
                                                             String projectId,
                                                             String userId,
                                                             DocumentExportDto.ProjectExportRequest request) throws IOException {
        DocumentExportDto.ExportFormat format = requireSupportedProjectFormat(request);
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        Project project = projectRepository.findWithAssociationsById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        if (!workspaceId.equals(project.getLibrary().getWorkspaceId())) {
            throw new IllegalArgumentException("Project does not belong to workspace");
        }

        List<Page> selectedPages = resolvePages(project, request.pageIds());
        ExportOptions options = ExportOptions.fromProjectRequest(request);
        List<ExportPage> exportPages = preparePages(project, selectedPages, options.pdfImageVariantSelection());
        return renderExportStream(project, exportPages, options, false);
    }

    public List<EmbeddedProjectOutput> exportEmbeddedProjectOutputs(Project project,
                                                                    List<Page> pages,
                                                                    List<DocumentExportDto.EmbeddedProjectOutputRequest> requests) throws IOException {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }

        List<ExportPage> defaultExportPages = null;
        Map<String, EmbeddedProjectOutput> outputsByPath = new LinkedHashMap<>();

        for (DocumentExportDto.EmbeddedProjectOutputRequest request : requests) {
            if (request == null || request.format() == null) {
                continue;
            }
            if (!request.format().supportsProjectPackageEmbedding()) {
                throw new IllegalArgumentException("Unsupported embedded project output format: " + request.format());
            }

            ExportOptions options = ExportOptions.fromEmbeddedRequest(request);
            List<ExportPage> exportPages;
            if (options.pdfImageVariantSelection() != null) {
                exportPages = preparePages(project, pages, options.pdfImageVariantSelection());
            } else {
                if (defaultExportPages == null) {
                    defaultExportPages = preparePages(project, pages, null);
                }
                exportPages = defaultExportPages;
            }
            StreamingDocumentExportResult export = renderExportStream(project, exportPages, options, false);
            String archivePath = "exports/" + export.fileName();
            if (outputsByPath.containsKey(archivePath)) {
                continue;
            }

            Path tempFile = Files.createTempFile("larex-embedded-export-", DocumentExportFileNames.fileExtension(export.fileName()));
            try {
                try (OutputStream outputStream = Files.newOutputStream(tempFile)) {
                    export.writer().write(outputStream);
                }
            } catch (IOException | RuntimeException e) {
                Files.deleteIfExists(tempFile);
                throw e;
            }
            outputsByPath.put(archivePath, new EmbeddedProjectOutput(archivePath, tempFile, Files.size(tempFile)));
        }

        return List.copyOf(outputsByPath.values());
    }

    private DocumentExportDto.ExportFormat requireSupportedPageFormat(DocumentExportDto.PageExportRequest request) {
        DocumentExportDto.ExportFormat format = request == null ? null : request.format();
        if (format == null || !format.supportsPageExportEndpoint()) {
            throw new IllegalArgumentException("Unsupported page export format: " + format);
        }
        return format;
    }

    private DocumentExportDto.ExportFormat requireSupportedProjectFormat(DocumentExportDto.ProjectExportRequest request) {
        DocumentExportDto.ExportFormat format = request == null ? null : request.format();
        if (format == null || !format.supportsProjectExportEndpoint()) {
            throw new IllegalArgumentException("Unsupported project export format: " + format);
        }
        return format;
    }

    private List<Page> resolvePages(Project project, List<String> selectedPageIds) {
        List<Page> sortedPages = project.getPages().stream()
                .sorted(pageOrderService.projectOrderComparator())
                .toList();

        if (selectedPageIds == null || selectedPageIds.isEmpty()) {
            return sortedPages;
        }

        Set<String> selectedIds = new HashSet<>(selectedPageIds);
        return sortedPages.stream()
                .filter(page -> selectedIds.contains(page.getId()))
                .toList();
    }

    private List<ExportPage> preparePages(Project project,
                                          List<Page> pages,
                                          DocumentExportDto.ImageVariantSelection imageVariantSelection) throws IOException {
        int gtIndex = project.getEffectiveDefaultGtIndex();
        List<ExportPage> exportPages = new ArrayList<>();
        Map<String, PageXml> headsByPageId = pageXmlRepository.findByPage_IdIn(
                        pages.stream().map(Page::getId).toList()
                ).stream()
                .collect(Collectors.toMap(xml -> xml.getPage().getId(), Function.identity()));

        for (Page page : pages) {
            PageXml primaryXml = headsByPageId.get(page.getId());
            PageImage primaryImage = resolvePrimaryImage(page, imageVariantSelection);
            if (imageVariantSelection != null && primaryImage == null) {
                continue;
            }
            Path imagePath = primaryImage == null ? null : resolveUploadPath(primaryImage.getFilePath());
            PageDto pageDto = primaryXml == null
                    ? emptyPageDto(primaryImage, imagePath)
                    : annotationProcessingService.parseXmlToAnnotation(primaryXml.getId());
            exportPages.add(new ExportPage(
                    page,
                    primaryXml,
                    pageDto,
                    ExportRegionExtractor.extractRegions(pageDto, gtIndex),
                    primaryImage,
                    imagePath
            ));
        }

        if (!pages.isEmpty() && exportPages.isEmpty() && imageVariantSelection != null) {
            throw new IllegalArgumentException(
                    "No pages have the selected image variant; choose another variant or enable fallback");
        }

        return exportPages;
    }

    private PageDto emptyPageDto(PageImage image, Path imagePath) {
        int imageWidth = 1;
        int imageHeight = 1;
        if (imagePath != null && Files.exists(imagePath)) {
            try {
                var bufferedImage = ImageIO.read(imagePath.toFile());
                if (bufferedImage != null) {
                    imageWidth = Math.max(1, bufferedImage.getWidth());
                    imageHeight = Math.max(1, bufferedImage.getHeight());
                }
            } catch (IOException ignored) {
                // The image writer will handle an unreadable image; text-based exports remain empty.
            }
        }

        return new PageDto(
                image == null ? null : image.getFileName(),
                imageWidth,
                imageHeight,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                "2019-07-15"
        );
    }

    private StreamingDocumentExportResult renderExportStream(Project project,
                                                             List<ExportPage> pages,
                                                             ExportOptions options,
                                                             boolean pageScope) throws IOException {
        if (options.format() == DocumentExportDto.ExportFormat.CSV || options.format() == DocumentExportDto.ExportFormat.XLSX) {
            if (pageScope) {
                throw new IllegalArgumentException("Spreadsheet export is only available for projects");
            }
            return spreadsheetExportWriter.render(project, pages, options.format(), options.spreadsheetProfiles());
        }

        String baseName = pages.size() == 1
                ? DocumentExportFileNames.sanitizeFileName(pages.get(0).page().getName(), "page")
                : DocumentExportFileNames.sanitizeFileName(project.getName(), "project");

        return switch (options.format()) {
            case TXT -> textDocumentExportWriter.renderText(
                    baseName,
                    pages,
                    options.includePageDelimiters(),
                    options.textLevel(),
                    options.textVariantIndex()
            );
            case DOCX -> textDocumentExportWriter.renderDocx(baseName, project, pages, options.docxOptions(), pageScope);
            case TEI -> textDocumentExportWriter.renderTei(baseName, project, pages, options.teiProfile());
            case PDF -> textDocumentExportWriter.renderPdf(baseName, project, pages, options.pdfProfile());
            case ALTO_XML -> altoExportWriter.render(project, pages);
            case PAGE_XML -> throw new IllegalArgumentException("PAGE XML export is only supported on the legacy page endpoint");
            case CSV, XLSX -> throw new IllegalStateException("Spreadsheet formats handled above");
        };
    }

    private PageImage resolvePrimaryImage(Page page,
                                          DocumentExportDto.ImageVariantSelection selection) {
        List<PageImage> sortedImages = page.getImages().stream()
                .sorted(PAGE_IMAGE_COMPARATOR)
                .toList();
        if (sortedImages.isEmpty()) {
            return null;
        }
        if (selection == null) {
            return sortedImages.getFirst();
        }

        String wantedVariant = wantedImageVariant(page.getId(), selection);
        if (wantedVariant != null && !wantedVariant.isBlank()) {
            PageImage matchingImage = sortedImages.stream()
                    .filter(image -> wantedVariant.equals(image.getVariant()))
                    .findFirst()
                    .orElse(null);
            if (matchingImage != null) {
                return matchingImage;
            }
        }
        return Boolean.TRUE.equals(selection.fallbackImage()) ? sortedImages.getFirst() : null;
    }

    private String wantedImageVariant(String pageId,
                                      DocumentExportDto.ImageVariantSelection selection) {
        String mode = selection.mode() == null ? "GLOBAL" : selection.mode().trim().toUpperCase(Locale.ROOT);
        if ("PER_PAGE".equals(mode)) {
            String pageVariant = selection.pageVariants() == null ? null : selection.pageVariants().get(pageId);
            return pageVariant == null ? null : pageVariant.trim();
        }
        return selection.variant() == null ? null : selection.variant().trim();
    }

    private Path resolveUploadPath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        return Paths.get(uploadDir).resolve(relativePath);
    }

    public record DocumentExportResult(
            String fileName,
            String contentType,
            byte[] bytes
    ) {
    }

    public record StreamingDocumentExportResult(
            String fileName,
            String contentType,
            ExportWriter writer
    ) {
    }

    @FunctionalInterface
    public interface ExportWriter {
        void write(OutputStream outputStream) throws IOException;
    }

    public record EmbeddedProjectOutput(
            String archivePath,
            Path absolutePath,
            long contentLength
    ) {
    }

    private record ExportOptions(
            DocumentExportDto.ExportFormat format,
            boolean includePageDelimiters,
            DocumentExportDto.TextLevel textLevel,
            int textVariantIndex,
            DocumentExportDto.PdfProfile pdfProfile,
            DocumentExportDto.TeiProfile teiProfile,
            List<DocumentExportDto.SpreadsheetProfile> spreadsheetProfiles,
            ResolvedDocxOptions docxOptions,
            DocumentExportDto.ImageVariantSelection imageVariantSelection
    ) {
        private DocumentExportDto.ImageVariantSelection pdfImageVariantSelection() {
            return format == DocumentExportDto.ExportFormat.PDF ? imageVariantSelection : null;
        }

        private static ExportOptions fromPageRequest(DocumentExportDto.PageExportRequest request) {
            return new ExportOptions(
                    request.format(),
                    Boolean.TRUE.equals(request.includePageDelimiters()),
                    request.textLevel() == null ? DocumentExportDto.TextLevel.PAGE : request.textLevel(),
                    request.textVariantIndex() == null ? 0 : Math.max(0, request.textVariantIndex()),
                    request.pdfProfile(),
                    request.teiProfile(),
                    request.spreadsheetProfiles(),
                    ResolvedDocxOptions.from(request.docxOptions(), true),
                    request.imageVariantSelection()
            );
        }

        private static ExportOptions fromProjectRequest(DocumentExportDto.ProjectExportRequest request) {
            return new ExportOptions(
                    request.format(),
                    Boolean.TRUE.equals(request.includePageDelimiters()),
                    request.textLevel() == null ? DocumentExportDto.TextLevel.PAGE : request.textLevel(),
                    request.textVariantIndex() == null ? 0 : Math.max(0, request.textVariantIndex()),
                    request.pdfProfile(),
                    request.teiProfile(),
                    request.spreadsheetProfiles(),
                    ResolvedDocxOptions.from(request.docxOptions(), false),
                    request.imageVariantSelection()
            );
        }

        private static ExportOptions fromEmbeddedRequest(DocumentExportDto.EmbeddedProjectOutputRequest request) {
            return new ExportOptions(
                    request.format(),
                    Boolean.TRUE.equals(request.includePageDelimiters()),
                    request.textLevel() == null ? DocumentExportDto.TextLevel.PAGE : request.textLevel(),
                    request.textVariantIndex() == null ? 0 : Math.max(0, request.textVariantIndex()),
                    request.pdfProfile(),
                    request.teiProfile(),
                    request.spreadsheetProfiles(),
                    ResolvedDocxOptions.from(request.docxOptions(), false),
                    request.imageVariantSelection()
            );
        }
    }
}

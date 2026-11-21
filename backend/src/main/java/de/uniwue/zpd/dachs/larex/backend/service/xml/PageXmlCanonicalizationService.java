package de.uniwue.zpd.dachs.larex.backend.service.xml;

import com.maxnth.page4j.formats.SourceImage;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageImageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.service.storage.HierarchicalFileStorageService;
import de.uniwue.zpd.dachs.larex.backend.service.version.PageXmlVersionService;
import de.uniwue.zpd.dachs.larex.backend.util.ImageFileUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Service
public class PageXmlCanonicalizationService {

    public static final String ALTO_SOURCE = "ALTO";

    private final PageXmlConversionService pageXmlConversionService;
    private final PageXmlVersionService pageXmlVersionService;
    private final PageXmlRepository pageXmlRepository;
    private final HierarchicalFileStorageService hierarchicalFileStorageService;
    private final PageImageRepository pageImageRepository;

    public PageXmlCanonicalizationService(PageXmlConversionService pageXmlConversionService,
                                          PageXmlVersionService pageXmlVersionService,
                                          PageXmlRepository pageXmlRepository,
                                          HierarchicalFileStorageService hierarchicalFileStorageService,
                                          PageImageRepository pageImageRepository) {
        this.pageXmlConversionService = pageXmlConversionService;
        this.pageXmlVersionService = pageXmlVersionService;
        this.pageXmlRepository = pageXmlRepository;
        this.hierarchicalFileStorageService = hierarchicalFileStorageService;
        this.pageImageRepository = pageImageRepository;
    }

    public Function<String, Optional<SourceImage>> storedPageImage(String pageId) {
        return imageFilename -> {
            if (pageId == null) {
                return Optional.empty();
            }
            List<PageImage> images = ImageFileUtils.sortForDisplay(pageImageRepository.findByPageId(pageId), null);
            return SourceImages.matching(images, PageImage::getFileName, imageFilename)
                    .flatMap(image -> SourceImages.read(hierarchicalFileStorageService.resolveUploadPath(image.getFilePath())));
        };
    }

    public CanonicalizationOutcome canonicalizeAtIngest(PageXml pageXml, String userId, String sourceContext) throws IOException {
        return canonicalizeAtIngest(pageXml, userId, sourceContext, true);
    }

    public CanonicalizationOutcome canonicalizeAtIngest(PageXml pageXml,
                                                        String userId,
                                                        String sourceContext,
                                                        boolean createPreConversionSnapshot) throws IOException {
        if (pageXml == null || pageXml.getSchema() != XmlSchema.PAGE_XML) {
            return new CanonicalizationOutcome(null, null, false, false, List.of());
        }

        if (pageXml.getId() == null) {
            throw new IllegalArgumentException("PAGE XML entity must be persisted before canonicalization");
        }

        Path xmlPath = hierarchicalFileStorageService.resolveUploadPath(pageXml.getFilePath());
        String targetVersion = PageXmlConversionService.PRIMARY_PAGE_VERSION;
        if (AltoToPageXml.isAlto(xmlPath)) {
            String pageId = pageXml.getPage() == null ? null : pageXml.getPage().getId();
            List<String> messages = AltoToPageXml.convertInPlace(xmlPath, storedPageImage(pageId));
            PageXmlPrettyPrinter.prettyPrint(xmlPath);
            pageXml.setSchemaVersion(targetVersion);
            pageXml.setFileSize(Files.size(xmlPath));
            pageXmlRepository.save(pageXml);
            return new CanonicalizationOutcome(ALTO_SOURCE, targetVersion, true, false, messages);
        }

        String sourceVersion = pageXmlConversionService.detectPageVersion(xmlPath);
        boolean converted = false;
        boolean snapshotCreated = false;
        List<String> messages = List.of();

        if (!targetVersion.equals(sourceVersion)) {
            if (createPreConversionSnapshot) {
                pageXmlVersionService.createVersion(pageXml.getId(), normalizeUserId(userId), snapshotComment(sourceVersion, sourceContext));
                snapshotCreated = true;
            }
            PageXmlConversionService.ConversionOutcome conversionOutcome =
                    pageXmlConversionService.convertFileInPlace(xmlPath, targetVersion);
            converted = conversionOutcome.converted();
            messages = conversionOutcome.messages();
        }

        PageXmlPrettyPrinter.prettyPrint(xmlPath);

        pageXml.setSchemaVersion(targetVersion);
        pageXml.setFileSize(Files.size(xmlPath));
        pageXmlRepository.save(pageXml);

        return new CanonicalizationOutcome(sourceVersion, targetVersion, converted, snapshotCreated, messages);
    }

    private String normalizeUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return "system";
        }
        return userId.trim();
    }

    private String snapshotComment(String sourceVersion, String sourceContext) {
        String context = (sourceContext == null || sourceContext.isBlank()) ? "ingest" : sourceContext.trim();
        return "Auto-snapshot before PAGE XML canonicalization from " + sourceVersion
                + " to " + PageXmlConversionService.PRIMARY_PAGE_VERSION + " (" + context + ")";
    }

    public record CanonicalizationOutcome(
            String sourceVersion,
            String targetVersion,
            boolean converted,
            boolean snapshotCreated,
            List<String> messages
    ) {
    }
}

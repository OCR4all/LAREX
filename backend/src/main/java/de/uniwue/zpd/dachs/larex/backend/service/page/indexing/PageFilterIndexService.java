package de.uniwue.zpd.dachs.larex.backend.service.page.indexing;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.GlyphDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.layout.AlternativeImageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.layout.RelationDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.layout.RelationsDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.geometry.PolygonDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.readingorder.ReadingOrderDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.region.RegionDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.metadata.LabelDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.metadata.LabelsDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.GraphemeElementDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.GraphemesDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.TextContentVariantDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.TextLineDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.WordDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageConfidenceIndex;
import de.uniwue.zpd.dachs.larex.backend.entity.PageLabelIndex;
import de.uniwue.zpd.dachs.larex.backend.entity.PageTextContent;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXmlAttributeIndex;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageConfidenceIndexRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageLabelIndexRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageTextContentRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlAttributeIndexRepository;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.application.AnnotationProcessingService;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.parser.PageXmlParseResult;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.parser.PageXmlPresenceIndex;
import de.uniwue.zpd.dachs.larex.backend.service.search.SearchLexiconService;
import de.uniwue.zpd.dachs.larex.backend.util.TextIndexDefaultsUtil;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for managing the search index tables (page_text_content, page_label_index, page_confidence_index).
 * Provides methods to index pages and filter by various criteria.
 */
@Service
public class PageFilterIndexService {

    public record XmlAttributePredicate(
            String elementName,
            String attributeName,
            String operator,
            String value,
            boolean negated) {
        public XmlAttributePredicate(String elementName, String attributeName, String operator, String value) {
            this(elementName, attributeName, operator, value, false);
        }
    }

    private record FilterGroup(Set<String> pageIds, boolean negated) {
        boolean matches(String pageId) {
            return pageIds.contains(pageId) != negated;
        }
    }

    private static final Logger log = LoggerFactory.getLogger(PageFilterIndexService.class);
    private static final int REBUILD_BATCH_SIZE = 50;
    private static final int TEXT_DIFF_BATCH_SIZE = 250;

    private static final String REGION_KIND_TEXT = "TextRegion";
    private static final double DEFAULT_CONFIDENCE_MIN = 0.0;
    private static final double DEFAULT_CONFIDENCE_MAX = 1.0;
    private static final Pattern CUSTOM_BLOCK_PATTERN = Pattern.compile("([\\w-]+)\\s*\\{([^}]*)}");

    private final PageRepository pageRepository;
    private final PageXmlRepository pageXmlRepository;
    private final PageTextContentRepository textContentRepository;
    private final PageLabelIndexRepository labelIndexRepository;
    private final PageConfidenceIndexRepository confidenceIndexRepository;
    private final PageXmlAttributeIndexRepository xmlAttributeIndexRepository;
    private final AnnotationProcessingService annotationProcessingService;
    private final SearchLexiconService searchLexiconService;
    private final String uploadDir;
    private final TransactionTemplate rebuildTransaction;
    private final Cache<TextMismatchKey, Boolean> textMismatchCache = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterAccess(Duration.ofMinutes(30))
            .build();

    public PageFilterIndexService(
            PageRepository pageRepository,
            PageXmlRepository pageXmlRepository,
            PageTextContentRepository textContentRepository,
            PageLabelIndexRepository labelIndexRepository,
            PageConfidenceIndexRepository confidenceIndexRepository,
            PageXmlAttributeIndexRepository xmlAttributeIndexRepository,
            AnnotationProcessingService annotationProcessingService,
            SearchLexiconService searchLexiconService,
            @Value("${file.upload-dir}") String uploadDir,
            PlatformTransactionManager transactionManager) {
        this.pageRepository = pageRepository;
        this.pageXmlRepository = pageXmlRepository;
        this.textContentRepository = textContentRepository;
        this.labelIndexRepository = labelIndexRepository;
        this.confidenceIndexRepository = confidenceIndexRepository;
        this.xmlAttributeIndexRepository = xmlAttributeIndexRepository;
        this.annotationProcessingService = annotationProcessingService;
        this.searchLexiconService = searchLexiconService;
        this.uploadDir = uploadDir;
        this.rebuildTransaction = new TransactionTemplate(transactionManager);
    }

    // ============================================================================
    // Indexing Methods
    // ============================================================================

    /**
     * Index a page from a PageDto (called after annotation save).
     */
    @Transactional
    public void indexPage(Page page, PageDto pageDto) {
        if (page == null || pageDto == null) {
            return;
        }

        PageXmlPresenceIndex presenceIndex = pageXmlRepository.findByPage_Id(page.getId())
            .filter(xml -> xml.getSchema() == de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema.PAGE_XML)
            .map(xml -> PageXmlPresenceIndex.fromPath(Paths.get(uploadDir, xml.getFilePath())))
            .orElseGet(PageXmlPresenceIndex::empty);
        indexPage(page, pageDto, presenceIndex);
    }

    private void indexPage(Page page, PageDto pageDto, PageXmlPresenceIndex presenceIndex) {

        log.debug("Indexing page {} with {} regions", page.getId(), pageDto.getRegionCount());

        // Clear existing index data for this page
        textContentRepository.deleteByPageId(page.getId());
        labelIndexRepository.deleteByPageId(page.getId());
        confidenceIndexRepository.deleteByPageId(page.getId());
        xmlAttributeIndexRepository.deleteByPageId(page.getId());

        List<PageTextContent> textContents = new ArrayList<>();
        List<PageLabelIndex> labelIndices = new ArrayList<>();
        List<PageConfidenceIndex> confidenceIndices = new ArrayList<>();
        List<PageXmlAttributeIndex> xmlAttributeIndices = new ArrayList<>();

        Set<String> labelDedupKeys = new HashSet<>();
        Set<String> confidenceDedupKeys = new HashSet<>();

        // Page-level index entries
        addConfidenceEntry(
            page,
            PageConfidenceIndex.ElementType.PAGE,
            firstNonBlank(pageDto.pcGtsId(), page.getId()),
            pageDto.confidence(),
            confidenceIndices,
            confidenceDedupKeys
        );
        addCoordsConfidence(page, "page:border", pageDto.border(), confidenceIndices, confidenceDedupKeys);
        addCoordsConfidence(page, "page:printSpace", pageDto.printSpace(), confidenceIndices, confidenceDedupKeys);
        if (pageDto.readingOrder() != null) {
            addConfidenceEntry(
                page,
                PageConfidenceIndex.ElementType.READING_ORDER,
                firstNonBlank(pageDto.readingOrder().root() != null ? pageDto.readingOrder().root().id() : null, "reading-order"),
                pageDto.readingOrder().confidence(),
                confidenceIndices,
                confidenceDedupKeys
            );
        }
        indexCommentValue(page, pageDto.metadata() != null ? pageDto.metadata().comments() : null, textContents);
        indexAlternativeImageComments(page, pageDto.alternativeImages(), textContents);
        indexLabelsComments(page, pageDto.labels(), textContents);
        indexReadingOrderComments(page, pageDto.readingOrder(), textContents);
        indexRelationsComments(page, pageDto.relations(), textContents);

        if (pageDto.regions() != null) {
            for (RegionDto region : pageDto.regions()) {
                extractFromRegion(
                    page,
                    region,
                    null,
                    textContents,
                    labelIndices,
                    confidenceIndices,
                    labelDedupKeys,
                    confidenceDedupKeys
                );
            }
        }

        for (PageXmlPresenceIndex.AttributeOccurrence occurrence : presenceIndex.attributeOccurrences()) {
            xmlAttributeIndices.add(new PageXmlAttributeIndex(
                page,
                occurrence.elementName(),
                occurrence.attributeName(),
                occurrence.value()
            ));
        }

        if (!textContents.isEmpty()) {
            textContentRepository.saveAll(textContents);
            textContentRepository.refreshSearchFieldsByPageId(page.getId());
            log.debug("Indexed {} text content records for page {}", textContents.size(), page.getId());
        }
        if (!labelIndices.isEmpty()) {
            labelIndexRepository.saveAll(labelIndices);
            log.debug("Indexed {} label records for page {}", labelIndices.size(), page.getId());
        }
        if (!confidenceIndices.isEmpty()) {
            confidenceIndexRepository.saveAll(confidenceIndices);
            log.debug("Indexed {} confidence records for page {}", confidenceIndices.size(), page.getId());
        }
        if (!xmlAttributeIndices.isEmpty()) {
            xmlAttributeIndexRepository.saveAll(xmlAttributeIndices);
            log.debug("Indexed {} PAGE XML attribute records for page {}", xmlAttributeIndices.size(), page.getId());
        }
    }

    /**
     * Clear all filter index rows for a page.
     */
    @Transactional
    public void clearPageIndex(String pageId) {
        if (pageId == null || pageId.isBlank()) {
            return;
        }

        textContentRepository.deleteByPageId(pageId);
        labelIndexRepository.deleteByPageId(pageId);
        confidenceIndexRepository.deleteByPageId(pageId);
        xmlAttributeIndexRepository.deleteByPageId(pageId);
    }

    /**
     * Index a page by parsing its XML file.
     */
    @Transactional
    public void indexPageFromXml(Page page) {
        if (page == null) {
            log.warn("indexPageFromXml called with null page");
            return;
        }

        log.debug("Starting indexPageFromXml for page: {} (id: {})", page.getName(), page.getId());

        Optional<PageXml> headXml = pageXmlRepository.findByPage_Id(page.getId());
        if (headXml.isEmpty()) {
            log.warn("No XML found for page {} - page not indexed", page.getId());
            return;
        }

        PageXml xml = headXml.get();
        try {
            log.debug("Attempting to parse PageXml {} (schema: {})", xml.getId(), xml.getSchema());
            PageXmlParseResult parseResult = annotationProcessingService.parseXmlToAnnotationWithPresence(xml.getId());
            indexPage(page, parseResult.pageDto(), parseResult.presenceIndex());
            log.info("Successfully indexed page {} from PageXml {}", page.getId(), xml.getId());
        } catch (IOException e) {
            log.warn("Failed to parse XML {} for page {}: {}", xml.getId(), page.getId(), e.getMessage());
        } catch (UnsupportedOperationException e) {
            log.debug("Skipping unsupported XML schema {} for page {}", xml.getSchema(), page.getId());
        }
    }

    @Async
    public void rebuildProjectIndex(String projectId) {
        log.info("Starting index rebuild for project {}", projectId);
        RebuildCounts counts = rebuildPages(pageRepository.findIdsByProjectId(projectId));
        searchLexiconService.rebuildProjectLexicon(projectId);
        log.info("Index rebuild complete for project {}. Success: {}, Failed: {}",
                projectId, counts.success(), counts.failed());
    }

    @Async
    public void rebuildGlobalIndex() {
        log.info("Starting global index rebuild");
        int successCount = 0;
        int failCount = 0;
        for (String projectId : pageRepository.findDistinctProjectIds()) {
            RebuildCounts counts = rebuildPages(pageRepository.findIdsByProjectId(projectId));
            searchLexiconService.rebuildProjectLexicon(projectId);
            successCount += counts.success();
            failCount += counts.failed();
        }
        log.info("Global index rebuild complete. Success: {}, Failed: {}", successCount, failCount);
    }

    /**
     * Indexes pages in short transactions of {@link #REBUILD_BATCH_SIZE} pages, so large projects
     * do not keep every page and its parsed annotation in one persistence context.
     */
    private RebuildCounts rebuildPages(List<String> pageIds) {
        int successCount = 0;
        int failCount = 0;
        for (int from = 0; from < pageIds.size(); from += REBUILD_BATCH_SIZE) {
            List<String> batch = pageIds.subList(from, Math.min(from + REBUILD_BATCH_SIZE, pageIds.size()));
            try {
                RebuildCounts counts = rebuildTransaction.execute(status -> {
                    int success = 0;
                    int failed = 0;
                    for (Page page : pageRepository.findAllById(batch)) {
                        try {
                            indexPageFromXml(page);
                            success++;
                        } catch (Exception e) {
                            log.error("Failed to index page {}: {}", page.getId(), e.getMessage());
                            failed++;
                        }
                    }
                    return new RebuildCounts(success, failed);
                });
                if (counts != null) {
                    successCount += counts.success();
                    failCount += counts.failed();
                }
            } catch (RuntimeException e) {
                log.error("Failed to index a batch of {} pages: {}", batch.size(), e.getMessage());
                failCount += batch.size();
            }
        }
        return new RebuildCounts(successCount, failCount);
    }

    private record RebuildCounts(int success, int failed) {
    }

    // ============================================================================
    // Filtering Methods
    // ============================================================================

    /**
     * Filter pages by multiple criteria with global AND/OR logic.
     */
    public Set<String> filterPages(
            String projectId,
            String textContent,
            List<String> labelIds,
            String labelOperator,
            List<String> tags,
            String tagOperator,
            Double confidenceMin,
            Double confidenceMax,
            List<String> confidenceElementTypes,
            Boolean hasComments) {

        return filterPages(
            projectId,
            textContent,
            labelIds,
            labelOperator,
            tags,
            tagOperator,
            confidenceMin,
            confidenceMax,
            confidenceElementTypes,
            hasComments,
            null,
            null,
            null,
            false,
            Collections.emptySet(),
            Collections.emptyList(),
            labelOperator
        );
    }

    /**
     * Unified builder evaluation. Every non-empty criterion is one group participating in the
     * same global AND/OR operation; each XML attribute predicate is its own group.
     */
    public Set<String> filterPages(
            String projectId,
            String textContent,
            List<String> labelIds,
            String labelOperator,
            List<String> tags,
            String tagOperator,
            Double confidenceMin,
            Double confidenceMax,
            List<String> confidenceElementTypes,
            Boolean hasComments,
            String commentText,
            List<String> workflowStates,
            String annotationPresence,
            Boolean onlyWithOpenSubtasks,
            Set<String> openSubtaskPageIds,
            List<XmlAttributePredicate> xmlAttributeFilters,
            String filterOperator) {
        return filterPages(
            projectId,
            textContent,
            labelIds,
            labelOperator,
            tags,
            tagOperator,
            confidenceMin,
            confidenceMax,
            confidenceElementTypes,
            hasComments,
            commentText,
            workflowStates,
            null,
            annotationPresence,
            onlyWithOpenSubtasks,
            openSubtaskPageIds,
            xmlAttributeFilters,
            false,
            null,
            Set.of(),
            filterOperator
        );
    }

    public Set<String> filterPages(
            String projectId,
            String textContent,
            List<String> labelIds,
            String labelOperator,
            List<String> tags,
            String tagOperator,
            Double confidenceMin,
            Double confidenceMax,
            List<String> confidenceElementTypes,
            Boolean hasComments,
            String commentText,
            List<String> workflowStates,
            Boolean changesRequested,
            String annotationPresence,
            Boolean onlyWithOpenSubtasks,
            Set<String> openSubtaskPageIds,
            List<XmlAttributePredicate> xmlAttributeFilters,
            boolean hasTextMismatch,
            List<Page> accessiblePages,
            Set<String> negatedFilters,
            String filterOperator) {

        List<FilterGroup> activeFilterGroups = new ArrayList<>();

        if (textContent != null && !textContent.trim().isEmpty()) {
            activeFilterGroups.add(new FilterGroup(new HashSet<>(
                textContentRepository.findPageIdsByProjectIdAndTextContentContaining(projectId, textContent.trim())
            ), negatedFilters.contains("textContent")));
        }

        if (labelIds != null && !labelIds.isEmpty()) {
            Set<String> labelMatches;
            if ("and".equalsIgnoreCase(labelOperator)) {
                labelMatches = new HashSet<>(
                    labelIndexRepository.findPageIdsByProjectIdAndAllLabelIds(projectId, labelIds, labelIds.size())
                );
            } else {
                labelMatches = new HashSet<>(
                    labelIndexRepository.findPageIdsByProjectIdAndLabelIdsIn(projectId, labelIds)
                );
            }
            activeFilterGroups.add(new FilterGroup(labelMatches, negatedFilters.contains("labels")));
        }

        if (tags != null && !tags.isEmpty()) {
            activeFilterGroups.add(new FilterGroup(findTagMatches(projectId, tags, tagOperator), negatedFilters.contains("tags")));
        }

        boolean hasConfidenceFilter = confidenceMin != null
            || confidenceMax != null
            || (confidenceElementTypes != null && !confidenceElementTypes.isEmpty());

        if (hasConfidenceFilter) {
            double min = clamp01(confidenceMin != null ? confidenceMin : DEFAULT_CONFIDENCE_MIN);
            double max = clamp01(confidenceMax != null ? confidenceMax : DEFAULT_CONFIDENCE_MAX);
            if (min > max) {
                double tmp = min;
                min = max;
                max = tmp;
            }

            List<PageConfidenceIndex.ElementType> elementTypes = parseConfidenceElementTypes(confidenceElementTypes);
            Set<String> confidenceMatches = elementTypes.isEmpty()
                ? Collections.emptySet()
                : new HashSet<>(
                    confidenceIndexRepository.findPageIdsByProjectIdAndConfidenceRangeAndElementTypes(
                        projectId,
                        min,
                        max,
                        elementTypes
                    )
                );
            activeFilterGroups.add(new FilterGroup(confidenceMatches, negatedFilters.contains("confidence")));
        }

        if (commentText != null && !commentText.trim().isEmpty()) {
            activeFilterGroups.add(new FilterGroup(new HashSet<>(
                textContentRepository.findPageIdsByProjectIdAndCommentContaining(projectId, commentText.trim())
            ), negatedFilters.contains("comments")));
        } else if (Boolean.TRUE.equals(hasComments)) {
            activeFilterGroups.add(new FilterGroup(new HashSet<>(
                textContentRepository.findPageIdsByProjectIdWithComments(projectId)
            ), negatedFilters.contains("comments")));
        }

        Set<Page.WorkflowState> parsedWorkflowStates = parseWorkflowStates(workflowStates);
        if (!parsedWorkflowStates.isEmpty()) {
            activeFilterGroups.add(new FilterGroup(new HashSet<>(
                pageRepository.findIdsByProjectIdAndWorkflowStateIn(projectId, parsedWorkflowStates)
            ), negatedFilters.contains("workflowStates")));
        }

        if (Boolean.TRUE.equals(changesRequested)) {
            activeFilterGroups.add(new FilterGroup(
                new HashSet<>(pageRepository.findIdsByProjectIdAndChangesRequestedTrue(projectId)),
                negatedFilters.contains("changesRequested")
            ));
        }

        boolean withoutXml = "without_xml".equals(annotationPresence);
        if (withoutXml || "with_xml".equals(annotationPresence)) {
            activeFilterGroups.add(new FilterGroup(new HashSet<>(pageXmlRepository.findPageIdsByProjectId(projectId)),
                withoutXml != negatedFilters.contains("annotationPresence")));
        }

        if (Boolean.TRUE.equals(onlyWithOpenSubtasks)) {
            activeFilterGroups.add(new FilterGroup(openSubtaskPageIds == null
                ? Collections.emptySet()
                : openSubtaskPageIds, negatedFilters.contains("openSubtasks")));
        }

        if (xmlAttributeFilters != null) {
            for (XmlAttributePredicate predicate : xmlAttributeFilters) {
                FilterGroup group = findXmlAttributeGroup(projectId, predicate);
                if (group != null) {
                    activeFilterGroups.add(group);
                }
            }
        }

        boolean useOr = "or".equalsIgnoreCase(filterOperator);
        if (hasTextMismatch) {
            activeFilterGroups.add(new FilterGroup(findTextMismatchPages(accessiblePages, activeFilterGroups, useOr),
                negatedFilters.contains("textMismatch")));
        }

        if (activeFilterGroups.isEmpty()) {
            return allPageIds(projectId, accessiblePages);
        }

        if (useOr) {
            if (activeFilterGroups.stream().anyMatch(FilterGroup::negated)) {
                Set<String> result = allPageIds(projectId, accessiblePages);
                result.removeIf(id -> !matchesFilterGroups(id, activeFilterGroups, true));
                return result;
            }
            Set<String> union = new HashSet<>();
            for (FilterGroup group : activeFilterGroups) {
                union.addAll(group.pageIds());
            }
            return union;
        }

        Set<String> intersection = activeFilterGroups.stream()
            .filter(group -> !group.negated())
            .map(FilterGroup::pageIds)
            .min(Comparator.comparingInt(Set::size))
            .<Set<String>>map(HashSet::new)
            .orElseGet(() -> allPageIds(projectId, accessiblePages));
        for (FilterGroup group : activeFilterGroups) {
            if (group.negated()) {
                intersection.removeAll(group.pageIds());
            } else {
                intersection.retainAll(group.pageIds());
            }
        }
        return intersection;
    }

    private static boolean matchesFilterGroups(String pageId, List<FilterGroup> groups, boolean useOr) {
        return useOr ? groups.stream().anyMatch(group -> group.matches(pageId))
            : groups.stream().allMatch(group -> group.matches(pageId));
    }

    private Set<String> findTextMismatchPages(
            List<Page> pages, List<FilterGroup> otherGroups, boolean useOr) {
        Set<String> matches = new HashSet<>();
        if (pages.isEmpty()) {
            return matches;
        }

        Project project = pages.getFirst().getProject();
        int gtIndex = project.getEffectiveDefaultGtIndex();
        Set<Integer> recognitionIndices = Set.copyOf(project.getDefaultRecognitionIndicesList());
        List<String> candidateIds = pages.stream()
                .map(Page::getId)
                .filter(id -> matchesFilterGroups(id, otherGroups, useOr) != useOr)
                .toList();

        // The full-text index mixes text lines with word and glyph text and omits blank variants.
        for (int from = 0; from < candidateIds.size(); from += TEXT_DIFF_BATCH_SIZE) {
            List<String> batch = candidateIds.subList(from, Math.min(from + TEXT_DIFF_BATCH_SIZE, candidateIds.size()));
            for (PageXml xml : pageXmlRepository.findByPage_IdIn(batch)) {
                if (xml.getSchema() != XmlSchema.PAGE_XML) {
                    continue;
                }
                try {
                    if (hasCachedTextMismatch(xml, gtIndex, recognitionIndices)) {
                        matches.add(xml.getPage().getId());
                    }
                } catch (IOException | UncheckedIOException e) {
                    log.warn("Could not compare text variants in PAGE XML {}: {}", xml.getId(), e.getMessage());
                }
            }
        }
        return matches;
    }

    private boolean hasCachedTextMismatch(PageXml xml, int gtIndex, Set<Integer> recognitionIndices) throws IOException {
        Path path = Paths.get(uploadDir, xml.getFilePath());
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
        var key = new TextMismatchKey(xml.getId(), path, attributes.fileKey(),
                attributes.lastModifiedTime(), attributes.size(), gtIndex, recognitionIndices);
        return textMismatchCache.get(key, ignored -> {
            try {
                // Keep project-wide scans from evicting full annotations used by the editor.
                PageDto annotation = annotationProcessingService.parsePageXmlForScan(xml);
                return hasTextMismatch(annotation.regions(), gtIndex, recognitionIndices);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private record TextMismatchKey(String xmlId, Path path, Object fileKey, FileTime modifiedAt,
                                   long size, int gtIndex, Set<Integer> recognitionIndices) {}

    private boolean hasTextMismatch(List<RegionDto> regions, int gtIndex, Set<Integer> recognitionIndices) {
        if (regions == null) {
            return false;
        }
        for (RegionDto region : regions) {
            if (region == null) {
                continue;
            }
            if (region.textLines() != null && region.textLines().stream()
                    .anyMatch(line -> line != null && hasLineMismatch(line.textContentVariants(), gtIndex, recognitionIndices))) {
                return true;
            }
            if (hasTextMismatch(region.nestedRegions(), gtIndex, recognitionIndices)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasLineMismatch(
            List<TextContentVariantDto> variants, int gtIndex, Set<Integer> recognitionIndices) {
        if (variants == null || variants.isEmpty()) {
            return false;
        }
        String gtText = variants.stream()
                .filter(variant -> variant != null && Objects.equals(variant.index(), gtIndex))
                .map(PageFilterIndexService::comparisonText)
                .findFirst()
                .orElse("");

        for (TextContentVariantDto variant : variants) {
            if (variant == null) {
                continue;
            }
            int index = variant.index() == null || variant.index() < 0
                    ? TextIndexDefaultsUtil.UNINDEXED_RECOGNITION_SENTINEL
                    : variant.index();
            if (index != gtIndex && recognitionIndices.contains(index)
                    && !comparisonText(variant).equals(gtText)) {
                return true;
            }
        }
        return false;
    }

    private static String comparisonText(TextContentVariantDto variant) {
        String unicode = variant.unicode();
        // The PAGE XML mapper also treats whitespace-only text as empty.
        return unicode == null || unicode.isBlank() ? "" : unicode;
    }

    public List<String> getMatchingTextLineIds(String pageId, String textContent) {
        if (textContent == null || textContent.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return textContentRepository.findTextLineIdsByPageIdAndTextContentContaining(pageId, textContent.trim());
    }

    public List<String> getMatchingTextRegionIds(String pageId, String textContent) {
        if (textContent == null || textContent.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return textContentRepository.findRegionIdsByPageIdAndTextContentContaining(pageId, textContent.trim());
    }

    public Map<String, Object> getIndexStats(String projectId) {
        Map<String, Object> stats = new HashMap<>();

        long totalPages = pageRepository.countByProjectId(projectId);

        long indexedTextPages = 0L;
        long indexedLabelPages = 0L;
        long indexedXmlAttributePages = 0L;
        long pageXmlPages = 0L;
        try {
            indexedTextPages = textContentRepository.countIndexedPagesByProjectId(projectId);
        } catch (Exception e) {
            log.warn("Failed to count indexed text content pages: {}", e.getMessage());
        }
        try {
            indexedLabelPages = labelIndexRepository.countIndexedPagesByProjectId(projectId);
        } catch (Exception e) {
            log.warn("Failed to count indexed label pages: {}", e.getMessage());
        }
        try {
            indexedXmlAttributePages = xmlAttributeIndexRepository.countIndexedPagesByProjectId(projectId);
        } catch (Exception e) {
            log.warn("Failed to count indexed PAGE XML attribute pages: {}", e.getMessage());
        }
        try {
            pageXmlPages = pageXmlRepository.countPageXmlByProjectId(projectId);
        } catch (Exception e) {
            log.warn("Failed to count PAGE XML pages: {}", e.getMessage());
        }

        stats.put("totalPages", Long.valueOf(totalPages));
        stats.put("indexedTextContentPages", Long.valueOf(indexedTextPages));
        stats.put("indexedLabelPages", Long.valueOf(indexedLabelPages));
        stats.put("indexedXmlAttributePages", Long.valueOf(indexedXmlAttributePages));
        stats.put("pagesNeedingIndex", Long.valueOf(Math.max(0L, pageXmlPages - indexedXmlAttributePages)));

        return stats;
    }

    public List<Map<String, Object>> getAvailableLabelsWithCounts(String projectId) {
        List<Object[]> results = labelIndexRepository.countPagesByLabelForProject(projectId);
        List<Map<String, Object>> labels = new ArrayList<>();

        for (Object[] row : results) {
            Map<String, Object> label = new HashMap<>();
            label.put("labelId", row[0]);
            label.put("pageCount", row[1]);
            labels.add(label);
        }

        return labels;
    }

    public List<Map<String, Object>> getAvailableXmlAttributesWithCounts(String projectId) {
        List<Map<String, Object>> attributes = new ArrayList<>();
        for (Object[] row : xmlAttributeIndexRepository.findAvailableAttributesByProjectId(projectId)) {
            Map<String, Object> attribute = new HashMap<>();
            attribute.put("elementName", row[0]);
            attribute.put("attributeName", row[1]);
            attribute.put("pageCount", row[2]);
            attributes.add(attribute);
        }
        return attributes;
    }

    // ============================================================================
    // Extraction Helpers
    // ============================================================================

    private void extractFromRegion(
            Page page,
            RegionDto region,
            String parentRegionId,
            List<PageTextContent> textContents,
            List<PageLabelIndex> labelIndices,
            List<PageConfidenceIndex> confidenceIndices,
            Set<String> labelDedupKeys,
            Set<String> confidenceDedupKeys) {
        if (region == null) {
            return;
        }

        String regionId = firstNonBlank(region.id(), parentRegionId, "region");
        String regionKind = region.kind() != null ? region.kind().name() : null;

        addConfidenceEntry(
            page,
            PageConfidenceIndex.ElementType.PAGE,
            regionId,
            region.confidence(),
            confidenceIndices,
            confidenceDedupKeys
        );

        if (regionKind != null && !regionKind.isBlank()) {
            addLabelToken(page, regionBaseToken(regionKind), regionId, PageLabelIndex.ElementType.REGION, labelIndices, labelDedupKeys);

            if (REGION_KIND_TEXT.equals(regionKind)) {
                String textType = blankToNull(region.type());
                if (textType != null) {
                    addLabelToken(page, regionTextTypeToken(textType), regionId, PageLabelIndex.ElementType.REGION, labelIndices, labelDedupKeys);
                }
            } else {
                String subtype = blankToNull(region.type());
                if (subtype == null) {
                    subtype = extractStructureTypeFromCustom(region.custom());
                }
                if (subtype != null) {
                    addLabelToken(page, regionSubtypeToken(regionKind, subtype), regionId, PageLabelIndex.ElementType.REGION, labelIndices, labelDedupKeys);
                }
            }
        }

        addCoordsConfidence(page, regionId, region.coords(), confidenceIndices, confidenceDedupKeys);
        indexCommentValue(page, region.comments(), textContents);
        indexAlternativeImageComments(page, region.alternativeImages(), textContents);
        indexLabelsComments(page, region.labels(), textContents);
        indexTextContentVariants(page, region.textContentVariants(), null, regionId, textContents, confidenceIndices, confidenceDedupKeys, regionId);

        if (region.textLines() != null) {
            for (TextLineDto textLine : region.textLines()) {
                extractFromTextLine(
                    page,
                    textLine,
                    regionId,
                    textContents,
                    labelIndices,
                    confidenceIndices,
                    labelDedupKeys,
                    confidenceDedupKeys
                );
            }
        }

        if (region.nestedRegions() != null) {
            for (RegionDto nested : region.nestedRegions()) {
                extractFromRegion(
                    page,
                    nested,
                    regionId,
                    textContents,
                    labelIndices,
                    confidenceIndices,
                    labelDedupKeys,
                    confidenceDedupKeys
                );
            }
        }
    }

    private void extractFromTextLine(
            Page page,
            TextLineDto textLine,
            String regionId,
            List<PageTextContent> textContents,
            List<PageLabelIndex> labelIndices,
            List<PageConfidenceIndex> confidenceIndices,
            Set<String> labelDedupKeys,
            Set<String> confidenceDedupKeys) {
        if (textLine == null) {
            return;
        }

        String textLineId = firstNonBlank(textLine.id(), "textline");

        addConfidenceEntry(
            page,
            PageConfidenceIndex.ElementType.PAGE,
            textLineId,
            textLine.confidence(),
            confidenceIndices,
            confidenceDedupKeys
        );

        for (Map.Entry<String, Map<String, String>> entry : parseCustomBlocks(textLine.custom()).entrySet()) {
            String customKey = entry.getKey();
            if (customKey == null || customKey.isBlank()) {
                continue;
            }

            addLabelToken(
                page,
                lineCustomPresenceToken(customKey),
                textLineId,
                PageLabelIndex.ElementType.LINE,
                labelIndices,
                labelDedupKeys
            );

            Map<String, String> pairs = entry.getValue();
            if (pairs != null && !pairs.isEmpty()) {
                addLabelToken(
                    page,
                    lineCustomPairsToken(customKey, pairs),
                    textLineId,
                    PageLabelIndex.ElementType.LINE,
                    labelIndices,
                    labelDedupKeys
                );
            }
        }

        addCoordsConfidence(page, textLineId, textLine.coords(), confidenceIndices, confidenceDedupKeys);
        addBaselineConfidence(page, textLineId, textLine.baseline(), confidenceIndices, confidenceDedupKeys);
        indexCommentValue(page, textLine.comments(), textContents);
        indexAlternativeImageComments(page, textLine.alternativeImages(), textContents);
        indexLabelsComments(page, textLine.labels(), textContents);

        indexTextContentVariants(
            page,
            textLine.textContentVariants(),
            textLineId,
            regionId,
            textContents,
            confidenceIndices,
            confidenceDedupKeys,
            textLineId
        );

        if (textLine.words() != null) {
            for (WordDto word : textLine.words()) {
                extractFromWord(
                    page,
                    word,
                    textLineId,
                    regionId,
                    textContents,
                    confidenceIndices,
                    confidenceDedupKeys
                );
            }
        }
    }

    private void extractFromWord(
            Page page,
            WordDto word,
            String textLineId,
            String regionId,
            List<PageTextContent> textContents,
            List<PageConfidenceIndex> confidenceIndices,
            Set<String> confidenceDedupKeys) {
        if (word == null) {
            return;
        }

        String wordId = firstNonBlank(word.id(), textLineId, "word");
        addConfidenceEntry(
            page,
            PageConfidenceIndex.ElementType.PAGE,
            wordId,
            word.confidence(),
            confidenceIndices,
            confidenceDedupKeys
        );
        addCoordsConfidence(page, wordId, word.coords(), confidenceIndices, confidenceDedupKeys);
        indexCommentValue(page, word.comments(), textContents);
        indexAlternativeImageComments(page, word.alternativeImages(), textContents);
        indexLabelsComments(page, word.labels(), textContents);

        indexTextContentVariants(
            page,
            word.textContentVariants(),
            textLineId,
            regionId,
            textContents,
            confidenceIndices,
            confidenceDedupKeys,
            wordId
        );

        if (word.glyphs() != null) {
            for (GlyphDto glyph : word.glyphs()) {
                extractFromGlyph(
                    page,
                    glyph,
                    textLineId,
                    regionId,
                    textContents,
                    confidenceIndices,
                    confidenceDedupKeys
                );
            }
        }
    }

    private void extractFromGlyph(
            Page page,
            GlyphDto glyph,
            String textLineId,
            String regionId,
            List<PageTextContent> textContents,
            List<PageConfidenceIndex> confidenceIndices,
            Set<String> confidenceDedupKeys) {
        if (glyph == null) {
            return;
        }

        String glyphId = firstNonBlank(glyph.id(), textLineId, "glyph");
        addConfidenceEntry(
            page,
            PageConfidenceIndex.ElementType.PAGE,
            glyphId,
            glyph.confidence(),
            confidenceIndices,
            confidenceDedupKeys
        );
        addCoordsConfidence(page, glyphId, glyph.coords(), confidenceIndices, confidenceDedupKeys);
        indexCommentValue(page, glyph.comments(), textContents);
        indexAlternativeImageComments(page, glyph.alternativeImages(), textContents);
        indexLabelsComments(page, glyph.labels(), textContents);
        indexGraphemeComments(page, glyph.graphemes(), textContents);

        indexTextContentVariants(
            page,
            glyph.textContentVariants(),
            textLineId,
            regionId,
            textContents,
            confidenceIndices,
            confidenceDedupKeys,
            glyphId
        );
    }

    private void indexTextContentVariants(
            Page page,
            List<TextContentVariantDto> variants,
            String textLineId,
            String regionId,
            List<PageTextContent> textContents,
            List<PageConfidenceIndex> confidenceIndices,
            Set<String> confidenceDedupKeys,
            String ownerId) {
        if (variants == null || variants.isEmpty()) {
            return;
        }

        for (int i = 0; i < variants.size(); i++) {
            TextContentVariantDto variant = variants.get(i);
            if (variant == null) {
                continue;
            }

            if (variant.unicode() != null && !variant.unicode().isBlank()) {
                textContents.add(new PageTextContent(
                    page,
                    textLineId,
                    regionId,
                    variant.unicode(),
                    variant.index()
                ));
            }

            indexCommentValue(page, variant.comments(), textContents);

            addConfidenceEntry(
                page,
                PageConfidenceIndex.ElementType.TEXTEQUIV,
                textEquivElementId(ownerId, variant.index(), i),
                variant.confidence(),
                confidenceIndices,
                confidenceDedupKeys
            );
        }
    }

    private void addLabelToken(
            Page page,
            String token,
            String elementId,
            PageLabelIndex.ElementType elementType,
            List<PageLabelIndex> labelIndices,
            Set<String> labelDedupKeys) {
        if (token == null || token.isBlank()) {
            return;
        }
        String normalizedElementId = firstNonBlank(elementId, "unknown");
        String dedupKey = elementType.name() + "|" + normalizedElementId + "|" + token;
        if (!labelDedupKeys.add(dedupKey)) {
            return;
        }
        labelIndices.add(new PageLabelIndex(page, token, normalizedElementId, elementType));
    }

    private void addCoordsConfidence(
            Page page,
            String elementId,
            PolygonDto polygon,
            List<PageConfidenceIndex> confidenceIndices,
            Set<String> confidenceDedupKeys) {
        if (polygon == null) {
            return;
        }
        addConfidenceEntry(
            page,
            PageConfidenceIndex.ElementType.COORDS,
            firstNonBlank(elementId, "coords"),
            polygon.confidence(),
            confidenceIndices,
            confidenceDedupKeys
        );
    }

    private void addBaselineConfidence(
            Page page,
            String textLineId,
            PolygonDto baseline,
            List<PageConfidenceIndex> confidenceIndices,
            Set<String> confidenceDedupKeys) {
        if (baseline == null) {
            return;
        }
        addConfidenceEntry(
            page,
            PageConfidenceIndex.ElementType.BASELINE,
            firstNonBlank(textLineId, "baseline"),
            baseline.confidence(),
            confidenceIndices,
            confidenceDedupKeys
        );
    }

    private void addConfidenceEntry(
            Page page,
            PageConfidenceIndex.ElementType elementType,
            String elementId,
            Double confidence,
            List<PageConfidenceIndex> confidenceIndices,
            Set<String> confidenceDedupKeys) {
        if (confidence == null || !Double.isFinite(confidence)) {
            return;
        }
        if (confidence < 0.0 || confidence > 1.0) {
            return;
        }

        String normalizedElementId = firstNonBlank(elementId, "unknown");
        String dedupKey = elementType.name() + "|" + normalizedElementId + "|" + confidence;
        if (!confidenceDedupKeys.add(dedupKey)) {
            return;
        }

        confidenceIndices.add(new PageConfidenceIndex(page, elementType, normalizedElementId, confidence));
    }

    private void indexCommentValue(Page page, String comment, List<PageTextContent> textContents) {
        String normalizedComment = blankToNull(comment);
        if (normalizedComment == null) {
            return;
        }
        textContents.add(new PageTextContent(page, null, null, normalizedComment, null, true));
    }

    private void indexAlternativeImageComments(Page page, List<AlternativeImageDto> alternativeImages, List<PageTextContent> textContents) {
        if (alternativeImages == null || alternativeImages.isEmpty()) {
            return;
        }
        for (AlternativeImageDto image : alternativeImages) {
            if (image == null) continue;
            indexCommentValue(page, image.comments(), textContents);
        }
    }

    private void indexLabelsComments(Page page, List<LabelsDto> labels, List<PageTextContent> textContents) {
        if (labels == null || labels.isEmpty()) {
            return;
        }
        for (LabelsDto group : labels) {
            if (group == null) continue;
            indexCommentValue(page, group.comments(), textContents);
            if (group.labels() == null || group.labels().isEmpty()) continue;
            for (LabelDto label : group.labels()) {
                if (label == null) continue;
                indexCommentValue(page, label.comments(), textContents);
            }
        }
    }

    private void indexReadingOrderComments(Page page, ReadingOrderDto readingOrder, List<PageTextContent> textContents) {
        if (readingOrder == null) {
            return;
        }
        indexReadingOrderGroupComments(page, readingOrder.root(), textContents);
    }

    private void indexReadingOrderGroupComments(
            Page page,
            ReadingOrderDto.GroupDto group,
            List<PageTextContent> textContents) {
        if (group == null) {
            return;
        }

        indexCommentValue(page, group.comments(), textContents);
        indexLabelsComments(page, group.labels(), textContents);

        List<ReadingOrderDto.GroupMemberDto> members = group.members();
        if (members == null || members.isEmpty()) {
            return;
        }
        for (ReadingOrderDto.GroupMemberDto member : members) {
            if (member instanceof ReadingOrderDto.NestedGroupDto nested && nested.group() != null) {
                indexReadingOrderGroupComments(page, nested.group(), textContents);
            }
        }
    }

    private void indexRelationsComments(Page page, RelationsDto relations, List<PageTextContent> textContents) {
        if (relations == null || relations.relations() == null || relations.relations().isEmpty()) {
            return;
        }
        for (RelationDto relation : relations.relations()) {
            if (relation == null) continue;
            indexCommentValue(page, relation.comments(), textContents);
            indexLabelsComments(page, relation.labels(), textContents);
        }
    }

    private void indexGraphemeComments(Page page, GraphemesDto graphemes, List<PageTextContent> textContents) {
        if (graphemes == null || graphemes.elements() == null || graphemes.elements().isEmpty()) {
            return;
        }
        for (GraphemeElementDto element : graphemes.elements()) {
            indexGraphemeElementComments(page, element, textContents);
        }
    }

    private void indexGraphemeElementComments(Page page, GraphemeElementDto element, List<PageTextContent> textContents) {
        if (element == null) {
            return;
        }
        indexCommentValue(page, element.comments(), textContents);
        indexLabelsComments(page, element.labels(), textContents);
        if (element.textContentVariants() != null) {
            for (TextContentVariantDto variant : element.textContentVariants()) {
                if (variant == null) continue;
                indexCommentValue(page, variant.comments(), textContents);
            }
        }
        if (element.members() != null) {
            for (GraphemeElementDto member : element.members()) {
                indexGraphemeElementComments(page, member, textContents);
            }
        }
    }

    // ============================================================================
    // Query Helpers
    // ============================================================================

    private Set<String> allPageIds(String projectId, List<Page> accessiblePages) {
        List<Page> pages = accessiblePages != null ? accessiblePages : pageRepository.findByProjectId(projectId);
        return pages
            .stream()
            .map(Page::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    private Set<Page.WorkflowState> parseWorkflowStates(List<String> rawStates) {
        Set<Page.WorkflowState> states = EnumSet.noneOf(Page.WorkflowState.class);
        if (rawStates == null) {
            return states;
        }
        for (String rawState : rawStates) {
            String normalized = blankToNull(rawState);
            if (normalized == null) {
                continue;
            }
            try {
                states.add(Page.WorkflowState.valueOf(normalized.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                log.debug("Ignoring unsupported workflow state: {}", rawState);
            }
        }
        return states;
    }

    /** Returns null for an incomplete predicate so it does not become an active filter group. */
    private FilterGroup findXmlAttributeGroup(String projectId, XmlAttributePredicate predicate) {
        if (predicate == null) {
            return null;
        }

        String attributeName = blankToNull(predicate.attributeName());
        String elementName = blankToNull(predicate.elementName());
        String operator = blankToNull(predicate.operator());
        if (attributeName == null || operator == null) {
            return null;
        }
        operator = operator.toLowerCase(Locale.ROOT);

        boolean valueOperator = Set.of("equals", "not_equals", "contains", "not_contains").contains(operator);
        if (valueOperator && (predicate.value() == null || predicate.value().isEmpty())) {
            return null;
        }

        Set<String> positiveMatches;
        switch (operator) {
            case "exists", "not_exists" -> positiveMatches = new HashSet<>(
                xmlAttributeIndexRepository.findPageIdsWithAttribute(projectId, elementName, attributeName)
            );
            case "equals", "not_equals" -> positiveMatches = new HashSet<>(
                xmlAttributeIndexRepository.findPageIdsWithAttributeValue(
                    projectId, elementName, attributeName, predicate.value())
            );
            case "contains", "not_contains" -> positiveMatches = new HashSet<>(
                xmlAttributeIndexRepository.findPageIdsWithAttributeValueContaining(
                    projectId, elementName, attributeName, predicate.value())
            );
            default -> {
                log.debug("Ignoring unsupported PAGE XML attribute operator: {}", operator);
                return null;
            }
        }

        return new FilterGroup(positiveMatches, operator.startsWith("not_") != predicate.negated());
    }

    private Set<String> findTagMatches(String projectId, List<String> tags, String tagOperator) {
        List<Page> pages = pageRepository.findByProjectId(projectId);
        Set<String> matches = new HashSet<>();

        for (Page page : pages) {
            List<String> pageTags = page.getTags();
            if (pageTags == null || pageTags.isEmpty()) {
                continue;
            }

            boolean isMatch;
            if ("and".equalsIgnoreCase(tagOperator)) {
                isMatch = pageTags.containsAll(tags);
            } else {
                isMatch = tags.stream().anyMatch(pageTags::contains);
            }

            if (isMatch) {
                matches.add(page.getId());
            }
        }

        return matches;
    }

    private List<PageConfidenceIndex.ElementType> parseConfidenceElementTypes(List<String> rawTypes) {
        if (rawTypes == null || rawTypes.isEmpty()) {
            return new ArrayList<>(EnumSet.allOf(PageConfidenceIndex.ElementType.class));
        }

        Set<PageConfidenceIndex.ElementType> parsed = EnumSet.noneOf(PageConfidenceIndex.ElementType.class);
        for (String rawType : rawTypes) {
            String normalized = blankToNull(rawType);
            if (normalized == null) {
                continue;
            }
            try {
                parsed.add(PageConfidenceIndex.ElementType.valueOf(normalized.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                log.debug("Ignoring unsupported confidence element type: {}", rawType);
            }
        }

        return new ArrayList<>(parsed);
    }

    // ============================================================================
    // Canonical Label Token Helpers
    // ============================================================================

    private String regionBaseToken(String regionKind) {
        return "region|kind=" + encodeTokenPart(regionKind);
    }

    private String regionTextTypeToken(String textType) {
        return "region|kind=" + encodeTokenPart(REGION_KIND_TEXT) + "|textType=" + encodeTokenPart(textType);
    }

    private String regionSubtypeToken(String regionKind, String subtype) {
        return "region|kind=" + encodeTokenPart(regionKind) + "|subType=" + encodeTokenPart(subtype);
    }

    private String lineCustomPresenceToken(String customKey) {
        return "line|customKey=" + encodeTokenPart(customKey);
    }

    private String lineCustomPairsToken(String customKey, Map<String, String> pairs) {
        String pairsToken = pairs.entrySet()
            .stream()
            .sorted(Comparator.comparing(Map.Entry::getKey))
            .map(entry -> encodeTokenPart(entry.getKey()) + "=" + encodeTokenPart(entry.getValue()))
            .collect(Collectors.joining(","));
        return "line|customKey=" + encodeTokenPart(customKey) + "|pairs=" + pairsToken;
    }

    private String textEquivElementId(String ownerId, Integer variantIndex, int position) {
        String suffix = variantIndex != null ? "idx-" + variantIndex : "pos-" + position;
        return firstNonBlank(ownerId, "textequiv") + ":" + suffix;
    }

    // ============================================================================
    // Custom Attribute Helpers
    // ============================================================================

    private String extractStructureTypeFromCustom(String custom) {
        Map<String, Map<String, String>> blocks = parseCustomBlocks(custom);
        Map<String, String> structure = blocks.get("structure");
        if (structure == null || structure.isEmpty()) {
            return null;
        }
        return blankToNull(structure.get("type"));
    }

    private Map<String, Map<String, String>> parseCustomBlocks(String custom) {
        if (custom == null || custom.isBlank()) {
            return Collections.emptyMap();
        }

        Map<String, Map<String, String>> blocks = new LinkedHashMap<>();
        Matcher matcher = CUSTOM_BLOCK_PATTERN.matcher(custom);
        while (matcher.find()) {
            String key = blankToNull(matcher.group(1));
            if (key == null) {
                continue;
            }

            Map<String, String> parsedPairs = parseKeyValuePairs(matcher.group(2));
            Map<String, String> existing = blocks.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
            existing.putAll(parsedPairs);
        }

        return blocks;
    }

    private Map<String, String> parseKeyValuePairs(String rawPairs) {
        if (rawPairs == null || rawPairs.isBlank()) {
            return Collections.emptyMap();
        }

        Map<String, String> pairs = new LinkedHashMap<>();
        String[] segments = rawPairs.split(";");
        for (String segment : segments) {
            String trimmed = segment == null ? null : segment.trim();
            if (trimmed == null || trimmed.isEmpty()) {
                continue;
            }

            int colonIndex = trimmed.indexOf(':');
            if (colonIndex <= 0 || colonIndex >= trimmed.length() - 1) {
                continue;
            }

            String key = blankToNull(trimmed.substring(0, colonIndex));
            String value = blankToNull(trimmed.substring(colonIndex + 1));
            if (key == null || value == null) {
                continue;
            }
            pairs.put(key, value);
        }

        return pairs;
    }

    // ============================================================================
    // Generic Helpers
    // ============================================================================

    private String encodeTokenPart(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return "";
        }
        return URLEncoder.encode(normalized, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String candidate = blankToNull(value);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}

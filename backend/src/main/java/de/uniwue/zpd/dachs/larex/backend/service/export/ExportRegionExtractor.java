package de.uniwue.zpd.dachs.larex.backend.service.export;

import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.readingorder.ReadingOrderDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.region.RegionDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.TextContentVariantDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.TextLineDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.WordDto;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class ExportRegionExtractor {

    private ExportRegionExtractor() {
    }

    static List<ExportRegion> extractRegions(PageDto pageDto, int gtIndex) {
        Map<String, RegionDto> regionById = new LinkedHashMap<>();
        Map<String, String> parentByRegionId = new HashMap<>();
        List<RegionDto> structuralOrder = new ArrayList<>();
        collectRegions(pageDto.regions(), null, regionById, parentByRegionId, structuralOrder);

        List<String> readingOrderIds = new ArrayList<>();
        flattenReadingOrder(pageDto.readingOrder(), readingOrderIds);

        List<ExportRegion> regions = new ArrayList<>();
        Set<String> visitedRegionIds = new HashSet<>();
        int readingOrderIndex = 0;

        for (String regionId : readingOrderIds) {
            RegionDto region = regionById.get(regionId);
            ExportRegion exportRegion = toExportRegion(region, parentByRegionId.get(regionId), gtIndex, readingOrderIndex);
            if (exportRegion != null && visitedRegionIds.add(exportRegion.id())) {
                regions.add(exportRegion);
                readingOrderIndex++;
            }
        }

        for (RegionDto region : structuralOrder) {
            ExportRegion exportRegion = toExportRegion(region, parentByRegionId.get(region.id()), gtIndex, readingOrderIndex);
            if (exportRegion != null && visitedRegionIds.add(exportRegion.id())) {
                regions.add(exportRegion);
                readingOrderIndex++;
            }
        }

        return regions;
    }

    private static void collectRegions(List<RegionDto> regions,
                                       String parentRegionId,
                                       Map<String, RegionDto> regionById,
                                       Map<String, String> parentByRegionId,
                                       List<RegionDto> structuralOrder) {
        if (regions == null) {
            return;
        }
        for (RegionDto region : regions) {
            if (region == null || region.id() == null) {
                continue;
            }
            regionById.put(region.id(), region);
            parentByRegionId.put(region.id(), parentRegionId);
            structuralOrder.add(region);
            collectRegions(region.nestedRegions(), region.id(), regionById, parentByRegionId, structuralOrder);
        }
    }

    private static void flattenReadingOrder(ReadingOrderDto readingOrder, List<String> orderedIds) {
        if (readingOrder == null || readingOrder.root() == null) {
            return;
        }
        flattenGroup(readingOrder.root(), orderedIds);
    }

    private static void flattenGroup(ReadingOrderDto.GroupDto group, List<String> orderedIds) {
        if (group == null || group.members() == null) {
            return;
        }
        for (ReadingOrderDto.GroupMemberDto member : group.members()) {
            if (member instanceof ReadingOrderDto.RegionRefDto regionRef && regionRef.regionRef() != null) {
                orderedIds.add(regionRef.regionRef());
            } else if (member instanceof ReadingOrderDto.NestedGroupDto nestedGroup) {
                flattenGroup(nestedGroup.group(), orderedIds);
            }
        }
    }

    private static ExportRegion toExportRegion(RegionDto region,
                                                   String parentRegionId,
                                                   int gtIndex,
                                                   int readingOrderIndex) {
        if (region == null || region.id() == null) {
            return null;
        }

        List<ExportTextLine> lines = extractLines(region.textLines(), gtIndex);
        String text = !lines.isEmpty()
                ? String.join("\n", lines.stream().map(ExportTextLine::text).filter(Objects::nonNull).toList())
                : resolveVariant(region.textContentVariants(), gtIndex).text();

        return new ExportRegion(
                region.id(),
                parentRegionId,
                region.kind(),
                region.type(),
                readingOrderIndex,
                region.coords(),
                region.rows(),
                region.columns(),
                region.labelIds(),
                region.custom(),
                text,
                lines
        );
    }

    private static List<ExportTextLine> extractLines(List<TextLineDto> textLines, int gtIndex) {
        if (textLines == null || textLines.isEmpty()) {
            return List.of();
        }

        List<TextLineDto> sortedTextLines = textLines.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing((TextLineDto line) -> line.index() == null ? Integer.MAX_VALUE : line.index())
                        .thenComparing(line -> line.id() == null ? "" : line.id()))
                .toList();

        List<ExportTextLine> lines = new ArrayList<>();
        for (TextLineDto line : sortedTextLines) {
            VariantSelection variant = resolveVariant(line.textContentVariants(), gtIndex);
            String text = variant.text();
            if ((text == null || text.isBlank()) && line.getText() != null && !line.getText().isBlank()) {
                text = line.getText();
            }
            List<ExportWord> words = extractWords(line.words(), gtIndex);
            if ((text == null || text.isBlank()) && !words.isEmpty()) {
                text = words.stream().map(ExportWord::text).filter(Objects::nonNull).reduce((left, right) -> left + " " + right).orElse(null);
            }
            lines.add(new ExportTextLine(
                    line.id(),
                    text,
                    line.coords(),
                    line.baseline(),
                    line.confidence(),
                    variant.confidence(),
                    words
            ));
        }
        return lines;
    }

    private static List<ExportWord> extractWords(List<WordDto> words, int gtIndex) {
        if (words == null || words.isEmpty()) {
            return List.of();
        }

        List<ExportWord> exportWords = new ArrayList<>();
        for (WordDto word : words) {
            if (word == null) {
                continue;
            }
            VariantSelection variant = resolveVariant(word.textContentVariants(), gtIndex);
            String text = variant.text();
            if ((text == null || text.isBlank()) && word.getText() != null && !word.getText().isBlank()) {
                text = word.getText();
            }
            exportWords.add(new ExportWord(word.id(), text, word.coords(), word.confidence(), variant.confidence()));
        }
        return exportWords;
    }

    private static VariantSelection resolveVariant(List<TextContentVariantDto> variants, int gtIndex) {
        if (variants == null || variants.isEmpty()) {
            return VariantSelection.EMPTY;
        }

        for (TextContentVariantDto variant : variants) {
            if (variant != null && Objects.equals(variant.index(), gtIndex) && hasText(variant.unicode())) {
                return new VariantSelection(variant.unicode(), variant.confidence());
            }
        }

        if (gtIndex != 0) {
            for (TextContentVariantDto variant : variants) {
                if (variant != null && Objects.equals(variant.index(), 0) && hasText(variant.unicode())) {
                    return new VariantSelection(variant.unicode(), variant.confidence());
                }
            }
        }

        return variants.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing((TextContentVariantDto variant) -> variant.index() == null ? Integer.MAX_VALUE : variant.index()))
                .filter(variant -> hasText(variant.unicode()))
                .findFirst()
                .map(variant -> new VariantSelection(variant.unicode(), variant.confidence()))
                .orElse(VariantSelection.EMPTY);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record VariantSelection(
            String text,
            Double confidence
    ) {
        private static final VariantSelection EMPTY = new VariantSelection(null, null);
    }
}

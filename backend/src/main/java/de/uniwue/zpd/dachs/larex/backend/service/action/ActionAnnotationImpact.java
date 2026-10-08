package de.uniwue.zpd.dachs.larex.backend.service.action;

import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto.AnnotationLevel;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto.TargetSelectionPage;
import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.region.RegionDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.readingorder.ReadingOrderDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.TextContentVariantDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.TextLineDto;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition.ActionTarget;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Read-only inspection of the annotation scope replaced by result import. */
public final class ActionAnnotationImpact {
    private ActionAnnotationImpact() {}

    public static List<AnnotationLevel> affectedLevels(PageDto page, ActionTarget target,
                                                       TargetSelectionPage selection,
                                                       List<AnnotationLevel> declarations) {
        Set<AnnotationLevel> present = EnumSet.noneOf(AnnotationLevel.class);
        if (target == ActionTarget.PAGE) {
            collectRegions(page.regions(), present);
            if (page.readingOrder() != null && hasReadingOrderReferences(page.readingOrder().root())) {
                present.add(AnnotationLevel.READING_ORDER);
            }
        } else {
            collectSelected(page.regions(), target, Set.copyOf(safe(selection.regionIds())),
                    Set.copyOf(safe(selection.textLineIds())), present);
        }
        present.retainAll(expand(declarations));
        return List.copyOf(present);
    }

    static Set<AnnotationLevel> expand(List<AnnotationLevel> declarations) {
        Set<AnnotationLevel> levels = EnumSet.noneOf(AnnotationLevel.class);
        levels.addAll(declarations);
        if (levels.contains(AnnotationLevel.REGIONS)) {
            levels.addAll(EnumSet.of(AnnotationLevel.TEXT_LINES, AnnotationLevel.BASELINES,
                    AnnotationLevel.TEXT, AnnotationLevel.WORDS, AnnotationLevel.GLYPHS));
        }
        if (levels.contains(AnnotationLevel.TEXT_LINES)) {
            levels.addAll(EnumSet.of(AnnotationLevel.BASELINES, AnnotationLevel.TEXT,
                    AnnotationLevel.WORDS, AnnotationLevel.GLYPHS));
        }
        if (levels.contains(AnnotationLevel.WORDS)) {
            levels.addAll(EnumSet.of(AnnotationLevel.GLYPHS, AnnotationLevel.TEXT));
        }
        if (levels.contains(AnnotationLevel.GLYPHS)) levels.add(AnnotationLevel.TEXT);
        return levels;
    }

    private static boolean hasReadingOrderReferences(ReadingOrderDto.GroupDto group) {
        if (group == null) return false;
        for (var member : safe(group.members())) {
            if (member instanceof ReadingOrderDto.RegionRefDto reference && nonempty(reference.regionRef())) {
                return true;
            }
            if (member instanceof ReadingOrderDto.NestedGroupDto nested && hasReadingOrderReferences(nested.group())) {
                return true;
            }
        }
        return false;
    }

    private static void collectSelected(List<RegionDto> regions, ActionTarget target,
                                        Set<String> regionIds, Set<String> lineIds, Set<AnnotationLevel> present) {
        for (RegionDto region : safe(regions)) {
            if (target == ActionTarget.REGION && region.id() != null && regionIds.contains(region.id())) {
                // Import preserves the selected region itself, replacing its children only.
                collectLines(region.textLines(), present);
                collectRegions(region.nestedRegions(), present);
            } else {
                if (target == ActionTarget.TEXT_LINE) {
                    for (TextLineDto line : safe(region.textLines())) {
                        if (line.id() != null && lineIds.contains(line.id())) collectLines(List.of(line), present);
                    }
                }
                collectSelected(region.nestedRegions(), target, regionIds, lineIds, present);
            }
        }
    }

    private static void collectRegions(List<RegionDto> regions, Set<AnnotationLevel> present) {
        for (RegionDto region : safe(regions)) {
            present.add(AnnotationLevel.REGIONS);
            collectText(region.textContentVariants(), present);
            collectLines(region.textLines(), present);
            collectRegions(region.nestedRegions(), present);
        }
    }

    private static void collectLines(List<TextLineDto> lines, Set<AnnotationLevel> present) {
        for (TextLineDto line : safe(lines)) {
            present.add(AnnotationLevel.TEXT_LINES);
            if (line.baseline() != null && !safe(line.baseline().points()).isEmpty()) {
                present.add(AnnotationLevel.BASELINES);
            }
            collectText(line.textContentVariants(), present);
            for (var word : safe(line.words())) {
                present.add(AnnotationLevel.WORDS);
                collectText(word.textContentVariants(), present);
                for (var glyph : safe(word.glyphs())) {
                    present.add(AnnotationLevel.GLYPHS);
                    collectText(glyph.textContentVariants(), present);
                }
            }
        }
    }

    private static void collectText(List<TextContentVariantDto> variants, Set<AnnotationLevel> present) {
        if (safe(variants).stream().anyMatch(v -> nonempty(v.unicode()) || nonempty(v.plainText()))) {
            present.add(AnnotationLevel.TEXT);
        }
    }

    private static boolean nonempty(String value) { return value != null && !value.isBlank(); }
    private static <T> List<T> safe(List<T> values) { return values == null ? List.of() : values; }
}

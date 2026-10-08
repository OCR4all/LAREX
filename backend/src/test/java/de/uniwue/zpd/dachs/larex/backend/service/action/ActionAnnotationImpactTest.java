package de.uniwue.zpd.dachs.larex.backend.service.action;

import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition.ActionTarget;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto.AnnotationLevel.*;
import static org.assertj.core.api.Assertions.assertThat;

class ActionAnnotationImpactTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final PageDto annotated = mapper.readValue("""
            {"imageWidth":100,"imageHeight":100,"regions":[
              {"id":"selected","textContentVariants":[{"unicode":"preserved region text"}],
               "textLines":[
                 {"id":"line","baseline":{"points":[[0,0]]},
                  "words":[{"id":"word","glyphs":[{"id":"glyph","textContentVariants":[{"unicode":"A"}]}]}]},
                 {"id":"other-line","textContentVariants":[{"plainText":"other text"}]}],
               "nestedRegions":[{"id":"nested","textContentVariants":[{"unicode":"nested text"}]}]},
              {"id":"unselected","textLines":[{"id":"unselected-line","textContentVariants":[{"unicode":"unrelated"}]}]}],
             "readingOrder":{"root":{"id":"order","ordered":true,"members":[]}}}
            """, PageDto.class);

    @Test
    void expandsStructuralReplacementsAndOnlyReportsExistingLevels() {
        assertThat(levels(ActionTarget.PAGE, List.of(REGIONS, READING_ORDER)))
                .containsExactly(REGIONS, TEXT_LINES, BASELINES, TEXT, WORDS, GLYPHS, READING_ORDER);
        assertThat(levels(ActionTarget.PAGE, List.of(WORDS))).containsExactly(TEXT, WORDS, GLYPHS);
        assertThat(levels(ActionTarget.PAGE, List.of(GLYPHS))).containsExactly(TEXT, GLYPHS);
    }

    @Test
    void regionScopePreservesSelectedRegionAndPageReadingOrder() {
        assertThat(levels(ActionTarget.REGION, List.of(REGIONS, READING_ORDER)))
                .containsExactly(REGIONS, TEXT_LINES, BASELINES, TEXT, WORDS, GLYPHS);
        PageDto regionOnly = mapper.readValue("""
                {"imageWidth":100,"imageHeight":100,"regions":[{"id":"selected","textContentVariants":[{"unicode":"preserved"}]}]}
                """, PageDto.class);
        assertThat(ActionAnnotationImpact.affectedLevels(regionOnly, ActionTarget.REGION, selection(), List.of(REGIONS, TEXT)))
                .isEmpty();
    }

    @Test
    void textlineScopeIgnoresOtherLinesAndNestedRegions() {
        assertThat(levels(ActionTarget.TEXT_LINE, List.of(REGIONS, READING_ORDER)))
                .containsExactly(TEXT_LINES, BASELINES, TEXT, WORDS, GLYPHS);
        assertThat(levels(ActionTarget.TEXT_LINE, List.of(TEXT))).containsExactly(TEXT);
    }

    @Test
    void readsTextAtEveryHierarchyLevel() {
        for (String regions : List.of(
                "[{\"textContentVariants\":[{\"unicode\":\"text\"}]}]",
                "[{\"textLines\":[{\"textContentVariants\":[{\"plainText\":\"text\"}]}]}]",
                "[{\"textLines\":[{\"words\":[{\"textContentVariants\":[{\"unicode\":\"text\"}]}]}]}]",
                "[{\"textLines\":[{\"words\":[{\"glyphs\":[{\"textContentVariants\":[{\"unicode\":\"text\"}]}]}]}]}]")) {
            PageDto page = mapper.readValue("{\"imageWidth\":100,\"imageHeight\":100,\"regions\":" + regions + "}", PageDto.class);
            assertThat(ActionAnnotationImpact.affectedLevels(page, ActionTarget.PAGE, selection(), List.of(TEXT)))
                    .containsExactly(TEXT);
        }
    }

    @Test
    void emptyAndWhitespaceTextAndEmptyBaselinesAreNotAffected() {
        PageDto page = mapper.readValue("""
                {"imageWidth":100,"imageHeight":100,"regions":[{"textLines":[{"baseline":{"points":[]},"textContentVariants":[{"unicode":" "}]}]}]}
                """, PageDto.class);
        assertThat(ActionAnnotationImpact.affectedLevels(page, ActionTarget.PAGE, selection(), List.of(TEXT, BASELINES)))
                .isEmpty();
        assertThat(ActionAnnotationImpact.affectedLevels(mapper.readValue("{\"imageWidth\":100,\"imageHeight\":100}", PageDto.class),
                ActionTarget.PAGE, selection(), List.of(REGIONS, READING_ORDER))).isEmpty();
        assertThat(levels(ActionTarget.PAGE, List.of())).isEmpty();
    }

    private List<ActionDto.AnnotationLevel> levels(ActionTarget target, List<ActionDto.AnnotationLevel> declarations) {
        return ActionAnnotationImpact.affectedLevels(annotated, target, selection(), declarations);
    }

    private ActionDto.TargetSelectionPage selection() {
        return new ActionDto.TargetSelectionPage("page", List.of("selected"), List.of("line"));
    }
}

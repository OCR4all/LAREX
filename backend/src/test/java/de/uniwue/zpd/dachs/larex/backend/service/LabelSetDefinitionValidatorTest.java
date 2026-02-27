package de.uniwue.zpd.dachs.larex.backend.service;

import de.uniwue.zpd.dachs.larex.backend.dto.LabelSetDto;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LabelSetDefinitionValidatorTest {

    private static LabelSetDefinitionValidator validator;

    @BeforeAll
    static void setUpValidator() {
        Validator beanValidator = Validation.buildDefaultValidatorFactory().getValidator();
        validator = new LabelSetDefinitionValidator(beanValidator);
    }

    @Test
    void validate_allowsTextRegionWithoutSubtype() {
        LabelSetDto.CreateOrUpdateRequest request = createRequest(
                LabelSetDto.PageRegionType.TextRegion,
                null
        );

        assertDoesNotThrow(() -> validator.validate(request));
    }

    @Test
    void validate_allowsTextRegionWithEmptySubtype() {
        LabelSetDto.CreateOrUpdateRequest request = createRequest(
                LabelSetDto.PageRegionType.TextRegion,
                ""
        );

        assertDoesNotThrow(() -> validator.validate(request));
    }

    @Test
    void validate_rejectsTextSubtypeForNonTextRegion() {
        LabelSetDto.CreateOrUpdateRequest request = createRequest(
                LabelSetDto.PageRegionType.ImageRegion,
                "paragraph"
        );

        assertThrows(IllegalArgumentException.class, () -> validator.validate(request));
    }

    @Test
    void validate_rejectsDuplicateRegionPageMappings() {
        LabelSetDto.Label first = createRegionLabel(
                "label-1",
                "Heading A",
                LabelSetDto.PageRegionType.TextRegion,
                "heading",
                "",
                "structure",
                ""
        );
        LabelSetDto.Label second = createRegionLabel(
                "label-2",
                "Heading B",
                LabelSetDto.PageRegionType.TextRegion,
                "heading",
                "",
                "structure",
                ""
        );

        LabelSetDto.CreateOrUpdateRequest request = new LabelSetDto.CreateOrUpdateRequest(
                new LabelSetDto.Meta("Test Label Set", "", List.of(), false, false),
                List.of(first, second)
        );

        assertThrows(IllegalArgumentException.class, () -> validator.validate(request));
    }

    @Test
    void validate_allowsDistinctCustomTextRegionMappings() {
        LabelSetDto.Label first = createRegionLabel(
                "label-1",
                "Custom Type A",
                LabelSetDto.PageRegionType.TextRegion,
                "custom",
                "article",
                "structure",
                "subclass:lead"
        );
        LabelSetDto.Label second = createRegionLabel(
                "label-2",
                "Custom Type B",
                LabelSetDto.PageRegionType.TextRegion,
                "custom",
                "article",
                "structure",
                "subclass:body"
        );

        LabelSetDto.CreateOrUpdateRequest request = new LabelSetDto.CreateOrUpdateRequest(
                new LabelSetDto.Meta("Test Label Set", "", List.of(), false, false),
                List.of(first, second)
        );

        assertDoesNotThrow(() -> validator.validate(request));
    }

    @Test
    void validate_rejectsCustomTextRegionWithoutCustomSubType() {
        LabelSetDto.Label label = createRegionLabel(
                "label-1",
                "Custom Missing Subtype",
                LabelSetDto.PageRegionType.TextRegion,
                "custom",
                "",
                "structure",
                "subclass:lead"
        );

        LabelSetDto.CreateOrUpdateRequest request = new LabelSetDto.CreateOrUpdateRequest(
                new LabelSetDto.Meta("Test Label Set", "", List.of(), false, false),
                List.of(label)
        );

        assertThrows(IllegalArgumentException.class, () -> validator.validate(request));
    }

    private static LabelSetDto.CreateOrUpdateRequest createRequest(
            LabelSetDto.PageRegionType regionType,
            String textType
    ) {
        LabelSetDto.Label label = createRegionLabel("label-1", "Label 1", regionType, textType, "", "structure", "");

        return new LabelSetDto.CreateOrUpdateRequest(
                new LabelSetDto.Meta("Test Label Set", "", List.of(), false, false),
                List.of(label)
        );
    }

    private static LabelSetDto.Label createRegionLabel(
            String id,
            String name,
            LabelSetDto.PageRegionType regionType,
            String textType,
            String customSubType,
            String customKey,
            String customData
    ) {
        boolean hasText = regionType == LabelSetDto.PageRegionType.TextRegion;
        return new LabelSetDto.Label(
                id,
                LabelSetDto.LabelScope.REGION,
                name,
                "Test label",
                "#123ABC",
                hasText,
                false,
                null,
                new LabelSetDto.Mapping(
                        new LabelSetDto.AltoXml(
                                LabelSetDto.AltoRole.TAGREFS,
                                name.replace(" ", ""),
                                hasText ? LabelSetDto.AltoBlockType.TextBlock : LabelSetDto.AltoBlockType.Illustration
                        ),
                        new LabelSetDto.PageXml(regionType, textType, customSubType, customKey, customData)
                )
        );
    }
}

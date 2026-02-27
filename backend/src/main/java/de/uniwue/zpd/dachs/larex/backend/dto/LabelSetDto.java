package de.uniwue.zpd.dachs.larex.backend.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public class LabelSetDto {

        @JsonIgnoreProperties(ignoreUnknown = false)
        public record Meta(
                        @NotBlank(message = "Name is required")
                        @Size(max = 255, message = "Name must not exceed 255 characters")
                        String name,

                        @Size(max = 10_000, message = "Description must not exceed 10000 characters")
                        String description,

                        @Size(max = 100, message = "Cannot have more than 100 tags")
                        List<String> tags,

                        Boolean altoEnabled,

                        Boolean isSystem
        ) {
                public boolean isAltoEnabled() {
                        return altoEnabled != null && altoEnabled;
                }

                public boolean getIsSystem() {
                        return isSystem != null && isSystem;
                }
        }

        public enum LabelScope {
                REGION("region"),
                LINE("line");

                private final String value;

                LabelScope(String value) {
                        this.value = value;
                }

                @JsonValue
                public String value() {
                        return value;
                }

                @JsonCreator
                public static LabelScope fromValue(String value) {
                        if (value == null) {
                                return null;
                        }
                        for (LabelScope scope : values()) {
                                if (scope.value.equalsIgnoreCase(value)) {
                                        return scope;
                                }
                        }
                        throw new IllegalArgumentException("Invalid label scope: " + value);
                }
        }

        public enum AltoRole {
                TAGREFS,
                STYLEREFS
        }

        public enum AltoBlockType {
                TextBlock,
                Illustration,
                GraphicalElement,
                ComposedBlock
        }

        public enum PageRegionType {
                TextRegion,
                ImageRegion,
                LineDrawingRegion,
                GraphicRegion,
                TableRegion,
                ChartRegion,
                MapRegion,
                SeparatorRegion,
                MathsRegion,
                ChemRegion,
                MusicRegion,
                AdvertRegion,
                NoiseRegion,
                UnknownRegion
        }

        @JsonIgnoreProperties(ignoreUnknown = false)
        public record AltoXml(
                        AltoRole role,

                        String tag,

                        AltoBlockType blockType
        ) {}

        @JsonIgnoreProperties(ignoreUnknown = false)
        public record PageXml(
                        PageRegionType regionType,

                        String textType,

                        String customSubType,

                        @NotBlank(message = "PAGE customKey is required")
                        String customKey,

                        String customData
        ) {}

        @JsonIgnoreProperties(ignoreUnknown = false)
        public record Mapping(
                        @NotNull(message = "Mapping.altoXml is required")
                        @Valid
                        AltoXml altoXml,

                        @NotNull(message = "Mapping.pageXml is required")
                        @Valid
                        PageXml pageXml
        ) {}

        @JsonIgnoreProperties(ignoreUnknown = false)
        public record Label(
                        @NotBlank(message = "Label ID is required")
                        String id,

                        @NotNull(message = "Label scope is required")
                        LabelScope scope,

                        @NotBlank(message = "Label name is required")
                        @Size(max = 255, message = "Label name must not exceed 255 characters")
                        String name,

                        @Size(max = 10_000, message = "Label description must not exceed 10000 characters")
                        String description,

                        @NotBlank(message = "Label color is required")
                        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Label color must be a hex string like #RRGGBB")
                        String color,

                        boolean hasText,

                        boolean isContainer,

                        String group,

                        @NotNull(message = "Label mapping is required")
                        @Valid
                        Mapping mapping
        ) {}

        @JsonIgnoreProperties(ignoreUnknown = false)
    public record CreateOrUpdateRequest(
                        @NotNull(message = "Meta is required")
                        @Valid
                        Meta meta,

                        @NotNull(message = "Labels is required")
                        @Valid
                        List<Label> labels
    ) {}

    public record Response(
            String id,
                        Meta meta,
                        List<Label> labels,
            LocalDateTime created,
            LocalDateTime updated
    ) {}

    public record SummaryResponse(
            String id,
                        Meta meta,
            int labelCount,
            LocalDateTime created,
            LocalDateTime updated
    ) {}
}

package de.uniwue.zpd.dachs.larex.backend.service.label;

import de.uniwue.zpd.dachs.larex.backend.dto.LabelSetDto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import java.util.HashSet;
import java.util.Set;

@Component
public class LabelSetDefinitionValidator {

    private static final Set<String> ALLOWED_TEXT_TYPES = Set.of(
            "",
            "paragraph",
            "heading",
            "caption",
            "header",
            "footer",
            "page-number",
            "drop-capital",
            "credit",
            "floating",
            "signature-mark",
            "catch-word",
            "marginalia",
            "footnote",
            "footnote-continued",
            "endnote",
            "TOC-entry",
            "list-label",
            "other",
            "custom"
    );

    private final Validator validator;

    public LabelSetDefinitionValidator(Validator validator) {
        this.validator = validator;
    }

    public void rejectUnknownFields(JsonNode root) {
        requireObject(root, "$", Set.of("meta", "labels"));

        JsonNode meta = root.get("meta");
        requireObject(meta, "$.meta", Set.of("name", "description", "altoEnabled", "tags"));

        JsonNode labels = root.get("labels");
        if (labels == null || !labels.isArray()) {
            throw new IllegalArgumentException("$.labels must be an array");
        }

        for (int index = 0; index < labels.size(); index++) {
            JsonNode label = labels.get(index);
            String base = "$.labels[" + index + "]";
            requireObject(label, base, Set.of(
                    "id",
                    "scope",
                    "name",
                    "description",
                    "color",
                    "hasText",
                    "isContainer",
                    "group",
                    "mapping"
            ));

            JsonNode mapping = label.get("mapping");
            requireObject(mapping, base + ".mapping", Set.of("altoXml", "pageXml"));

            JsonNode altoXml = mapping.get("altoXml");
            requireObject(altoXml, base + ".mapping.altoXml", Set.of("role", "tag", "blockType"));

            JsonNode pageXml = mapping.get("pageXml");
            requireObject(pageXml, base + ".mapping.pageXml", Set.of(
                    "regionType",
                    "textType",
                    "customSubType",
                    "customKey",
                    "customData"
            ));
        }
    }

    public void validate(LabelSetDto.CreateOrUpdateRequest request) {
        Set<ConstraintViolation<LabelSetDto.CreateOrUpdateRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(violations.iterator().next().getPropertyPath() + ": " + violations.iterator().next().getMessage());
        }

        // Ensure label IDs are unique
        Set<String> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        Map<String, String> regionMappingSignatureToLabelName = new LinkedHashMap<>();
        for (LabelSetDto.Label label : request.labels()) {
            if (!ids.add(label.id())) {
                throw new IllegalArgumentException("Duplicate label id: " + label.id());
            }
            String normalizedName = label.name().trim().toLowerCase();
            if (!names.add(normalizedName)) {
                throw new IllegalArgumentException("Duplicate label name: " + label.name());
            }

            String regionSignature = canonicalRegionMappingSignature(label);
            if (regionSignature != null) {
                String existingLabel = regionMappingSignatureToLabelName.putIfAbsent(regionSignature, label.name());
                if (existingLabel != null) {
                    throw new IllegalArgumentException("Duplicate PAGE region mapping between labels '" + existingLabel + "' and '" + label.name() + "': " + regionSignature);
                }
            }
        }

        for (LabelSetDto.Label label : request.labels()) {
            validateLabel(label, request.meta().isAltoEnabled());
        }
    }

    private void requireObject(JsonNode node, String path, Set<String> allowedFields) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException(path + " must be an object");
        }

        Iterator<String> fieldNames = node.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            if (!allowedFields.contains(fieldName)) {
                throw new IllegalArgumentException("Unknown field: " + path + "." + fieldName);
            }
        }
    }

    private void validateLabel(LabelSetDto.Label label, boolean altoEnabled) {
        LabelSetDto.Mapping mapping = label.mapping();
        LabelSetDto.AltoXml altoXml = mapping.altoXml();
        LabelSetDto.PageXml pageXml = mapping.pageXml();

        // Validate scope-dependent shape
        if (label.scope() == LabelSetDto.LabelScope.LINE) {
            if (altoEnabled && altoXml.blockType() != null) {
                throw new IllegalArgumentException("Label '" + label.name() + "' (line) must not specify altoXml.blockType");
            }
            if (pageXml.regionType() != null) {
                throw new IllegalArgumentException("Label '" + label.name() + "' (line) must not specify pageXml.regionType");
            }
            if (pageXml.textType() != null) {
                throw new IllegalArgumentException("Label '" + label.name() + "' (line) must not specify pageXml.textType");
            }
            if (pageXml.customSubType() != null) {
                throw new IllegalArgumentException("Label '" + label.name() + "' (line) must not specify pageXml.customSubType");
            }
        } else if (label.scope() == LabelSetDto.LabelScope.REGION) {
            if (altoEnabled && altoXml.blockType() == null) {
                throw new IllegalArgumentException("Label '" + label.name() + "' (region) must specify altoXml.blockType");
            }
            if (pageXml.regionType() == null) {
                throw new IllegalArgumentException("Label '" + label.name() + "' (region) must specify pageXml.regionType");
            }
            if (pageXml.customSubType() == null) {
                throw new IllegalArgumentException("Label '" + label.name() + "' (region) must specify pageXml.customSubType (can be empty string)");
            }

            // PAGE textType rules
            if (pageXml.regionType() == LabelSetDto.PageRegionType.TextRegion) {
                // TextRegion subtype is optional: null/blank means plain <TextRegion> without @type.
                if ("custom".equals(pageXml.textType()) && (pageXml.customSubType() == null || pageXml.customSubType().isBlank())) {
                    throw new IllegalArgumentException("Label '" + label.name() + "' with pageXml.textType=custom must specify pageXml.customSubType");
                }
            } else {
                if (pageXml.textType() != null && !pageXml.textType().isBlank()) {
                    throw new IllegalArgumentException("Label '" + label.name() + "' (region) must not specify pageXml.textType for non-TextRegion");
                }
            }

            // Semantic constraints - only check ALTO-related ones when altoEnabled
            if (label.hasText()) {
                if (pageXml.regionType() != LabelSetDto.PageRegionType.TextRegion) {
                    throw new IllegalArgumentException("Label '" + label.name() + "' hasText=true requires pageXml.regionType=TextRegion");
                }
                if (altoEnabled && altoXml.blockType() != LabelSetDto.AltoBlockType.TextBlock && altoXml.blockType() != LabelSetDto.AltoBlockType.ComposedBlock) {
                    throw new IllegalArgumentException("Label '" + label.name() + "' hasText=true requires altoXml.blockType=TextBlock or ComposedBlock");
                }
            }

            if (label.isContainer() && altoEnabled) {
                if (altoXml.blockType() != LabelSetDto.AltoBlockType.ComposedBlock) {
                    throw new IllegalArgumentException("Label '" + label.name() + "' isContainer=true requires altoXml.blockType=ComposedBlock");
                }
            }
        }

        // Always validate that textType (if present) is within allowed values.
        if (pageXml.textType() != null && !ALLOWED_TEXT_TYPES.contains(pageXml.textType())) {
            throw new IllegalArgumentException("Label '" + label.name() + "' has invalid pageXml.textType: " + pageXml.textType());
        }

        // ALTO validation only when enabled
        if (altoEnabled) {
            if (altoXml.role() == null) {
                throw new IllegalArgumentException("Label '" + label.name() + "' must specify altoXml.role");
            }
            if (altoXml.tag() == null || altoXml.tag().isBlank()) {
                throw new IllegalArgumentException("Label '" + label.name() + "' must specify altoXml.tag");
            }
        }

        // Ensure customKey is always meaningful
        if (pageXml.customKey() == null || pageXml.customKey().isBlank()) {
            throw new IllegalArgumentException("Label '" + label.name() + "' must specify pageXml.customKey");
        }
    }

    private String canonicalRegionMappingSignature(LabelSetDto.Label label) {
        if (label == null || label.scope() != LabelSetDto.LabelScope.REGION || label.mapping() == null || label.mapping().pageXml() == null) {
            return null;
        }

        LabelSetDto.PageXml pageXml = label.mapping().pageXml();
        if (pageXml.regionType() == null) {
            return null;
        }

        String regionType = pageXml.regionType().name();
        if (pageXml.regionType() != LabelSetDto.PageRegionType.TextRegion) {
            String subType = normalize(pageXml.customSubType());
            return subType == null
                    ? "region|kind=" + regionType
                    : "region|kind=" + regionType + "|subType=" + subType;
        }

        String textType = normalize(pageXml.textType());
        if (textType == null) {
            textType = "";
        }
        if (!"custom".equals(textType)) {
            return "region|kind=TextRegion|textType=" + textType;
        }

        String blockKey = normalize(pageXml.customKey());
        if (blockKey == null) {
            blockKey = "structure";
        }
        String customSubType = normalize(pageXml.customSubType());
        if (customSubType == null) {
            customSubType = "";
        }

        Map<String, String> pairs = parseLabelCustomPairs(pageXml.customData());
        pairs.remove("type");

        String pairsToken = pairs.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((a, b) -> a + "," + b)
                .orElse("");

        return "region|kind=TextRegion|textType=custom|block=" + blockKey + "|customType=" + customSubType + "|pairs=" + pairsToken;
    }

    private Map<String, String> parseLabelCustomPairs(String raw) {
        Map<String, String> pairs = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return pairs;
        }

        String[] segments = raw.split(";");
        for (String segment : segments) {
            if (segment == null) {
                continue;
            }
            String trimmed = segment.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            int colonIndex = trimmed.indexOf(':');
            if (colonIndex <= 0 || colonIndex >= trimmed.length() - 1) {
                continue;
            }

            String key = normalize(trimmed.substring(0, colonIndex));
            String value = normalize(trimmed.substring(colonIndex + 1));
            if (key == null || value == null) {
                continue;
            }
            pairs.put(key, value);
        }
        return pairs;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

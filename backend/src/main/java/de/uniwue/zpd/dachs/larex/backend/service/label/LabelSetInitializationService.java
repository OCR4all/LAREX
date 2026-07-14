package de.uniwue.zpd.dachs.larex.backend.service.label;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import de.uniwue.zpd.dachs.larex.backend.dto.LabelSetDto;
import de.uniwue.zpd.dachs.larex.backend.entity.LabelSet;
import de.uniwue.zpd.dachs.larex.backend.repository.label.LabelSetRepository;
import de.uniwue.zpd.dachs.larex.backend.util.JsonNodeUtils;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class LabelSetInitializationService {

    private final LabelSetRepository labelSetRepository;
    private final ObjectMapper objectMapper;
    private static final String EMPTY_CUSTOM_SUBTYPE = "";
    private static final List<String> TEXT_SUBTYPE_COLORS = List.of(
            "#1E88E5",
            "#D81B60",
            "#8E24AA",
            "#5E35B1",
            "#3949AB",
            "#039BE5",
            "#00897B",
            "#43A047",
            "#7CB342",
            "#C0CA33",
            "#FDD835",
            "#FFB300",
            "#FB8C00",
            "#F4511E",
            "#6D4C41",
            "#546E7A",
            "#00ACC1",
            "#7E57C2"
    );

    public LabelSetInitializationService(LabelSetRepository labelSetRepository, ObjectMapper objectMapper) {
        this.labelSetRepository = labelSetRepository;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void fixExistingLabelSets() {
        List<LabelSet> allLabelSets = labelSetRepository.findAll();
        for (LabelSet labelSet : allLabelSets) {
            if (!"PAGE XML Standard".equals(labelSet.getName()) && labelSet.isSystem()) {
                labelSet.setSystem(false);
                labelSetRepository.save(labelSet);
            }
        }
        normalizePageXmlDefaults(allLabelSets);
    }

    public LabelSet createPageXmlLabelset(String workspaceId) {
        if (labelSetRepository.existsByNameAndWorkspaceId("PAGE XML Standard", workspaceId)) {
            return labelSetRepository.findByWorkspaceId(workspaceId).stream()
                    .filter(ls -> "PAGE XML Standard".equals(ls.getName()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("PAGE XML Standard labelset not found"));
        }

        LabelSetDto.Meta meta = new LabelSetDto.Meta(
                "PAGE XML Standard",
                "Standard PAGE XML region types based on the PAGE XML schema. This is a system-provided labelset that cannot be modified.",
                List.of("PAGE XML", "Standard", "System"),
                true
        );

        List<LabelSetDto.Label> labels = createPageXmlLabels();

        LabelSetDto.CreateOrUpdateRequest request = new LabelSetDto.CreateOrUpdateRequest(meta, labels);

        LabelSet labelSet = new LabelSet(workspaceId, meta.name(), meta.description(), objectMapper.valueToTree(request));
        labelSet.setTags(meta.tags());
        labelSet.setSystem(true);
        labelSet = labelSetRepository.save(labelSet);

        return labelSet;
    }

    private List<LabelSetDto.Label> createPageXmlLabels() {
        List<LabelSetDto.Label> labels = new ArrayList<>();
        int counter = 1;

        // TextRegion goes in "Text" group
        labels.add(createRegionLabel(counter++, LabelSetDto.PageRegionType.TextRegion, "Text"));

        // Other regions have no group
        for (LabelSetDto.PageRegionType regionType : LabelSetDto.PageRegionType.values()) {
            if (regionType != LabelSetDto.PageRegionType.TextRegion) {
                labels.add(createRegionLabel(counter++, regionType, null));
            }
        }

        labels.add(createCustomRegionLabel(counter++));

        // TextRegion subtypes go in "Text" group
        int textIndex = 0;
        for (String textType : getTextRegionSubtypes()) {
            labels.add(createTextRegionSubtypeLabel(counter++, textType, "Text", getTextSubtypeColor(textIndex)));
            textIndex++;
        }

        labels.add(createTextLineLabel(counter++));

        return labels;
    }

    private LabelSetDto.Label createRegionLabel(int id, LabelSetDto.PageRegionType regionType, String group) {
        boolean hasText = isTextCapableRegion(regionType);
        String name = getRegionDisplayName(regionType);
        String description = getRegionDescription(regionType);
        String color = getRegionColor(regionType);

        LabelSetDto.PageXml pageXml = new LabelSetDto.PageXml(
                regionType,
                null,
                EMPTY_CUSTOM_SUBTYPE,
                "structure",
                ""
        );

        LabelSetDto.Mapping mapping = new LabelSetDto.Mapping(pageXml);

        return new LabelSetDto.Label(
                "page-region-" + id,
                LabelSetDto.LabelScope.REGION,
                name,
                description,
                color,
                hasText,
                false,
                group,
                mapping
        );
    }

    private void normalizePageXmlDefaults(List<LabelSet> allLabelSets) {
        for (LabelSet labelSet : allLabelSets) {
            if (!"PAGE XML Standard".equals(labelSet.getName())) {
                continue;
            }
            JsonNode sanitized = JsonNodeUtils.removeFieldRecursively(labelSet.getDefinition(), "icon").node();
            LabelSetDto.CreateOrUpdateRequest request = objectMapper.convertValue(sanitized, LabelSetDto.CreateOrUpdateRequest.class);
            List<LabelSetDto.Label> updatedLabels = new ArrayList<>();
            boolean changed = false;
            List<String> textTypes = getTextRegionSubtypes();

            for (LabelSetDto.Label label : request.labels()) {
                if (label.mapping() == null || label.mapping().pageXml() == null) {
                    updatedLabels.add(label);
                    continue;
                }

                LabelSetDto.PageXml pageXml = label.mapping().pageXml();
                LabelSetDto.PageRegionType regionType = pageXml.regionType();
                LabelSetDto.PageRegionType desiredRegionType = regionType;
                String desiredGroup = label.group();
                String desiredColor = label.color();
                String desiredCustomSubType = pageXml.customSubType();

                if (label.scope() == LabelSetDto.LabelScope.REGION && desiredCustomSubType == null) {
                    desiredCustomSubType = EMPTY_CUSTOM_SUBTYPE;
                }
                if (label.scope() == LabelSetDto.LabelScope.REGION
                        && regionType == null
                        && "custom".equals(desiredCustomSubType)) {
                    desiredRegionType = LabelSetDto.PageRegionType.UnknownRegion;
                }

                if (desiredRegionType == LabelSetDto.PageRegionType.TextRegion) {
                    desiredGroup = "Text";
                    if (pageXml.textType() == null || pageXml.textType().isBlank()) {
                        desiredColor = getRegionColor(LabelSetDto.PageRegionType.TextRegion);
                    } else {
                        int idx = textTypes.indexOf(pageXml.textType());
                        if (idx >= 0) {
                            desiredColor = getTextSubtypeColor(idx);
                        }
                    }
                } else if (desiredRegionType != null) {
                    desiredColor = getRegionColor(desiredRegionType);
                } else if ("custom".equals(pageXml.customSubType())) {
                    desiredColor = getCustomRegionColor();
                }
                if (desiredRegionType == LabelSetDto.PageRegionType.UnknownRegion && "custom".equals(desiredCustomSubType)) {
                    desiredColor = getCustomRegionColor();
                }

                boolean mappingChanged = pageXml.regionType() != desiredRegionType
                        || !equalsString(pageXml.customSubType(), desiredCustomSubType);
                LabelSetDto.Mapping desiredMapping = label.mapping();
                if (mappingChanged) {
                    LabelSetDto.PageXml desiredPageXml = new LabelSetDto.PageXml(
                            desiredRegionType,
                            pageXml.textType(),
                            desiredCustomSubType,
                            pageXml.customKey(),
                            pageXml.customData()
                    );
                    desiredMapping = new LabelSetDto.Mapping(desiredPageXml);
                }

                if (!equalsString(label.group(), desiredGroup) || !equalsString(label.color(), desiredColor) || mappingChanged) {
                    LabelSetDto.Label updatedLabel = new LabelSetDto.Label(
                            label.id(),
                            label.scope(),
                            label.name(),
                            label.description(),
                            desiredColor,
                            label.hasText(),
                            label.isContainer(),
                            desiredGroup,
                            desiredMapping
                    );
                    updatedLabels.add(updatedLabel);
                    changed = true;
                } else {
                    updatedLabels.add(label);
                }
            }

            if (changed) {
                LabelSetDto.CreateOrUpdateRequest updatedRequest = new LabelSetDto.CreateOrUpdateRequest(request.meta(), updatedLabels);
                labelSet.setDefinition(objectMapper.valueToTree(updatedRequest));
                labelSetRepository.save(labelSet);
            }
        }
    }

    private boolean equalsString(String left, String right) {
        if (left == null) return right == null;
        return left.equals(right);
    }

    private LabelSetDto.Label createCustomRegionLabel(int id) {
        String name = "Custom Region";
        String description = "Custom region type for content not covered by standard PAGE XML types";

        LabelSetDto.PageXml pageXml = new LabelSetDto.PageXml(
                LabelSetDto.PageRegionType.UnknownRegion,
                null,
                "custom",
                "structure",
                ""
        );

        LabelSetDto.Mapping mapping = new LabelSetDto.Mapping(pageXml);

        return new LabelSetDto.Label(
                "page-region-" + id,
                LabelSetDto.LabelScope.REGION,
                name,
                description,
                getCustomRegionColor(),
                false,
                false,
                null,
                mapping
        );
    }

    private LabelSetDto.Label createTextRegionSubtypeLabel(int id, String textType, String group, String color) {
        String name = formatTextTypeName(textType);
        String description = "TextRegion with subtype: " + textType;

        LabelSetDto.PageXml pageXml = new LabelSetDto.PageXml(
                LabelSetDto.PageRegionType.TextRegion,
                textType,
                EMPTY_CUSTOM_SUBTYPE,
                "structure",
                ""
        );

        LabelSetDto.Mapping mapping = new LabelSetDto.Mapping(pageXml);

        return new LabelSetDto.Label(
                "page-text-subtype-" + id,
                LabelSetDto.LabelScope.REGION,
                name,
                description,
                color,
                true,
                false,
                group,
                mapping
        );
    }

    private LabelSetDto.Label createTextLineLabel(int id) {
        LabelSetDto.PageXml pageXml = new LabelSetDto.PageXml(
                null,
                null,
                null,
                "structure",
                ""
        );

        LabelSetDto.Mapping mapping = new LabelSetDto.Mapping(pageXml);

        return new LabelSetDto.Label(
                "page-line-" + id,
                LabelSetDto.LabelScope.LINE,
                "Text Line",
                "A line of text within a text region",
                "#3B82F6",
                false,
                false,
                null,
                mapping
        );
    }

    private boolean isTextCapableRegion(LabelSetDto.PageRegionType regionType) {
        return regionType == LabelSetDto.PageRegionType.TextRegion;
    }

    private String getRegionDisplayName(LabelSetDto.PageRegionType regionType) {
        return switch (regionType) {
            case TextRegion -> "Text Region";
            case ImageRegion -> "Image Region";
            case LineDrawingRegion -> "Line Drawing Region";
            case GraphicRegion -> "Graphic Region";
            case TableRegion -> "Table Region";
            case ChartRegion -> "Chart Region";
            case MapRegion -> "Map Region";
            case SeparatorRegion -> "Separator Region";
            case MathsRegion -> "Maths Region";
            case ChemRegion -> "Chemistry Region";
            case MusicRegion -> "Music Region";
            case AdvertRegion -> "Advertisement Region";
            case NoiseRegion -> "Noise Region";
            case UnknownRegion -> "Unknown Region";
        };
    }

    private String getRegionDescription(LabelSetDto.PageRegionType regionType) {
        return switch (regionType) {
            case TextRegion -> "Pure text content region";
            case ImageRegion -> "Photographic or pictorial image region";
            case LineDrawingRegion -> "Single color illustration without solid areas";
            case GraphicRegion -> "Simple graphic such as a company logo";
            case TableRegion -> "Tabular data in any form";
            case ChartRegion -> "Chart or graph of any type";
            case MapRegion -> "Map or cartographic content";
            case SeparatorRegion -> "Line separating columns or paragraphs";
            case MathsRegion -> "Equations and mathematical symbols";
            case ChemRegion -> "Chemical formulas";
            case MusicRegion -> "Musical notations";
            case AdvertRegion -> "Advertisement content";
            case NoiseRegion -> "Noise or artifact (scanner noise, etc.)";
            case UnknownRegion -> "Region of unknown type";
        };
    }

    private String getRegionColor(LabelSetDto.PageRegionType regionType) {
        return switch (regionType) {
            case TextRegion -> "#4CAF50";
            case ImageRegion -> "#FF5722";
            case LineDrawingRegion -> "#607D8B";
            case GraphicRegion -> "#795548";
            case TableRegion -> "#2196F3";
            case ChartRegion -> "#9C27B0";
            case MapRegion -> "#8BC34A";
            case SeparatorRegion -> "#9E9E9E";
            case MathsRegion -> "#FF9800";
            case ChemRegion -> "#00BCD4";
            case MusicRegion -> "#CDDC39";
            case AdvertRegion -> "#E91E63";
            case NoiseRegion -> "#F44336";
            case UnknownRegion -> "#757575";
        };
    }

    private String getCustomRegionColor() {
        return "#00BFA5";
    }

    private String getTextSubtypeColor(int index) {
        return TEXT_SUBTYPE_COLORS.get(index % TEXT_SUBTYPE_COLORS.size());
    }

    private List<String> getTextRegionSubtypes() {
        return List.of(
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
                "other"
        );
    }

    private String formatTextTypeName(String textType) {
        String[] parts = textType.split("-");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                result.append(Character.toUpperCase(part.charAt(0)))
                      .append(part.substring(1))
                      .append(" ");
            }
        }
        return result.toString().trim();
    }
}

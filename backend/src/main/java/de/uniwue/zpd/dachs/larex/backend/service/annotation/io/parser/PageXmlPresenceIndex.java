package de.uniwue.zpd.dachs.larex.backend.service.annotation.io.parser;

import com.maxnth.page4j.dla.page.io.FileInput;
import com.maxnth.page4j.dla.page.io.xml.XmlElementOccurrence;
import com.maxnth.page4j.dla.page.io.xml.XmlPageReader;
import com.maxnth.page4j.dla.page.io.xml.XmlSourceMetadata;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adapts page4j source metadata to the sparse-presence lookups used by LAREX's PAGE DTO mapper and
 * filter index.
 */
public record PageXmlPresenceIndex(
    Set<String> pageAttributes,
    Map<String, Set<String>> attributesByElementId,
    boolean metadataCreator,
    boolean metadataCreated,
    boolean metadataLastChange,
    boolean metadataComments,
    boolean metadataExternalRef,
    Set<String> textRegionIdsWithType,
    Map<String, Set<Integer>> textEquivConfPositionsByElementId,
    Set<AttributeOccurrence> attributeOccurrences
) {

    /** Exact source attribute occurrence exposed by page4j before schema defaults are materialized. */
    public record AttributeOccurrence(String elementName, String attributeName, String value) {}

    public static PageXmlPresenceIndex empty() {
        return new PageXmlPresenceIndex(Set.of(), Map.of(), false, false, false, false, false, Set.of(), Map.of(), Set.of());
    }

    /** Read only source metadata; no page4j {@code Page} model is constructed or validated. */
    public static PageXmlPresenceIndex fromPath(Path xmlPath) {
        if (xmlPath == null) {
            return empty();
        }

        XmlSourceMetadata metadata = new XmlPageReader(null)
            .readSourceMetadata(new FileInput(xmlPath.toFile()));
        return fromSourceMetadata(metadata);
    }

    /** Build LAREX's specialized lookups from the immutable source metadata returned by page4j. */
    public static PageXmlPresenceIndex fromSourceMetadata(XmlSourceMetadata metadata) {
        if (metadata == null || metadata.elements().isEmpty()) {
            return empty();
        }

        Set<String> pageAttributes = new HashSet<>();
        Map<String, Set<String>> attributesByElementId = new HashMap<>();
        Set<String> textRegionIdsWithType = new HashSet<>();
        Map<String, Set<Integer>> textEquivConfPositionsByElementId = new HashMap<>();
        Set<AttributeOccurrence> attributeOccurrences = new HashSet<>();
        Map<Integer, Integer> nextTextEquivPositionByParent = new HashMap<>();
        boolean metadataCreator = false;
        boolean metadataCreated = false;
        boolean metadataLastChange = false;
        boolean metadataComments = false;
        boolean metadataExternalRef = false;

        List<XmlElementOccurrence> elements = metadata.elements();
        for (XmlElementOccurrence element : elements) {
            String elementName = element.name().getLocalPart();
            Map<String, String> attributes = attributesByLocalName(element);
            String elementId = attributes.get("id");

            element.attributes().forEach((attributeName, value) ->
                attributeOccurrences.add(new AttributeOccurrence(
                    elementName,
                    attributeName.getLocalPart(),
                    value
                )));

            if ("Page".equals(elementName)) {
                pageAttributes.addAll(attributes.keySet());
            }
            if (elementId != null && !elementId.isBlank()) {
                attributesByElementId.put(elementId, Set.copyOf(attributes.keySet()));
            }
            if ("Metadata".equals(elementName) && attributes.containsKey("externalRef")) {
                metadataExternalRef = true;
            } else if ("TextRegion".equals(elementName)
                && attributes.containsKey("type")
                && elementId != null
                && !elementId.isBlank()) {
                textRegionIdsWithType.add(elementId);
            } else if (isDirectChildOf(elements, element, "Metadata")) {
                metadataCreator |= "Creator".equals(elementName);
                metadataCreated |= "Created".equals(elementName);
                metadataLastChange |= "LastChange".equals(elementName);
                metadataComments |= "Comments".equals(elementName);
            }

            if ("TextEquiv".equals(elementName) && element.parentIndex() != null) {
                XmlElementOccurrence parent = elements.get(element.parentIndex());
                String parentId = attributeValue(parent, "id");
                if (parentId != null && !parentId.isBlank()) {
                    int position = nextTextEquivPositionByParent.merge(parent.index(), 1, Integer::sum) - 1;
                    if (attributes.containsKey("conf")) {
                        textEquivConfPositionsByElementId
                            .computeIfAbsent(parentId, ignored -> new HashSet<>())
                            .add(position);
                    }
                }
            }
        }

        return new PageXmlPresenceIndex(
            Set.copyOf(pageAttributes),
            copySetMap(attributesByElementId),
            metadataCreator,
            metadataCreated,
            metadataLastChange,
            metadataComments,
            metadataExternalRef,
            Set.copyOf(textRegionIdsWithType),
            copySetMap(textEquivConfPositionsByElementId),
            Set.copyOf(attributeOccurrences)
        );
    }

    public boolean hasMetadataCreator() {
        return metadataCreator;
    }

    public boolean hasMetadataCreated() {
        return metadataCreated;
    }

    public boolean hasMetadataLastChange() {
        return metadataLastChange;
    }

    public boolean hasMetadataComments() {
        return metadataComments;
    }

    public boolean hasMetadataExternalRef() {
        return metadataExternalRef;
    }

    public boolean hasTextRegionType(String regionId) {
        return regionId != null && textRegionIdsWithType.contains(regionId);
    }

    public boolean hasPageAttribute(String attribute) {
        return attribute != null && pageAttributes.contains(attribute);
    }

    public boolean hasAttributeForElementId(String elementId, String attribute) {
        if (elementId == null || attribute == null) {
            return false;
        }
        Set<String> attrs = attributesByElementId.get(elementId);
        return attrs != null && attrs.contains(attribute);
    }

    public boolean hasTextEquivConfidenceForElementId(String elementId, int textEquivPosition) {
        if (elementId == null || textEquivPosition < 0) {
            return false;
        }
        Set<Integer> positions = textEquivConfPositionsByElementId.get(elementId);
        return positions != null && positions.contains(textEquivPosition);
    }

    private static Map<String, String> attributesByLocalName(XmlElementOccurrence element) {
        Map<String, String> attributes = new HashMap<>();
        element.attributes().forEach((name, value) -> attributes.putIfAbsent(name.getLocalPart(), value));
        return attributes;
    }

    private static String attributeValue(XmlElementOccurrence element, String expectedName) {
        return element.attributes().entrySet().stream()
            .filter(entry -> expectedName.equals(entry.getKey().getLocalPart()))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
    }

    private static boolean isDirectChildOf(
        List<XmlElementOccurrence> elements,
        XmlElementOccurrence element,
        String parentName
    ) {
        return element.parentIndex() != null
            && parentName.equals(elements.get(element.parentIndex()).name().getLocalPart());
    }

    private static <T> Map<String, Set<T>> copySetMap(Map<String, Set<T>> source) {
        Map<String, Set<T>> copy = new HashMap<>();
        source.forEach((key, value) -> copy.put(key, Set.copyOf(value)));
        return Map.copyOf(copy);
    }
}

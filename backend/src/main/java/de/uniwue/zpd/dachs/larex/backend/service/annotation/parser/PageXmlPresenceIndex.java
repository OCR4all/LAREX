package de.uniwue.zpd.dachs.larex.backend.service.annotation.parser;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Captures field presence in source PAGE XML to avoid materializing schema defaults.
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
    Map<String, Set<Integer>> textEquivConfPositionsByElementId
) {

    public static PageXmlPresenceIndex empty() {
        return new PageXmlPresenceIndex(Set.of(), Map.of(), false, false, false, false, false, Set.of(), Map.of());
    }

    public static PageXmlPresenceIndex fromXml(String xml) {
        if (xml == null || xml.isBlank()) {
            return empty();
        }

        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            return fromDocument(document);
        } catch (Exception ignored) {
            return empty();
        }
    }

    private static PageXmlPresenceIndex fromDocument(Document document) {
        if (document == null) {
            return empty();
        }

        boolean metadataCreator = false;
        boolean metadataCreated = false;
        boolean metadataLastChange = false;
        boolean metadataComments = false;
        boolean metadataExternalRef = false;
        Set<String> pageAttributes = new HashSet<>();
        Map<String, Set<String>> attributesByElementId = new HashMap<>();
        Set<String> textRegionIdsWithType = new HashSet<>();
        Map<String, Set<Integer>> textEquivConfPositionsByElementId = new HashMap<>();

        NodeList allElements = document.getElementsByTagName("*");
        for (int i = 0; i < allElements.getLength(); i++) {
            Node node = allElements.item(i);
            if (!(node instanceof Element element)) {
                continue;
            }

            String name = localName(element);
            if ("Page".equals(name)) {
                pageAttributes.addAll(attributeNames(element));
            }
            String id = element.getAttribute("id");
            if (id != null && !id.isBlank()) {
                attributesByElementId.put(id, attributeNames(element));
                Set<Integer> confPositions = textEquivConfidencePositions(element);
                if (!confPositions.isEmpty()) {
                    textEquivConfPositionsByElementId.put(id, confPositions);
                }
            }
            if ("Metadata".equals(name)) {
                metadataExternalRef |= element.hasAttribute("externalRef");
                NodeList metadataChildren = element.getChildNodes();
                for (int j = 0; j < metadataChildren.getLength(); j++) {
                    Node child = metadataChildren.item(j);
                    if (!(child instanceof Element childElement)) {
                        continue;
                    }
                    String childName = localName(childElement);
                    if ("Creator".equals(childName)) {
                        metadataCreator = true;
                    } else if ("Created".equals(childName)) {
                        metadataCreated = true;
                    } else if ("LastChange".equals(childName)) {
                        metadataLastChange = true;
                    } else if ("Comments".equals(childName)) {
                        metadataComments = true;
                    }
                }
            } else if ("TextRegion".equals(name) && element.hasAttribute("type")) {
                String regionId = element.getAttribute("id");
                if (regionId != null && !regionId.isBlank()) {
                    textRegionIdsWithType.add(regionId);
                }
            }
        }

        return new PageXmlPresenceIndex(
            Set.copyOf(pageAttributes),
            copyAttributesByElementId(attributesByElementId),
            metadataCreator,
            metadataCreated,
            metadataLastChange,
            metadataComments,
            metadataExternalRef,
            Set.copyOf(textRegionIdsWithType),
            copyTextEquivConfPositionsByElementId(textEquivConfPositionsByElementId)
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

    private static Set<String> attributeNames(Element element) {
        Set<String> names = new HashSet<>();
        NamedNodeMap attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node attr = attrs.item(i);
            if (attr == null) {
                continue;
            }
            String name = localName(attr.getNodeName());
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }
        return names;
    }

    private static Map<String, Set<String>> copyAttributesByElementId(Map<String, Set<String>> source) {
        Map<String, Set<String>> copy = new HashMap<>();
        source.forEach((key, value) -> copy.put(key, Set.copyOf(value)));
        return Map.copyOf(copy);
    }

    private static Set<Integer> textEquivConfidencePositions(Element element) {
        Set<Integer> positions = new HashSet<>();
        NodeList children = element.getChildNodes();
        int textEquivPos = 0;
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!(child instanceof Element childElement)) {
                continue;
            }
            if (!"TextEquiv".equals(localName(childElement))) {
                continue;
            }
            if (childElement.hasAttribute("conf")) {
                positions.add(textEquivPos);
            }
            textEquivPos++;
        }
        return positions;
    }

    private static Map<String, Set<Integer>> copyTextEquivConfPositionsByElementId(Map<String, Set<Integer>> source) {
        Map<String, Set<Integer>> copy = new HashMap<>();
        source.forEach((key, value) -> copy.put(key, Set.copyOf(value)));
        return Map.copyOf(copy);
    }

    private static String localName(Element element) {
        String local = element.getLocalName();
        if (local != null && !local.isBlank()) {
            return local;
        }
        String nodeName = element.getNodeName();
        int index = nodeName.indexOf(':');
        return index >= 0 ? nodeName.substring(index + 1) : nodeName;
    }

    private static String localName(String nodeName) {
        if (nodeName == null || nodeName.isBlank()) {
            return nodeName;
        }
        int index = nodeName.indexOf(':');
        return index >= 0 ? nodeName.substring(index + 1) : nodeName;
    }
}

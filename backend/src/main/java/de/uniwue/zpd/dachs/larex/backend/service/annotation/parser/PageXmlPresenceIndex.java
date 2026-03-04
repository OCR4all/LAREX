package de.uniwue.zpd.dachs.larex.backend.service.annotation.parser;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
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

    public static PageXmlPresenceIndex fromPath(Path xmlPath) {
        if (xmlPath == null) {
            return empty();
        }

        try (InputStream inputStream = Files.newInputStream(xmlPath)) {
            return parse(new InputSource(inputStream));
        } catch (Exception ignored) {
            return empty();
        }
    }

    public static PageXmlPresenceIndex fromXml(String xml) {
        if (xml == null || xml.isBlank()) {
            return empty();
        }

        try {
            return parse(new InputSource(new StringReader(xml)));
        } catch (Exception ignored) {
            return empty();
        }
    }

    private static PageXmlPresenceIndex parse(InputSource inputSource) throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        PresenceHandler handler = new PresenceHandler();
        factory.newSAXParser().parse(inputSource, handler);
        return handler.toIndex();
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

    private static Map<String, Set<String>> copyAttributesByElementId(Map<String, Set<String>> source) {
        Map<String, Set<String>> copy = new HashMap<>();
        source.forEach((key, value) -> copy.put(key, Set.copyOf(value)));
        return Map.copyOf(copy);
    }

    private static Map<String, Set<Integer>> copyTextEquivConfPositionsByElementId(Map<String, Set<Integer>> source) {
        Map<String, Set<Integer>> copy = new HashMap<>();
        source.forEach((key, value) -> copy.put(key, Set.copyOf(value)));
        return Map.copyOf(copy);
    }

    private static String localName(String localName, String qName) {
        if (localName != null && !localName.isBlank()) {
            return localName;
        }
        if (qName == null || qName.isBlank()) {
            return qName;
        }
        int index = qName.indexOf(':');
        return index >= 0 ? qName.substring(index + 1) : qName;
    }

    private static final class PresenceHandler extends DefaultHandler {
        private boolean metadataCreator;
        private boolean metadataCreated;
        private boolean metadataLastChange;
        private boolean metadataComments;
        private boolean metadataExternalRef;
        private final Set<String> pageAttributes = new HashSet<>();
        private final Map<String, Set<String>> attributesByElementId = new HashMap<>();
        private final Set<String> textRegionIdsWithType = new HashSet<>();
        private final Map<String, Set<Integer>> textEquivConfPositionsByElementId = new HashMap<>();
        private final Deque<ElementState> stack = new ArrayDeque<>();

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            String name = localName(localName, qName);
            String elementId = attrValue(attributes, "id");
            Set<String> attributeNames = attributeNames(attributes);

            if ("Page".equals(name)) {
                pageAttributes.addAll(attributeNames);
            }
            if (elementId != null && !elementId.isBlank()) {
                attributesByElementId.put(elementId, attributeNames);
            }
            if ("Metadata".equals(name) && hasAttribute(attributes, "externalRef")) {
                metadataExternalRef = true;
            } else if ("TextRegion".equals(name) && hasAttribute(attributes, "type") && elementId != null && !elementId.isBlank()) {
                textRegionIdsWithType.add(elementId);
            } else if ("Creator".equals(name) && isInside("Metadata")) {
                metadataCreator = true;
            } else if ("Created".equals(name) && isInside("Metadata")) {
                metadataCreated = true;
            } else if ("LastChange".equals(name) && isInside("Metadata")) {
                metadataLastChange = true;
            } else if ("Comments".equals(name) && isInside("Metadata")) {
                metadataComments = true;
            } else if ("TextEquiv".equals(name) && !stack.isEmpty()) {
                ElementState parent = stack.peek();
                if (parent != null && parent.id != null && !parent.id.isBlank()) {
                    int position = parent.nextTextEquivPosition++;
                    if (hasAttribute(attributes, "conf")) {
                        textEquivConfPositionsByElementId
                                .computeIfAbsent(parent.id, ignored -> new HashSet<>())
                                .add(position);
                    }
                }
            }

            stack.push(new ElementState(name, elementId));
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if (!stack.isEmpty()) {
                stack.pop();
            }
        }

        private boolean isInside(String expectedParentName) {
            return !stack.isEmpty() && expectedParentName.equals(stack.peek().name);
        }

        private boolean hasAttribute(Attributes attributes, String expectedName) {
            return attrValue(attributes, expectedName) != null;
        }

        private String attrValue(Attributes attributes, String expectedName) {
            for (int i = 0; i < attributes.getLength(); i++) {
                String name = localName(attributes.getLocalName(i), attributes.getQName(i));
                if (expectedName.equals(name)) {
                    return attributes.getValue(i);
                }
            }
            return null;
        }

        private Set<String> attributeNames(Attributes attributes) {
            Set<String> names = new HashSet<>();
            for (int i = 0; i < attributes.getLength(); i++) {
                String name = localName(attributes.getLocalName(i), attributes.getQName(i));
                if (name != null && !name.isBlank()) {
                    names.add(name);
                }
            }
            return names;
        }

        private PageXmlPresenceIndex toIndex() {
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
    }

    private static final class ElementState {
        private final String name;
        private final String id;
        private int nextTextEquivPosition;

        private ElementState(String name, String id) {
            this.name = name;
            this.id = id;
        }
    }
}

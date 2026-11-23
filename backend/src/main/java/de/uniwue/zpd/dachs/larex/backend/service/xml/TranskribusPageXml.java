package de.uniwue.zpd.dachs.larex.backend.service.xml;

import com.maxnth.page4j.convert.PageConverter;
import com.maxnth.page4j.model.Label;
import com.maxnth.page4j.model.LabelGroup;
import com.maxnth.page4j.model.MetadataItem;
import com.maxnth.page4j.model.Page;
import com.maxnth.page4j.model.TableCellRole;
import com.maxnth.page4j.model.TextRegion;
import com.maxnth.page4j.model.value.MetadataItemType;
import com.maxnth.page4j.model.value.PageDateTime;
import com.maxnth.page4j.pagexml.PageXml;
import com.maxnth.page4j.pagexml.PageXmlReadResult;
import de.uniwue.zpd.dachs.larex.backend.dto.PageXmlTextDto;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.exception.InvalidPageXmlException;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class TranskribusPageXml {

    public static final String IMPORT_STEP_NAME = "LAREX Transkribus import";
    public static final String METADATA_ITEM_NAME = "Transkribus";
    public static final String IMAGE_TRANSFORM_MODEL = "TranskribusImageTransform";

    private static final String PAGE_NAMESPACE_PREFIX = XmlSchema.PAGE_XML.getNamespace();
    private static final List<String> KEPT_METADATA_ATTRIBUTES =
            List.of("docId", "pageId", "pageNr", "tsid", "imageId", "status");
    private static final List<String> PAGE_EXTENSION_ATTRIBUTES = List.of(
            "imageRotation", "imageTranslationX", "imageTranslationY", "imageScalingX", "imageScalingY");
    private static final Set<String> EXTENSION_ELEMENTS = Set.of("Tag", "Property", "Link");
    private static final Set<String> TABLE_REGION_ATTRIBUTES = Set.of(
            "id", "custom", "comments", "continuation", "orientation", "mirrored", "rows", "columns",
            "lineColour", "bgColour", "lineSeparators", "embText");
    private static final Set<String> TABLE_CELL_ATTRIBUTES = Set.of(
            "row", "col", "rowSpan", "colSpan", "label",
            "leftBorderVisible", "rightBorderVisible", "topBorderVisible", "bottomBorderVisible");
    private static final List<String> TIMESTAMP_ELEMENTS = List.of("Created", "LastChange");
    private static final Pattern LENIENT_TIMESTAMP = Pattern.compile(
            "(\\d{4})-(\\d{1,2})-(\\d{1,2})[T ](\\d{1,2}):(\\d{1,2})(?::(\\d{1,2}))?(?:\\.(\\d*))?\\s*(?:(Z)|([+-]\\d{2}):?(\\d{2}))?");
    private static final Set<String> READING_ORDER_GROUPS = Set.of(
            "OrderedGroup", "UnorderedGroup", "OrderedGroupIndexed", "UnorderedGroupIndexed");
    private static final String NAMESPACE_2013 = PAGE_NAMESPACE_PREFIX + "2013-07-15";
    private static final String NAMESPACE_2019 = PAGE_NAMESPACE_PREFIX + "2019-07-15";
    private static final Map<String, String> SCRIPTS_2019 = Map.ofEntries(
            Map.entry("Arabic", "Arab - Arabic"),
            Map.entry("Bengali", "Beng - Bengali"),
            Map.entry("Chinese-simplified", "Hans - Han (Simplified variant)"),
            Map.entry("Chinese-traditional", "Hant - Han (Traditional variant)"),
            Map.entry("Cyrillic", "Cyrl - Cyrillic"),
            Map.entry("Devangari", "Deva - Devanagari (Nagari)"),
            Map.entry("Ethiopic", "Ethi - Ethiopic"),
            Map.entry("Greek", "Grek - Greek"),
            Map.entry("Gujarati", "Gujr - Gujarati"),
            Map.entry("Gurmukhi", "Guru - Gurmukhi"),
            Map.entry("Hebrew", "Hebr - Hebrew"),
            Map.entry("Latin", "Latn - Latin"),
            Map.entry("Thai", "Thai - Thai"));

    private TranskribusPageXml() {
    }

    public static boolean isExport(byte[] xml) {
        XMLStreamReader reader = null;
        try {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            reader = factory.createXMLStreamReader(new ByteArrayInputStream(xml));
            boolean root = true;
            while (reader.hasNext()) {
                if (reader.next() != XMLStreamConstants.START_ELEMENT) {
                    continue;
                }
                String name = reader.getLocalName();
                if (root) {
                    String namespace = reader.getNamespaceURI();
                    if (!"PcGts".equals(name) || namespace == null || !namespace.startsWith(PAGE_NAMESPACE_PREFIX)) {
                        return false;
                    }
                    root = false;
                } else if ("TranskribusMetadata".equals(name) || "TableCell".equals(name)) {
                    return true;
                } else if ("Creator".equals(name) && isTranskribusCreator(reader.getElementText())) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static boolean isTranskribusCreator(String creator) {
        String text = creator.trim();
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("transkribus") || lower.contains("read-coop") || text.equals("TRP") || text.startsWith("TRP ");
    }

    public static Optional<Converted> normalize(byte[] xml, Consumer<byte[]> assertValid) {
        if (!isExport(xml)) {
            return Optional.empty();
        }
        Document document = parse(xml);
        List<String> messages = new ArrayList<>();
        Extracted extracted = repair(document, messages);
        byte[] repaired = messages.isEmpty() ? xml : serialize(document);
        if (NAMESPACE_2013.equals(document.getDocumentElement().getNamespaceURI()) && !isValid(repaired, assertValid)) {
            upgradeTo2019(document);
            messages.add("Read as PAGE XML 2019-07-15 because the export uses content that PAGE XML 2013-07-15 does not allow.");
            repaired = serialize(document);
        }
        if (messages.isEmpty()) {
            return Optional.empty();
        }
        assertValid.accept(repaired);
        return Optional.of(new Converted(toPrimaryPageXml(repaired, extracted, messages), List.copyOf(messages)));
    }

    private static boolean isValid(byte[] xml, Consumer<byte[]> assertValid) {
        try {
            assertValid.accept(xml);
            return true;
        } catch (InvalidPageXmlException e) {
            return false;
        }
    }

    private static Extracted repair(Document document, List<String> messages) {
        Element root = document.getDocumentElement();
        String namespace = root.getNamespaceURI();

        Map<String, String> transkribusMetadata = new LinkedHashMap<>();
        for (Element metadata : elements(root, null, "TranskribusMetadata")) {
            for (String attribute : KEPT_METADATA_ATTRIBUTES) {
                if (metadata.hasAttribute(attribute)) {
                    transkribusMetadata.put(attribute, metadata.getAttribute(attribute));
                }
            }
            metadata.getParentNode().removeChild(metadata);
            messages.add("Removed TranskribusMetadata from Metadata"
                    + (transkribusMetadata.isEmpty() ? "." : " and kept its document and page IDs as a metadata item."));
        }

        int extensionElements = 0;
        for (String name : EXTENSION_ELEMENTS) {
            for (Element extension : elements(root, namespace, name)) {
                extension.getParentNode().removeChild(extension);
                extensionElements++;
            }
        }
        if (extensionElements > 0) {
            messages.add("Removed " + extensionElements + " Transkribus Tag, Property or Link elements.");
        }

        repairTimestamps(root, messages);

        Element page = elements(root, namespace, "Page").stream().findFirst().orElse(null);
        Map<String, String> imageTransform = new LinkedHashMap<>();
        if (page != null) {
            for (String attribute : PAGE_EXTENSION_ATTRIBUTES) {
                if (page.hasAttribute(attribute)) {
                    imageTransform.put(attribute, page.getAttribute(attribute));
                    page.removeAttribute(attribute);
                }
            }
            if (!imageTransform.isEmpty()) {
                messages.add("Moved Transkribus attributes " + String.join(", ", imageTransform.keySet())
                        + " from Page to labels of the page.");
            }
        }

        repairReadingOrder(root, messages);
        repairRelations(document, messages);

        Map<String, TableCellRole> cellRoles = new LinkedHashMap<>();
        for (Element table : elements(root, namespace, "TableRegion")) {
            List<String> removed = removeAttributes(table, name -> !TABLE_REGION_ATTRIBUTES.contains(name));
            if (!removed.isEmpty()) {
                messages.add("Removed Transkribus attributes " + String.join(", ", removed)
                        + " from table region " + table.getAttribute("id") + ".");
            }
            convertCells(document, table, cellRoles, messages);
        }

        convertTextAlternatives(document, messages);

        int clamped = 0;
        int width = page == null ? 0 : intAttribute(page, "imageWidth", 0);
        int height = page == null ? 0 : intAttribute(page, "imageHeight", 0);
        for (Element element : elements(root, namespace, "*")) {
            String points = element.getAttribute("points");
            String normalized = normalizePoints(points, width, height);
            if (normalized != null && !normalized.equals(points)) {
                element.setAttribute("points", normalized);
                clamped++;
            }
        }
        if (clamped > 0) {
            messages.add("Moved points outside the page image onto its edge or rounded them to whole pixels in "
                    + clamped + " outlines, baselines and table grid rows.");
        }

        return new Extracted(transkribusMetadata, imageTransform, cellRoles);
    }

    private static void repairTimestamps(Element root, List<String> messages) {
        for (String name : TIMESTAMP_ELEMENTS) {
            for (Element element : elements(root, root.getNamespaceURI(), name)) {
                String timestamp = element.getTextContent();
                String normalized = normalizeTimestamp(timestamp);
                if (normalized != null && !normalized.equals(timestamp.strip())) {
                    element.setTextContent(normalized);
                    messages.add("Rewrote the " + name + " timestamp " + timestamp.strip() + " as " + normalized + ".");
                }
            }
        }
    }

    static String normalizeTimestamp(String timestamp) {
        String text = timestamp.strip();
        if (isTimestamp(text)) {
            return text;
        }
        Matcher matcher = LENIENT_TIMESTAMP.matcher(text);
        if (!matcher.matches()) {
            return null;
        }
        String fraction = matcher.group(7);
        String offset = "";
        if (matcher.group(8) != null) {
            offset = matcher.group(8);
        } else if (matcher.group(9) != null) {
            offset = matcher.group(9) + ":" + matcher.group(10);
        }
        String normalized = matcher.group(1) + "-" + twoDigits(matcher.group(2)) + "-" + twoDigits(matcher.group(3))
                + "T" + twoDigits(matcher.group(4)) + ":" + twoDigits(matcher.group(5))
                + ":" + twoDigits(matcher.group(6))
                + (fraction == null || fraction.isEmpty() ? "" : "." + fraction)
                + offset;
        return isTimestamp(normalized) ? normalized : null;
    }

    private static boolean isTimestamp(String text) {
        try {
            PageDateTime.parse(text);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String twoDigits(String digits) {
        return digits == null ? "00" : digits.length() == 1 ? "0" + digits : digits;
    }

    private static void repairReadingOrder(Element root, List<String> messages) {
        String namespace = root.getNamespaceURI();
        int removed = 0;
        for (Element readingOrder : elements(root, namespace, "ReadingOrder")) {
            removed += removeEmptyGroups(readingOrder);
            if (childElements(readingOrder).isEmpty()) {
                readingOrder.getParentNode().removeChild(readingOrder);
                removed++;
            }
        }
        if (removed > 0) {
            messages.add("Removed " + removed + " empty reading order elements.");
        }
    }

    private static int removeEmptyGroups(Element parent) {
        int removed = 0;
        for (Element group : childElements(parent)) {
            if (!READING_ORDER_GROUPS.contains(group.getLocalName())) {
                continue;
            }
            removed += removeEmptyGroups(group);
            if (childElements(group).stream().allMatch(child -> "UserDefined".equals(child.getLocalName()))) {
                parent.removeChild(group);
                removed++;
            }
        }
        return removed;
    }

    private static void repairRelations(Document document, List<String> messages) {
        Element root = document.getDocumentElement();
        for (Element relations : elements(root, root.getNamespaceURI(), "Relations")) {
            for (Element relation : childElements(relations, "Relation")) {
                List<Element> references = childElements(relation, "RegionRef");
                if (references.size() <= 2) {
                    continue;
                }
                for (int i = 0; i + 1 < references.size(); i++) {
                    Element pair = (Element) relation.cloneNode(false);
                    if (i > 0) {
                        pair.removeAttribute("id");
                    }
                    pair.appendChild(references.get(i).cloneNode(true));
                    pair.appendChild(references.get(i + 1).cloneNode(true));
                    relations.insertBefore(pair, relation);
                }
                relations.removeChild(relation);
                messages.add("Split a relation between " + references.size() + " objects into "
                        + (references.size() - 1) + " relations between two objects.");
            }
            if (childElements(relations).isEmpty()) {
                relations.getParentNode().removeChild(relations);
                messages.add("Removed empty Relations.");
            }
        }
    }

    private static void convertTextAlternatives(Document document, List<String> messages) {
        Element root = document.getDocumentElement();
        String namespace = root.getNamespaceURI();
        int converted = 0;
        for (Element textEquiv : elements(root, namespace, "TextEquiv")) {
            List<Element> alternatives = childElements(textEquiv, "UnicodeAlternative");
            if (alternatives.isEmpty()) {
                continue;
            }
            Element owner = (Element) textEquiv.getParentNode();
            int index = childElements(owner, "TextEquiv").stream()
                    .mapToInt(sibling -> intAttribute(sibling, "index", 0))
                    .max()
                    .orElse(0);
            Node next = textEquiv.getNextSibling();
            for (Element alternative : alternatives) {
                textEquiv.removeChild(alternative);
                Element unicode = document.createElementNS(namespace, qualifiedName(textEquiv, "Unicode"));
                unicode.setTextContent(alternative.getTextContent());
                Element variant = document.createElementNS(namespace, qualifiedName(textEquiv, "TextEquiv"));
                variant.setAttribute("index", Integer.toString(++index));
                variant.appendChild(unicode);
                owner.insertBefore(variant, next);
                converted++;
            }
        }
        if (converted > 0) {
            messages.add("Converted " + converted + " Transkribus text alternatives to indexed TextEquiv elements.");
        }
    }

    private static void upgradeTo2019(Document document) {
        Element root = document.getDocumentElement();
        root.removeAttributeNS(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "schemaLocation");
        List<Element> all = new ArrayList<>(elements(root, "*", "*"));
        all.add(root);
        for (Element element : all) {
            NamedNodeMap attributes = element.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                Attr attribute = (Attr) attributes.item(i);
                if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attribute.getNamespaceURI())
                        && NAMESPACE_2013.equals(attribute.getValue())) {
                    attribute.setValue(NAMESPACE_2019);
                }
            }
            for (String name : List.of("primaryScript", "secondaryScript")) {
                String script = SCRIPTS_2019.get(element.getAttribute(name));
                if (script != null) {
                    element.setAttribute(name, script);
                }
            }
            if (NAMESPACE_2013.equals(element.getNamespaceURI())) {
                document.renameNode(element, NAMESPACE_2019, element.getNodeName());
            }
        }
        upgradeRelationsTo2019(document);
    }

    private static void upgradeRelationsTo2019(Document document) {
        Element root = document.getDocumentElement();
        Set<String> ids = elements(root, "*", "*").stream()
                .map(element -> element.getAttribute("id"))
                .collect(Collectors.toSet());
        int next = 1;
        for (Element relation : elements(root, NAMESPACE_2019, "Relation")) {
            if (!relation.hasAttribute("id")) {
                while (!ids.add("relation_" + next)) {
                    next++;
                }
                relation.setAttribute("id", "relation_" + next);
            }
            List<Element> references = childElements(relation, "RegionRef");
            if (references.size() == 2) {
                document.renameNode(references.get(0), NAMESPACE_2019, qualifiedName(relation, "SourceRegionRef"));
                document.renameNode(references.get(1), NAMESPACE_2019, qualifiedName(relation, "TargetRegionRef"));
            }
        }
    }

    private static void convertCells(Document document,
                                     Element table,
                                     Map<String, TableCellRole> cellRoles,
                                     List<String> messages) {
        String namespace = table.getNamespaceURI();
        int rows = 0;
        int columns = 0;
        int converted = 0;
        for (Element cell : childElements(table, "TableCell")) {
            Element region = document.createElementNS(namespace, qualifiedName(cell, "TextRegion"));
            NamedNodeMap attributes = cell.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                Attr attribute = (Attr) attributes.item(i);
                if (attribute.getNamespaceURI() == null && TABLE_CELL_ATTRIBUTES.contains(attribute.getName())) {
                    continue;
                }
                region.setAttributeNodeNS((Attr) attribute.cloneNode(true));
            }
            while (cell.getFirstChild() != null) {
                Node child = cell.getFirstChild();
                cell.removeChild(child);
                if (!(child instanceof Element element && "CornerPts".equals(element.getLocalName()))) {
                    region.appendChild(child);
                }
            }
            table.replaceChild(region, cell);
            converted++;

            Integer row = integer(cell.getAttribute("row"));
            Integer column = integer(cell.getAttribute("col"));
            if (row == null || column == null || row < 0 || column < 0) {
                continue;
            }
            int rowSpan = Math.max(1, intAttribute(cell, "rowSpan", 1));
            int colSpan = Math.max(1, intAttribute(cell, "colSpan", 1));
            cellRoles.put(region.getAttribute("id"), new TableCellRole(row, column,
                    rowSpan == 1 ? null : rowSpan, colSpan == 1 ? null : colSpan,
                    isTrue(cell.getAttribute("label")) ? true : null));
            rows = Math.max(rows, row + rowSpan);
            columns = Math.max(columns, column + colSpan);
        }
        if (converted == 0) {
            return;
        }
        if (!table.hasAttribute("rows") && rows > 0) {
            table.setAttribute("rows", Integer.toString(rows));
        }
        if (!table.hasAttribute("columns") && columns > 0) {
            table.setAttribute("columns", Integer.toString(columns));
        }
        messages.add("Converted " + converted + " Transkribus table cells of table region "
                + table.getAttribute("id") + " to text regions with table cell roles.");
    }

    static String normalizePoints(String points, int width, int height) {
        if (points == null || points.isBlank()) {
            return null;
        }
        StringBuilder normalized = new StringBuilder();
        for (String point : points.trim().split("\\s+")) {
            int comma = point.indexOf(',');
            if (comma < 0) {
                return null;
            }
            Long x = coordinate(point.substring(0, comma), width);
            Long y = coordinate(point.substring(comma + 1), height);
            if (x == null || y == null) {
                return null;
            }
            if (!normalized.isEmpty()) {
                normalized.append(' ');
            }
            normalized.append(x).append(',').append(y);
        }
        return normalized.toString();
    }

    private static Long coordinate(String value, int bound) {
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed)) {
                return null;
            }
            long rounded = Math.max(0, Math.round(parsed));
            return bound > 0 ? Math.min(rounded, bound) : rounded;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static byte[] toPrimaryPageXml(byte[] xml, Extracted extracted, List<String> messages) {
        try {
            PageXmlReadResult read = PageXmlVersionGate.requireReadable(PageXml.create().read(xml));
            List<String> notes = new ArrayList<>(messages);
            Page page = read.page();
            if (read.version() != PageXmlConversionService.PRIMARY_VERSION) {
                var conversion = PageConverter.convert(page, PageXmlConversionService.PRIMARY_VERSION);
                page = conversion.page();
                conversion.messages().forEach(message -> notes.add(message.kind() + ": " + message.message()));
            }
            for (Map.Entry<String, TableCellRole> cell : extracted.cellRoles().entrySet()) {
                Optional<TextRegion> region = page.find(cell.getKey(), TextRegion.class);
                if (region.isPresent()) {
                    page = page.replace(cell.getKey(), region.get().withTableCellRole(cell.getValue()));
                }
            }
            if (!extracted.imageTransform().isEmpty()) {
                List<LabelGroup> labels = new ArrayList<>(page.labels());
                labels.add(new LabelGroup(IMAGE_TRANSFORM_MODEL, null, null, null, extracted.imageTransform().entrySet().stream()
                        .map(attribute -> new Label(attribute.getValue(), attribute.getKey(), null))
                        .toList()));
                page = page.toBuilder().labels(labels).build();
            }
            page = withMetadata(page, extracted.transkribusMetadata(), notes);
            return PageXml.create().write(page, PageXmlConversionService.PRIMARY_VERSION);
        } catch (IOException | RuntimeException e) {
            throw invalid("CONVERSION_ERROR", "Could not convert the Transkribus export to PAGE XML: " + e.getMessage());
        }
    }

    private static Page withMetadata(Page page, Map<String, String> transkribusMetadata, List<String> notes) {
        List<MetadataItem> items = new ArrayList<>(page.metadata().metadataItems());
        if (!transkribusMetadata.isEmpty()) {
            List<Label> labels = new ArrayList<>();
            for (String attribute : KEPT_METADATA_ATTRIBUTES) {
                String value = transkribusMetadata.get(attribute);
                if (value != null) {
                    labels.add(new Label(value, attribute, null));
                }
            }
            items.add(new MetadataItem(MetadataItemType.OTHER, METADATA_ITEM_NAME, "Exported from Transkribus", null,
                    List.of(new LabelGroup(null, null, null, null, labels))));
        }
        items.add(new MetadataItem(
                MetadataItemType.PROCESSING_STEP,
                IMPORT_STEP_NAME,
                "Normalized a Transkribus PAGE XML export to valid PAGE XML. "
                        + "Transkribus content without a PAGE equivalent was not kept.",
                PageDateTime.now(),
                List.of(new LabelGroup(null, null, null, null, notes.stream()
                        .map(note -> new Label(note, OcrFormatToPageXml.CONVERSION_NOTE_LABEL, null))
                        .toList()))));
        return page.withMetadata(page.metadata().withMetadataItems(items));
    }

    private static Document parse(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        } catch (Exception e) {
            throw invalid("READ_ERROR", "Could not read the Transkribus export: " + e.getMessage());
        }
    }

    private static byte[] serialize(Document document) {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            document.setXmlStandalone(true);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(document), new StreamResult(output));
            return output.toByteArray();
        } catch (Exception e) {
            throw invalid("CONVERSION_ERROR", "Could not write the normalized Transkribus export: " + e.getMessage());
        }
    }

    private static List<Element> elements(Element root, String namespace, String localName) {
        NodeList nodes = root.getElementsByTagNameNS(namespace == null ? "*" : namespace, localName);
        List<Element> result = new ArrayList<>(nodes.getLength());
        for (int i = 0; i < nodes.getLength(); i++) {
            result.add((Element) nodes.item(i));
        }
        return result;
    }

    private static List<Element> childElements(Element parent, String localName) {
        return childElements(parent).stream()
                .filter(element -> localName.equals(element.getLocalName())
                        && parent.getNamespaceURI().equals(element.getNamespaceURI()))
                .toList();
    }

    private static List<Element> childElements(Element parent) {
        List<Element> result = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element) {
                result.add(element);
            }
        }
        return result;
    }

    private static List<String> removeAttributes(Element element, Predicate<String> remove) {
        List<String> removed = new ArrayList<>();
        NamedNodeMap attributes = element.getAttributes();
        for (int i = attributes.getLength() - 1; i >= 0; i--) {
            Attr attribute = (Attr) attributes.item(i);
            if (attribute.getNamespaceURI() == null && remove.test(attribute.getName())) {
                removed.add(0, attribute.getName());
                element.removeAttributeNode(attribute);
            }
        }
        return removed;
    }

    private static String qualifiedName(Element template, String localName) {
        String prefix = template.getPrefix();
        return prefix == null || prefix.isEmpty() ? localName : prefix + ":" + localName;
    }

    private static int intAttribute(Element element, String name, int fallback) {
        Integer value = integer(element.getAttribute(name));
        return value == null ? fallback : value;
    }

    private static boolean isTrue(String value) {
        return "true".equals(value.trim()) || "1".equals(value.trim());
    }

    private static Integer integer(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static InvalidPageXmlException invalid(String code, String message) {
        return new InvalidPageXmlException(new PageXmlTextDto.XmlValidationResult(
                false,
                List.of(new PageXmlTextDto.XmlValidationError(1, 1, "error", "TRANSKRIBUS_" + code, message)),
                null,
                XmlSchema.PAGE_XML.getNamespace()));
    }

    public record Converted(byte[] pageXml, List<String> messages) {
    }

    private record Extracted(Map<String, String> transkribusMetadata,
                             Map<String, String> imageTransform,
                             Map<String, TableCellRole> cellRoles) {
    }
}

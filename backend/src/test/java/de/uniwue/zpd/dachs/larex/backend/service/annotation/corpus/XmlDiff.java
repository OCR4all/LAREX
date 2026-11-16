package de.uniwue.zpd.dachs.larex.backend.service.annotation.corpus;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class XmlDiff {

    public enum Kind {
        MISSING_ELEMENT,
        EXTRA_ELEMENT,
        MISSING_ATTRIBUTE,
        EXTRA_ATTRIBUTE,
        CHANGED_ATTRIBUTE,
        CHANGED_TEXT
    }

    public record Difference(Kind kind, String path, String expected, String actual) {
        @Override
        public String toString() {
            return switch (kind) {
                case MISSING_ELEMENT, EXTRA_ELEMENT -> kind + " " + path;
                case MISSING_ATTRIBUTE -> kind + " " + path + " " + quote(expected);
                case EXTRA_ATTRIBUTE -> kind + " " + path + " " + quote(actual);
                case CHANGED_ATTRIBUTE, CHANGED_TEXT -> kind + " " + path + " " + quote(expected) + " -> " + quote(actual);
            };
        }

        private static String quote(String value) {
            return '"' + value.replace("\\", "\\\\").replace("\n", "\\n").replace("\"", "\\\"") + '"';
        }
    }

    private final boolean ignoreElementNamespaces;

    private XmlDiff(boolean ignoreElementNamespaces) {
        this.ignoreElementNamespaces = ignoreElementNamespaces;
    }

    public static List<Difference> compare(String expected, String actual) throws Exception {
        return new XmlDiff(false).compare(parse(expected), parse(actual));
    }

    public static List<Difference> compareIgnoringElementNamespaces(String expected, String actual) throws Exception {
        return new XmlDiff(true).compare(parse(expected), parse(actual));
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private List<Difference> compare(Document expected, Document actual) {
        List<Difference> differences = new ArrayList<>();
        Element e = expected.getDocumentElement();
        Element a = actual.getDocumentElement();
        if (!key(e).equals(key(a))) {
            differences.add(new Difference(Kind.MISSING_ELEMENT, "/" + step(e, 1), null, null));
            differences.add(new Difference(Kind.EXTRA_ELEMENT, "/" + step(a, 1), null, null));
        } else {
            compareElements(e, a, "/" + step(e, 1), differences);
        }
        return differences;
    }

    private void compareElements(Element expected, Element actual, String path, List<Difference> differences) {
        compareAttributes(expected, actual, path, differences);

        List<Element> expectedChildren = indexOrdered(childElements(expected));
        List<Element> actualChildren = indexOrdered(childElements(actual));

        String expectedText = directText(expected);
        String actualText = directText(actual);
        boolean structural = !expectedChildren.isEmpty() || !actualChildren.isEmpty();
        if (!(structural && expectedText.isBlank() && actualText.isBlank()) && !expectedText.equals(actualText)) {
            differences.add(new Difference(Kind.CHANGED_TEXT, path, expectedText, actualText));
        }

        alignChildren(expectedChildren, actualChildren, path, differences);
    }

    private void compareAttributes(Element expected, Element actual, String path, List<Difference> differences) {
        Map<String, Attr> expectedAttributes = attributes(expected);
        Map<String, Attr> actualAttributes = attributes(actual);
        for (var entry : expectedAttributes.entrySet()) {
            Attr e = entry.getValue();
            Attr a = actualAttributes.get(entry.getKey());
            String attributePath = path + "/@" + e.getName();
            if (a == null) {
                differences.add(new Difference(Kind.MISSING_ATTRIBUTE, attributePath, e.getValue(), null));
            } else if (!e.getValue().equals(a.getValue())) {
                differences.add(new Difference(Kind.CHANGED_ATTRIBUTE, attributePath, e.getValue(), a.getValue()));
            }
        }
        for (var entry : actualAttributes.entrySet()) {
            if (!expectedAttributes.containsKey(entry.getKey())) {
                Attr a = entry.getValue();
                differences.add(new Difference(Kind.EXTRA_ATTRIBUTE, path + "/@" + a.getName(), null, a.getValue()));
            }
        }
    }

    private void alignChildren(List<Element> expected, List<Element> actual, String path, List<Difference> differences) {
        int n = expected.size();
        int m = actual.size();
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                lcs[i][j] = key(expected.get(i)).equals(key(actual.get(j)))
                    ? lcs[i + 1][j + 1] + 1
                    : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }

        Map<String, Integer> expectedPositions = new LinkedHashMap<>();
        Map<String, Integer> actualPositions = new LinkedHashMap<>();
        int i = 0;
        int j = 0;
        while (i < n || j < m) {
            if (i < n && j < m && key(expected.get(i)).equals(key(actual.get(j)))) {
                Element e = expected.get(i++);
                Element a = actual.get(j++);
                actualPositions.merge(key(a), 1, Integer::sum);
                compareElements(e, a, path + "/" + step(e, expectedPositions.merge(key(e), 1, Integer::sum)), differences);
            } else if (j < m && (i == n || lcs[i][j + 1] >= lcs[i + 1][j])) {
                Element a = actual.get(j++);
                String step = step(a, actualPositions.merge(key(a), 1, Integer::sum));
                differences.add(new Difference(Kind.EXTRA_ELEMENT, path + "/" + step, null, null));
            } else {
                Element e = expected.get(i++);
                String step = step(e, expectedPositions.merge(key(e), 1, Integer::sum));
                differences.add(new Difference(Kind.MISSING_ELEMENT, path + "/" + step, null, null));
            }
        }
    }

    private String key(Element element) {
        String namespace = ignoreElementNamespaces ? "" : Objects.toString(element.getNamespaceURI(), "");
        String name = "{" + namespace + "}" + localName(element);
        String id = element.getAttribute("id");
        return id.isEmpty() ? name : name + "#" + id;
    }

    private static String step(Element element, int position) {
        String id = element.getAttribute("id");
        return id.isEmpty()
            ? localName(element) + "[" + position + "]"
            : localName(element) + "[@id='" + id + "']";
    }

    private static String localName(Element element) {
        return element.getLocalName() != null ? element.getLocalName() : element.getNodeName();
    }

    private static Map<String, Attr> attributes(Element element) {
        Map<String, Attr> result = new LinkedHashMap<>();
        NamedNodeMap map = element.getAttributes();
        for (int i = 0; i < map.getLength(); i++) {
            Attr attr = (Attr) map.item(i);
            if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attr.getNamespaceURI())) {
                continue;
            }
            String localName = attr.getLocalName() != null ? attr.getLocalName() : attr.getName();
            result.put("{" + Objects.toString(attr.getNamespaceURI(), "") + "}" + localName, attr);
        }
        return result;
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

    private static String directText(Element element) {
        StringBuilder text = new StringBuilder();
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                text.append(child.getNodeValue());
            }
        }
        return text.toString();
    }

    private static List<Element> indexOrdered(List<Element> children) {
        List<Element> indexed = new ArrayList<>();
        for (Element child : children) {
            if (index(child) != null) {
                indexed.add(child);
            }
        }
        if (indexed.size() < 2) {
            return children;
        }
        indexed.sort(Comparator.comparing(XmlDiff::index));
        List<Element> result = new ArrayList<>(children.size());
        int next = 0;
        for (Element child : children) {
            result.add(index(child) != null ? indexed.get(next++) : child);
        }
        return result;
    }

    private static Integer index(Element element) {
        String value = element.getAttribute("index");
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

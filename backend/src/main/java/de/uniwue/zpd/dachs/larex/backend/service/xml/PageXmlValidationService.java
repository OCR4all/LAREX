package de.uniwue.zpd.dachs.larex.backend.service.xml;

import de.uniwue.zpd.dachs.larex.backend.dto.PageXmlTextDto;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PageXmlValidationService {

    private static final Pattern ROOT_PATTERN = Pattern.compile("<\\s*([A-Za-z_][\\w:.-]*)\\b([^>]*)>", Pattern.DOTALL);
    private static final Pattern DEFAULT_XMLNS_PATTERN = Pattern.compile("\\bxmlns\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern PREFIX_XMLNS_PATTERN = Pattern.compile("\\bxmlns:([A-Za-z_][\\w.-]*)\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern SCHEMA_LOCATION_PATTERN = Pattern.compile("\\bxsi:schemaLocation\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");

    private final Map<String, Resource> xsdByVersion;
    private final Set<String> supportedVersions;
    private final Map<String, Schema> schemaCache = new ConcurrentHashMap<>();

    public PageXmlValidationService() {
        this.xsdByVersion = discoverVersionedXsds();
        this.supportedVersions = Collections.unmodifiableSet(xsdByVersion.keySet());
    }

    public Set<String> getSupportedVersions() {
        return supportedVersions;
    }

    public PageXmlTextDto.XmlValidationResult validatePageXml(String xmlText) {
        String normalizedXml = xmlText == null ? "" : xmlText;
        return validatePageXml(new ByteArrayResource(normalizedXml.getBytes(StandardCharsets.UTF_8)));
    }

    public PageXmlTextDto.XmlValidationResult validatePageXml(Resource xmlResource) {
        Detection detection;
        try {
            detection = detect(readPrefix(xmlResource, 131_072));
        } catch (IOException error) {
            return invalid(List.of(error(1, 1, "XML_READ_ERROR", "Could not read PAGE XML")), null, null);
        }

        List<PageXmlTextDto.XmlValidationError> parseErrors = checkWellFormedXml(xmlResource);
        if (!parseErrors.isEmpty()) {
            return invalid(parseErrors, detection.pageVersion(), detection.namespace());
        }

        if (!detection.isPageRoot()) {
            return invalid(List.of(error(
                    1,
                    1,
                    "UNSUPPORTED_ROOT_ELEMENT",
                    "Root element must be PcGts for PAGE XML"
            )), detection.pageVersion(), detection.namespace());
        }

        if (detection.pageVersion() == null || !supportedVersions.contains(detection.pageVersion())) {
            String version = detection.pageVersion() == null ? "unknown" : detection.pageVersion();
            return invalid(List.of(error(
                    1,
                    1,
                    "UNSUPPORTED_PAGE_VERSION",
                    "Unsupported PAGE XML version: " + version + ". Supported versions: " + supportedVersionsMessage()
            )), detection.pageVersion(), detection.namespace());
        }

        List<PageXmlTextDto.XmlValidationError> xsdErrors = validateAgainstSchema(
                xmlResource,
                detection.pageVersion()
        );
        if (!xsdErrors.isEmpty()) {
            return invalid(xsdErrors, detection.pageVersion(), detection.namespace());
        }

        return new PageXmlTextDto.XmlValidationResult(
                true,
                List.of(),
                detection.pageVersion(),
                detection.namespace()
        );
    }

    private List<PageXmlTextDto.XmlValidationError> validateAgainstSchema(Resource xmlResource, String version) {
        Schema schema = loadSchema(version);
        if (schema == null) {
            return List.of(error(
                    1,
                    1,
                    "XSD_NOT_AVAILABLE",
                    "No XSD schema available for PAGE XML version " + version
            ));
        }

        CollectingErrorHandler handler = new CollectingErrorHandler("XSD_VALIDATION_ERROR");
        try {
            Validator validator = schema.newValidator();
            validator.setErrorHandler(handler);
            try (InputStream inputStream = xmlResource.getInputStream()) {
                Source source = new StreamSource(inputStream);
                validator.validate(source);
            }
        } catch (Exception e) {
            if (handler.errors().isEmpty()) {
                handler.addFallback(e.getMessage());
            }
        }
        return handler.errors();
    }

    private List<PageXmlTextDto.XmlValidationError> checkWellFormedXml(Resource xmlResource) {
        CollectingErrorHandler handler = new CollectingErrorHandler("XML_PARSE_ERROR");
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            safeSetFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
            safeSetFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
            safeSetFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
            safeSetFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
            safeSetFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            SAXParser parser = factory.newSAXParser();
            try (InputStream inputStream = xmlResource.getInputStream()) {
                parser.parse(new InputSource(inputStream), new DefaultHandler() {
                });
            }
        } catch (SAXParseException e) {
            handler.error(e);
        } catch (Exception e) {
            if (handler.errors().isEmpty()) {
                handler.addFallback(e.getMessage());
            }
        }
        return handler.errors();
    }

    private String readPrefix(Resource resource, int maxBytes) throws IOException {
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readNBytes(maxBytes), StandardCharsets.UTF_8);
        }
    }

    private void safeSetFeature(SAXParserFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (Exception ignored) {
            // Some parser implementations may not support every hardening feature.
        }
    }

    private Schema loadSchema(String version) {
        return schemaCache.computeIfAbsent(version, this::compileSchema);
    }

    private Schema compileSchema(String version) {
        Resource resource = xsdByVersion.get(version);
        if (resource == null) {
            return null;
        }

        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

            try (InputStream inputStream = resource.getInputStream()) {
                StreamSource source = new StreamSource(inputStream);
                source.setSystemId(resource.getURL().toString());
                return factory.newSchema(source);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private Detection detect(String xmlText) {
        Matcher rootMatcher = ROOT_PATTERN.matcher(xmlText);
        if (!rootMatcher.find()) {
            return new Detection(false, null, null);
        }

        String rootName = rootMatcher.group(1);
        String attrs = rootMatcher.group(2) == null ? "" : rootMatcher.group(2);

        String localName = rootName;
        String prefix = null;
        int colon = rootName.indexOf(':');
        if (colon > 0) {
            prefix = rootName.substring(0, colon);
            localName = rootName.substring(colon + 1);
        }

        Map<String, String> namespaces = new HashMap<>();
        Matcher defaultXmlnsMatcher = DEFAULT_XMLNS_PATTERN.matcher(attrs);
        if (defaultXmlnsMatcher.find()) {
            namespaces.put("", defaultXmlnsMatcher.group(1));
        }
        Matcher prefixedMatcher = PREFIX_XMLNS_PATTERN.matcher(attrs);
        while (prefixedMatcher.find()) {
            namespaces.put(prefixedMatcher.group(1), prefixedMatcher.group(2));
        }

        String namespace = prefix == null ? namespaces.get("") : namespaces.get(prefix);
        if (namespace == null && rootName.toLowerCase(Locale.ROOT).contains("pcgts")) {
            namespace = namespaces.get("");
        }

        String schemaLocation = null;
        Matcher schemaLocationMatcher = SCHEMA_LOCATION_PATTERN.matcher(attrs);
        if (schemaLocationMatcher.find()) {
            schemaLocation = schemaLocationMatcher.group(1);
        }

        String pageVersion = extractVersion(namespace, schemaLocation);
        boolean isPageRoot = "PcGts".equals(localName);
        return new Detection(isPageRoot, pageVersion, namespace);
    }

    private String extractVersion(String namespace, String schemaLocation) {
        String version = extractVersionToken(namespace);
        if (version != null) {
            return version;
        }
        return extractVersionToken(schemaLocation);
    }

    private String extractVersionToken(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Matcher matcher = VERSION_PATTERN.matcher(value);
        return matcher.find() ? matcher.group(1) : null;
    }

    private Map<String, Resource> discoverVersionedXsds() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:schema/page/*.xsd");

            Map<String, Resource> map = new LinkedHashMap<>();
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null) {
                    continue;
                }
                String version = extractVersionToken(filename);
                if (version == null) {
                    continue;
                }
                map.put(version, resource);
            }
            return map.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (a, b) -> b,
                            LinkedHashMap::new
                    ));
        } catch (IOException e) {
            return Map.of();
        }
    }

    private String supportedVersionsMessage() {
        return joinVersions(supportedVersions);
    }

    private String joinVersions(Collection<String> versions) {
        if (versions == null || versions.isEmpty()) {
            return "none";
        }
        return versions.stream().sorted().collect(Collectors.joining(", "));
    }

    private PageXmlTextDto.XmlValidationResult invalid(
            List<PageXmlTextDto.XmlValidationError> errors,
            String pageVersion,
            String namespace
    ) {
        return new PageXmlTextDto.XmlValidationResult(false, List.copyOf(errors), pageVersion, namespace);
    }

    private PageXmlTextDto.XmlValidationError error(int line, int column, String code, String message) {
        return new PageXmlTextDto.XmlValidationError(
                Math.max(1, line),
                Math.max(1, column),
                "error",
                code,
                message == null || message.isBlank() ? "Validation error" : message
        );
    }

    private record Detection(boolean isPageRoot, String pageVersion, String namespace) {
    }

    private final class CollectingErrorHandler implements ErrorHandler {

        private final String code;
        private final List<PageXmlTextDto.XmlValidationError> errors = new ArrayList<>();

        private CollectingErrorHandler(String code) {
            this.code = code;
        }

        @Override
        public void warning(SAXParseException exception) {
            // Warnings are intentionally ignored for strict save gating.
        }

        @Override
        public void error(SAXParseException exception) {
            errors.add(PageXmlValidationService.this.error(
                    exception != null ? exception.getLineNumber() : 1,
                    exception != null ? exception.getColumnNumber() : 1,
                    code,
                    exception != null ? exception.getMessage() : "Validation error"
            ));
        }

        @Override
        public void fatalError(SAXParseException exception) {
            error(exception);
        }

        private List<PageXmlTextDto.XmlValidationError> errors() {
            return List.copyOf(errors);
        }

        private void addFallback(String message) {
            errors.add(PageXmlValidationService.this.error(1, 1, code, message));
        }
    }
}

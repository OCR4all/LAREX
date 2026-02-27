package de.uniwue.zpd.dachs.larex.backend.entity;

/**
 * Enumeration of supported XML schema types for document annotation.
 */
public enum XmlSchema {
    /**
     * PAGE XML format - a comprehensive format for document layout analysis.
     * Supports detailed text regions, baselines, reading order, and metadata.
     */
    PAGE_XML("PAGE", "http://schema.primaresearch.org/PAGE/gts/pagecontent/"),

    /**
     * ALTO XML format - Analyzed Layout and Text Object format.
     * Library of Congress standard for OCR and layout analysis results.
     */
    ALTO_XML("ALTO", "http://www.loc.gov/standards/alto/"),

    /**
     * Unknown or unsupported format.
     */
    UNKNOWN("UNKNOWN", "");

    private final String displayName;
    private final String namespace;

    XmlSchema(String displayName, String namespace) {
        this.displayName = displayName;
        this.namespace = namespace;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getNamespace() {
        return namespace;
    }

    /**
     * Detect XML schema from namespace URI or root element.
     */
    public static XmlSchema detectFromNamespace(String namespace) {
        if (namespace == null || namespace.isEmpty()) {
            return UNKNOWN;
        }

        for (XmlSchema schema : values()) {
            if (namespace.startsWith(schema.namespace)) {
                return schema;
            }
        }

        return UNKNOWN;
    }

    /**
     * Detect XML schema from root element name.
     */
    public static XmlSchema detectFromRootElement(String rootElementName) {
        if (rootElementName == null) {
            return UNKNOWN;
        }

        return switch (rootElementName.toLowerCase()) {
            case "pcgts", "pctgs" -> PAGE_XML;
            case "alto" -> ALTO_XML;
            default -> UNKNOWN;
        };
    }
}
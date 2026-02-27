package de.uniwue.zpd.dachs.larex.backend.util;

/**
 * Utility class for handling XML file operations and naming conventions.
 * Similar to ImageFileUtils but for XML files.
 */
public class XmlFileUtils {

    /**
     * Extracts the base name from a filename by taking everything before the first dot.
     * Examples:
     * - "0001.xml" -> baseName: "0001", variant: "xml"
     * - "0001.kraken.xml" -> baseName: "0001", variant: "kraken.xml"
     * - "page_001.alto.xml" -> baseName: "page_001", variant: "alto.xml"
     * - "simple.xml" -> baseName: "simple", variant: "xml"
     *
     * @param fileName the original filename
     * @return XmlNameInfo containing baseName and variant
     */
    public static XmlNameInfo parseXmlName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return new XmlNameInfo(fileName, "");
        }

        int firstDotIndex = fileName.indexOf('.');
        if (firstDotIndex == -1) {
            // No extension, entire filename is the base name
            return new XmlNameInfo(fileName, "");
        }

        String baseName = fileName.substring(0, firstDotIndex);
        String variant = fileName.substring(firstDotIndex + 1);

        return new XmlNameInfo(baseName, variant);
    }

    /**
     * Data class to hold parsed XML name information.
     */
    public record XmlNameInfo(String baseName, String variant) {
    }
}
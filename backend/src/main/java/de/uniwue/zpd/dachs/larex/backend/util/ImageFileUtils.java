package de.uniwue.zpd.dachs.larex.backend.util;

/**
 * Utility class for handling image file operations and naming conventions.
 */
public class ImageFileUtils {

    /**
     * Extracts the base name from a filename by taking everything before the first dot.
     * Examples:
     * - "0001.png" -> baseName: "0001", variant: "png"
     * - "0001.bin.png" -> baseName: "0001", variant: "bin.png"
     * - "page_001.nrm.png" -> baseName: "page_001", variant: "nrm.png"
     * - "simple.jpg" -> baseName: "simple", variant: "jpg"
     *
     * @param fileName the original filename
     * @return ImageNameInfo containing baseName and variant
     */
    public static ImageNameInfo parseImageName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return new ImageNameInfo(fileName, "");
        }

        int firstDotIndex = fileName.indexOf('.');
        if (firstDotIndex == -1) {
            // No extension, entire filename is the base name
            return new ImageNameInfo(fileName, "");
        }

        String baseName = fileName.substring(0, firstDotIndex);
        String variant = fileName.substring(firstDotIndex + 1);

        return new ImageNameInfo(baseName, variant);
    }

    /**
         * Data class to hold parsed image name information.
         */
        public record ImageNameInfo(String baseName, String variant) {
    }
}

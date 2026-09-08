package de.uniwue.zpd.dachs.larex.backend.util;

import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Utility class for handling image file operations and naming conventions.
 */
public class ImageFileUtils {

    /**
     * Orders page images so all callers agree on the effective editor default.
     * Missing project preferences fall back to original, then stable lexical order.
     */
    public static List<PageImage> sortForDisplay(Collection<PageImage> images, String primaryVariant) {
        String configured = primaryVariant == null ? null : primaryVariant.trim();
        return images.stream()
                .sorted(Comparator
                        .comparingInt((PageImage image) -> imagePriority(image, configured))
                        .thenComparing(image -> normalized(image.getVariant()))
                        .thenComparing(image -> raw(image.getVariant()))
                        .thenComparing(image -> normalized(image.getFileName()))
                        .thenComparing(image -> raw(image.getFileName()))
                        .thenComparing(image -> normalized(image.getId()))
                        .thenComparing(image -> raw(image.getId())))
                .toList();
    }

    private static int imagePriority(PageImage image, String primaryVariant) {
        if (primaryVariant != null && primaryVariant.equals(image.getVariant())) return 0;
        if ("original".equalsIgnoreCase(image.getVariant())) return 1;
        return 2;
    }

    private static String normalized(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static String raw(String value) {
        return value == null ? "" : value;
    }

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

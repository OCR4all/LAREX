package de.uniwue.zpd.dachs.larex.backend.util;

import java.util.Locale;

public final class DownloadFileNames {

    private DownloadFileNames() {
    }

    public static String sanitize(String value, String fallback) {
        if (value == null) {
            return fallback;
        }

        String sanitized = value.trim()
                .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return sanitized.isBlank() || ".".equals(sanitized) || "..".equals(sanitized)
                ? fallback
                : sanitized;
    }

    public static String projectBasicExport(String projectName) {
        return sanitize(projectName, "project") + " - flat export.zip";
    }

    public static String projectPackage(String projectName) {
        return sanitize(projectName, "project") + " - LAREX package.larex-project.zip";
    }

    public static String datasetPackage(String datasetName) {
        return sanitize(datasetName, "dataset") + " - LAREX dataset.larex-dataset.zip";
    }

    public static String batchProjectExport() {
        return "larex-projects-batch-export.zip";
    }

    public static String actionOutputBundle(String processorName, String timestamp) {
        return sanitize(processorName, "action-output") + "-" + sanitize(timestamp, "output") + ".zip";
    }

    public static String annotationExport(String pageName, String schema) {
        String baseName = sanitize(pageName, "page");
        int extensionIndex = baseName.lastIndexOf('.');
        if (extensionIndex > 0) {
            baseName = baseName.substring(0, extensionIndex);
        }
        String schemaName = sanitize(schema, "xml").toLowerCase(Locale.ROOT).replace('_', '-');
        return baseName + "." + schemaName + ".xml";
    }
}

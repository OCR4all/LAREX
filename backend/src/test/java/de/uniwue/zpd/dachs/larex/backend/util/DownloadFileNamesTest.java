package de.uniwue.zpd.dachs.larex.backend.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DownloadFileNamesTest {

    @Test
    void sanitizesReadableNamesWithoutAllowingPathCharacters() {
        assertEquals("My Project Edition", DownloadFileNames.sanitize(" My / Project: Edition ", "fallback"));
    }

    @Test
    void buildsSemanticArchiveNames() {
        assertEquals("My Project - flat export.zip", DownloadFileNames.projectBasicExport("My Project"));
        assertEquals("My Project - LAREX package.larex-project.zip", DownloadFileNames.projectPackage("My Project"));
        assertEquals("Training Set - LAREX dataset.larex-dataset.zip", DownloadFileNames.datasetPackage("Training Set"));
        assertEquals("processor-1-20260811-203000.zip", DownloadFileNames.actionOutputBundle("processor-1", "20260811-203000"));
    }

    @Test
    void derivesAnnotationNameFromImageName() {
        assertEquals("page-001.alto-xml.xml", DownloadFileNames.annotationExport("page-001.png", "ALTO_XML"));
    }
}

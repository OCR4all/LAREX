package de.uniwue.zpd.dachs.larex.backend.service.annotation.exporter;

import de.uniwue.zpd.dachs.larex.backend.dto.page.PageDto;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Exporter for PageDto to ALTO XML format.
 * TODO(larex): Implement ALTO export via page4j ALTO support.
 */
@Component
public class AnnotationToAltoXmlExporter {

    /**
     * Export a PageDto to ALTO XML format.
     * Currently a stub - throws UnsupportedOperationException.
     *
     * @param page PageDto to export
     * @param outputPath Path where ALTO XML should be saved
     * @throws IOException if export fails
     */
    public void export(PageDto page, Path outputPath) throws IOException {
        // TODO(larex): Implement ALTO export with page4j's ALTO writer.

        throw new UnsupportedOperationException(
            "ALTO XML export not yet migrated to page4j. " +
            "Use PAGE XML format or implement ALTO support using page4j's ALTO writer."
        );
    }

    /**
     * Convert a PageDto to ALTO XML string.
     * Currently a stub - throws UnsupportedOperationException.
     *
     * @param page PageDto to convert
     * @return ALTO XML as string
     */
    public String toXmlString(PageDto page) {
        // TODO(larex): Implement ALTO string conversion with page4j's ALTO writer.

        throw new UnsupportedOperationException(
            "ALTO XML conversion not yet migrated to page4j. " +
            "Use PAGE XML format or implement ALTO support using page4j's ALTO writer."
        );
    }
}

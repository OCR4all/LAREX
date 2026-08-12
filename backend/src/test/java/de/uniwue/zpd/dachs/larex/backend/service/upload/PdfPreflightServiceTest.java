package de.uniwue.zpd.dachs.larex.backend.service.upload;

import de.uniwue.zpd.dachs.larex.backend.config.UploadProperties;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

class PdfPreflightServiceTest {

    @Test
    void analyzesPageCountPixelsAndPngEstimateWithoutKeepingOutputInMemory() throws Exception {
        UploadProperties properties = new UploadProperties();
        properties.getPdf().setPreflightSamplePages(1);
        PdfPreflightService service = new PdfPreflightService(properties);

        var pdfPath = Files.createTempFile("larex-preflight", ".pdf");
        try {
            try (PDDocument document = new PDDocument()) {
                document.addPage(new PDPage());
                document.save(pdfPath.toFile());
            }

            PdfPreflightService.PdfAnalysis analysis = service.analyze(pdfPath, 72);

            assertThat(analysis.pageCount()).isEqualTo(1);
            assertThat(analysis.renderedPixels()).isEqualTo(612L * 792L);
            assertThat(analysis.estimatedBytes()).isPositive();
        } finally {
            Files.deleteIfExists(pdfPath);
        }
    }
}

package de.uniwue.zpd.dachs.larex.backend.service.upload;

import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncUploadProcessorPdfRenderingTest {

    @Test
    void reducesScaleForPagesThatWouldExceedPixelLimit() {
        PDPage page = new PDPage(new PDRectangle(10_000, 10_000));

        float scale = AsyncUploadProcessor.resolvePdfRenderScale(page, 250, 4_000_000);
        int[] dimensions = AsyncUploadProcessor.resolvePdfImageDimensions(page, scale);

        assertThat(scale).isLessThan(250 / 72.0f);
        assertThat((long) dimensions[0] * dimensions[1]).isLessThanOrEqualTo(4_000_000L);
    }

    @Test
    void keepsRequestedScaleForNormalPages() {
        PDPage page = new PDPage(PDRectangle.A4);

        float scale = AsyncUploadProcessor.resolvePdfRenderScale(page, 250, 16_000_000);

        assertThat(scale).isEqualTo(250 / 72.0f);
    }
}

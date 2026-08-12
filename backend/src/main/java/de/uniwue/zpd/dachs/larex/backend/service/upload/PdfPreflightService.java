package de.uniwue.zpd.dachs.larex.backend.service.upload;

import de.uniwue.zpd.dachs.larex.backend.config.UploadProperties;
import de.uniwue.zpd.dachs.larex.backend.entity.UploadSessionFile;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

@Service
public class PdfPreflightService {

    private static final double MINIMUM_ESTIMATED_PNG_BYTES_PER_PIXEL = 0.25;
    private static final double FALLBACK_ESTIMATED_PNG_BYTES_PER_PIXEL = 1.0;

    private final UploadProperties uploadProperties;

    public PdfPreflightService(UploadProperties uploadProperties) {
        this.uploadProperties = uploadProperties;
    }

    public PdfAnalysis analyze(Path pdfPath, int renderDpi) throws IOException {
        if (!Files.exists(pdfPath)) {
            throw new IOException("PDF temp file not found: " + pdfPath);
        }

        Path tempDirectory = pdfPath.toAbsolutePath().getParent();
        MemoryUsageSetting memoryUsage = MemoryUsageSetting.setupTempFileOnly()
                .setTempDir(tempDirectory.toFile());

        try (PDDocument document = Loader.loadPDF(pdfPath.toFile(), memoryUsage.streamCache)) {
            long renderedPixels = 0L;
            double maxSampleBytesPerPixel = 0.0;
            Set<Integer> sampleIndexes = sampleIndexes(document.getNumberOfPages());
            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                PDPage page = document.getPage(pageIndex);
                float scale = AsyncUploadProcessor.resolvePdfRenderScale(
                        page,
                        renderDpi,
                        uploadProperties.getPdf().getMaxRenderedPixels()
                );
                int[] dimensions = AsyncUploadProcessor.resolvePdfImageDimensions(page, scale);
                renderedPixels = saturatedAdd(renderedPixels, saturatedMultiply(dimensions[0], dimensions[1]));

                if (sampleIndexes.contains(pageIndex)) {
                    BufferedImage image = null;
                    try {
                        PDFRenderer renderer = new PDFRenderer(document);
                        renderer.setSubsamplingAllowed(true);
                        image = renderer.renderImage(pageIndex, scale, ImageType.RGB);
                        CountingOutputStream output = new CountingOutputStream();
                        if (!ImageIO.write(image, "png", output)) {
                            throw new IOException("Unable to estimate PNG output size for PDF page " + (pageIndex + 1));
                        }
                        long pagePixels = saturatedMultiply(image.getWidth(), image.getHeight());
                        if (pagePixels > 0) {
                            maxSampleBytesPerPixel = Math.max(
                                    maxSampleBytesPerPixel,
                                    output.bytes / (double) pagePixels
                            );
                        }
                    } finally {
                        if (image != null) {
                            image.flush();
                        }
                    }
                }
            }

            double bytesPerPixel = Math.max(
                    MINIMUM_ESTIMATED_PNG_BYTES_PER_PIXEL,
                    maxSampleBytesPerPixel > 0
                            ? maxSampleBytesPerPixel
                            : FALLBACK_ESTIMATED_PNG_BYTES_PER_PIXEL
            );
            long estimatedBytes = estimateBytes(
                    renderedPixels,
                    bytesPerPixel,
                    uploadProperties.getPdf().getPreflightSafetyFactor()
            );
            return new PdfAnalysis(
                    document.getNumberOfPages(),
                    renderedPixels,
                    estimatedBytes
            );
        }
    }

    public boolean isPdf(UploadSessionFile file) {
        String mimeType = file.getMimeType();
        if (mimeType != null && mimeType.equalsIgnoreCase("application/pdf")) {
            return true;
        }
        String fileName = file.getOriginalFileName();
        return fileName != null && fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf");
    }

    public record PdfAnalysis(
            int pageCount,
            long renderedPixels,
            long estimatedBytes
    ) {}

    private Set<Integer> sampleIndexes(int pageCount) {
        Set<Integer> indexes = new HashSet<>();
        if (pageCount <= 0) {
            return indexes;
        }

        int requestedSamples = Math.min(pageCount, uploadProperties.getPdf().getPreflightSamplePages());
        for (int sample = 0; sample < requestedSamples; sample++) {
            int index = requestedSamples == 1
                    ? 0
                    : (int) Math.round(sample * (pageCount - 1.0) / (requestedSamples - 1.0));
            indexes.add(index);
        }
        return indexes;
    }

    private long estimateBytes(long pixels, double bytesPerPixel, double safetyFactor) {
        double estimate = pixels * bytesPerPixel * safetyFactor;
        if (!Double.isFinite(estimate) || estimate >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(1L, (long) Math.ceil(estimate));
    }

    private long saturatedMultiply(int left, int right) {
        if (left <= 0 || right <= 0) {
            return 0L;
        }
        if ((long) left > Long.MAX_VALUE / right) {
            return Long.MAX_VALUE;
        }
        return (long) left * right;
    }

    private long saturatedAdd(long left, long right) {
        if (Long.MAX_VALUE - left < right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static final class CountingOutputStream extends OutputStream {
        private long bytes;

        @Override
        public void write(int value) {
            bytes = increment(bytes);
        }

        @Override
        public void write(byte[] buffer, int offset, int length) {
            bytes = increment(bytes, length);
        }

        private static long increment(long value) {
            return increment(value, 1);
        }

        private static long increment(long value, int amount) {
            if (Long.MAX_VALUE - value < amount) {
                return Long.MAX_VALUE;
            }
            return value + amount;
        }
    }
}

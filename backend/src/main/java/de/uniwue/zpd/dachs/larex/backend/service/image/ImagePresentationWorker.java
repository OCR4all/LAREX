package de.uniwue.zpd.dachs.larex.backend.service.image;

import net.coobird.thumbnailator.Thumbnails;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Runs outside the application JVM: its heap and lifetime are bounded by the parent. */
public final class ImagePresentationWorker {
    private ImagePresentationWorker() {}
    public static void main(String[] args) throws Exception {
        Path source = Path.of(args[0]), data = Path.of(args[1]), metadata = Path.of(args[2]);
        int edge = Integer.parseInt(args[3]); // zero means native pixels, never original file bytes
        long maxPixels = Long.parseLong(args[4]);
        int maxDimension = Integer.parseInt(args[5]);
        var result = new Properties();
        try (var input = ImageIO.createImageInputStream(source.toFile())) {
            if (input == null) throw new ImagePresentationException(415, "Cannot read this image format");
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new ImagePresentationException(415, "Unsupported image format");
            var reader = readers.next();
            BufferedImage sampled = null, resized = null, rgb = null;
            try {
                // Canonical geometry is stored pixels. Neither this path nor its
                // native-resolution PNG applies EXIF rotations or mirrors.
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                long pixels = (long) width * height;
                if (width <= 0 || height <= 0 || (edge != 0 && (width > maxDimension || height > maxDimension || pixels > maxPixels)))
                    throw new ImagePresentationException(413, "Image exceeds the configured source pixel limit");
                var parameters = reader.getDefaultReadParam();
                int sampling = edge == 0 ? 1 : Math.max(1, Math.max(width, height) / edge);
                if (edge != 0) {
                    var type = reader.getRawImageType(0);
                    int bytesPerPixel = type == null ? 8 : Math.max(4, (type.getColorModel().getPixelSize() + 7) / 8);
                    // Limit the sampled raster to approximately 64 MiB. Codec
                    // scratch allocations are additionally contained by -Xmx.
                    sampling = Math.max(sampling, (int) Math.ceil(Math.sqrt(pixels * (double) bytesPerPixel / (64 * 1024 * 1024))));
                }
                parameters.setSourceSubsampling(sampling, sampling, 0, 0);
                sampled = reader.read(0, parameters);
                resized = edge == 0 ? sampled : Thumbnails.of(sampled)
                        .size(Math.min(edge, sampled.getWidth()), Math.min(edge, sampled.getHeight())).asBufferedImage();
                rgb = new BufferedImage(resized.getWidth(), resized.getHeight(), BufferedImage.TYPE_INT_RGB);
                var graphics = rgb.createGraphics();
                try {
                    graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
                    graphics.drawImage(resized, 0, 0, null);
                } finally { graphics.dispose(); }
                String format = edge == 0 ? "png" : "jpeg";
                var writer = ImageIO.getImageWritersByFormatName(format).next();
                try (var output = ImageIO.createImageOutputStream(data.toFile())) {
                    writer.setOutput(output);
                    var write = writer.getDefaultWriteParam();
                    if (edge != 0) {
                        write.setCompressionMode(ImageWriteParam.MODE_EXPLICIT); write.setCompressionQuality(0.88f);
                    }
                    writer.write(null, new javax.imageio.IIOImage(rgb, null, null), write);
                } finally { writer.dispose(); }
                result.setProperty("width", String.valueOf(width)); result.setProperty("height", String.valueOf(height));
                result.setProperty("type", edge == 0 ? "image/png" : "image/jpeg");
            } finally {
                reader.dispose();
                if (sampled != null) sampled.flush(); if (resized != null) resized.flush(); if (rgb != null) rgb.flush();
            }
        } catch (ImagePresentationException e) {
            result.setProperty("status", String.valueOf(e.status())); result.setProperty("message", e.getMessage());
        } catch (Throwable e) {
            result.setProperty("status", "422"); result.setProperty("message", "Image cannot be decoded within the display resource limits");
        }
        try (var output = Files.newOutputStream(metadata)) { result.store(output, null); }
    }
}

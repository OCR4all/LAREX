package de.uniwue.zpd.dachs.larex.backend.service.xml;

import com.maxnth.page4j.formats.SourceImage;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataFormatImpl;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

public final class SourceImages {

    private static final double MIN_PPI = 10;
    private static final double MAX_PPI = 20_000;

    private SourceImages() {
    }

    public static <T> Optional<T> matching(List<T> images, Function<T, String> fileName, String imageFilename) {
        String wanted = baseName(imageFilename);
        return images.stream()
                .filter(image -> !wanted.isEmpty() && wanted.equals(baseName(fileName.apply(image))))
                .findFirst()
                .or(() -> images.stream().findFirst());
    }

    private static String baseName(String path) {
        if (path == null) {
            return "";
        }
        String normalized = path.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
    }

    public static Optional<SourceImage> read(Path image) {
        if (image == null || !Files.isRegularFile(image)) {
            return Optional.empty();
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(image.toFile())) {
            if (input == null) {
                return Optional.empty();
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return Optional.empty();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, false);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0) {
                    return Optional.empty();
                }
                IIOMetadata metadata = reader.getImageMetadata(0);
                double x = pixelsPerInch(metadata, "HorizontalPixelSize");
                double y = pixelsPerInch(metadata, "VerticalPixelSize");
                return Optional.of(new SourceImage(width, height, x > 0 ? x : y, y > 0 ? y : x));
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private static double pixelsPerInch(IIOMetadata metadata, String pixelSize) {
        if (metadata == null || !metadata.isStandardMetadataFormatSupported()) {
            return 0;
        }
        Node root = metadata.getAsTree(IIOMetadataFormatImpl.standardMetadataFormatName);
        for (Node dimension = root.getFirstChild(); dimension != null; dimension = dimension.getNextSibling()) {
            if (!"Dimension".equals(dimension.getNodeName())) {
                continue;
            }
            for (Node node = dimension.getFirstChild(); node != null; node = node.getNextSibling()) {
                Node value = node.getAttributes() == null ? null : node.getAttributes().getNamedItem("value");
                if (pixelSize.equals(node.getNodeName()) && value != null) {
                    double millimetresPerPixel = Double.parseDouble(value.getNodeValue());
                    double ppi = millimetresPerPixel > 0 ? 25.4 / millimetresPerPixel : 0;
                    return ppi >= MIN_PPI && ppi <= MAX_PPI ? ppi : 0;
                }
            }
        }
        return 0;
    }
}

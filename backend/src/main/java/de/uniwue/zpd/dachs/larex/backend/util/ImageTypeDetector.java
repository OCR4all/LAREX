package de.uniwue.zpd.dachs.larex.backend.util;

import de.uniwue.zpd.dachs.larex.backend.entity.ImageType;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import java.awt.color.ColorSpace;
import java.awt.image.IndexColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.io.IOException;
import java.nio.file.Path;

/** Uses the color model for color/grayscale and exact grayscale samples for binary data. */
public final class ImageTypeDetector {
    private ImageTypeDetector() {}

    public static ImageType detect(Path path) {
        try (var input = ImageIO.createImageInputStream(path.toFile())) {
            if (input == null) return ImageType.UNKNOWN;
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return ImageType.UNKNOWN;
            var reader = readers.next();
            try {
                reader.setInput(input);
                var type = reader.getRawImageType(0);
                if (type == null) return ImageType.UNKNOWN;
                ImageType encoding = classify(type);
                if (encoding != ImageType.GRAYSCALE) return encoding;
                // Binarizers commonly save 0/255 pixels as 8-bit grayscale. Preserve native
                // sample depth while decoding once; subsampling could miss a gray pixel.
                var parameters = reader.getDefaultReadParam();
                parameters.setDestinationType(type);
                var image = reader.read(0, parameters);
                return hasOnlyBlackAndWhiteSamples(image.getRaster(), image.getColorModel() instanceof IndexColorModel palette ? palette : null)
                        ? ImageType.BINARY : ImageType.GRAYSCALE;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException error) {
            return ImageType.UNKNOWN;
        }
    }

    static ImageType classify(ImageTypeSpecifier type) {
        var color = type.getColorModel();
        if (color instanceof IndexColorModel palette) {
            boolean blackAndWhite = true;
            for (int i = 0; i < palette.getMapSize(); i++) {
                int red = palette.getRed(i);
                if (red != palette.getGreen(i) || red != palette.getBlue(i)) return ImageType.COLOR;
                blackAndWhite &= red == 0 || red == 255;
            }
            return blackAndWhite ? ImageType.BINARY : ImageType.GRAYSCALE;
        }
        if (color.getColorSpace().getType() == ColorSpace.TYPE_GRAY) {
            return type.getSampleModel().getSampleSize(0) == 1 ? ImageType.BINARY : ImageType.GRAYSCALE;
        }
        return ImageType.COLOR;
    }

    private static boolean hasOnlyBlackAndWhiteSamples(Raster raster, IndexColorModel palette) {
        int dataType = raster.getSampleModel().getDataType();
        double white = dataType == DataBuffer.TYPE_FLOAT || dataType == DataBuffer.TYPE_DOUBLE
                ? 1.0 : Math.scalb(1.0, raster.getSampleModel().getSampleSize(0)) - 1.0;
        double[] row = new double[raster.getWidth()];
        for (int y = raster.getMinY(); y < raster.getMinY() + raster.getHeight(); y++) {
            raster.getSamples(raster.getMinX(), y, raster.getWidth(), 1, 0, row);
            for (double sample : row) {
                double value = palette == null ? sample : palette.getRed((int) sample);
                if (value != 0 && value != (palette == null ? white : 255)) return false;
            }
        }
        return true;
    }

}

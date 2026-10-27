package de.uniwue.zpd.dachs.larex.backend.util;

import de.uniwue.zpd.dachs.larex.backend.entity.ImageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import javax.imageio.ImageIO;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.*;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class ImageTypeDetectorTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"png", "tiff"})
    void distinguishesColorGrayscaleAndOneBitBinary(String format) throws Exception {
        assertType(new BufferedImage(4, 4, BufferedImage.TYPE_BYTE_BINARY), format, ImageType.BINARY);
        assertType(grayscaleWithIntermediateSample(BufferedImage.TYPE_BYTE_GRAY), format, ImageType.GRAYSCALE);
        assertType(grayscaleWithIntermediateSample(BufferedImage.TYPE_USHORT_GRAY), format, ImageType.GRAYSCALE);
        assertType(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), format, ImageType.COLOR);
        assertType(new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB), format, ImageType.COLOR);
    }

    @ParameterizedTest
    @ValueSource(strings = {"png", "tiff"})
    void recognizesBinarizerOutputStoredAsEightBitGrayscale(String format) throws Exception {
        var image = new BufferedImage(4, 4, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) image.getRaster().setSample(x, y, 0, (x + y) % 2 == 0 ? 0 : 255);
        }
        assertType(image, format, ImageType.BINARY);
    }

    @Test
    void distinguishesGrayscaleAndRgbJpeg() throws Exception {
        assertType(grayscaleWithIntermediateSample(BufferedImage.TYPE_BYTE_GRAY), "jpeg", ImageType.GRAYSCALE);
        assertType(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "jpeg", ImageType.COLOR);
    }

    @Test
    void checksPaletteColorsRatherThanNumberOfChannelsOrBitsAlone() throws Exception {
        byte[] zero = {0, 0};
        byte[] end = {0, (byte) 255};
        var colored = new IndexColorModel(1, 2, end, zero, zero);
        assertType(new BufferedImage(4, 4, BufferedImage.TYPE_BYTE_BINARY, colored), "png", ImageType.COLOR);
        byte[] gray = {50, (byte) 150};
        var twoGrayLevels = new IndexColorModel(1, 2, gray, gray, gray);
        assertType(new BufferedImage(4, 4, BufferedImage.TYPE_BYTE_BINARY, twoGrayLevels), "png", ImageType.GRAYSCALE);
        var grayscalePalette = new IndexColorModel(8, 2, end, end, end);
        assertType(new BufferedImage(4, 4, BufferedImage.TYPE_BYTE_INDEXED, grayscalePalette), "tiff", ImageType.BINARY);
    }

    @Test
    void ignoresAlphaWhenCountingColorChannels() throws Exception {
        var color = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_GRAY), new int[]{8, 8},
                true, false, Transparency.TRANSLUCENT, DataBuffer.TYPE_BYTE);
        var image = new BufferedImage(color, color.createCompatibleWritableRaster(4, 4), false, null);
        image.getRaster().setSample(3, 3, 0, 127);
        assertType(image, "png", ImageType.GRAYSCALE);
    }

    @Test
    void doesNotInferTypeFromFilenameAndHandlesUndecodableFiles() throws Exception {
        Path misleading = directory.resolve("binary.bin.jpg");
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", misleading.toFile());
        assertThat(ImageTypeDetector.detect(misleading)).isEqualTo(ImageType.COLOR);
        Path invalid = directory.resolve("invalid.png");
        Files.writeString(invalid, "not an image");
        assertThat(ImageTypeDetector.detect(invalid)).isEqualTo(ImageType.UNKNOWN);
        assertThat(ImageTypeDetector.detect(directory.resolve("missing.png"))).isEqualTo(ImageType.UNKNOWN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"png", "tiff"})
    void checksFullGrayscalePrecisionAndEveryPixel(String format) throws Exception {
        var sixteenBit = new BufferedImage(4, 4, BufferedImage.TYPE_USHORT_GRAY);
        sixteenBit.getRaster().setSample(0, 0, 0, 65535);
        assertType(sixteenBit, format, ImageType.BINARY);
        sixteenBit.getRaster().setSample(3, 3, 0, 1);
        assertType(sixteenBit, format, ImageType.GRAYSCALE);
        assertType(grayscaleWithIntermediateSample(BufferedImage.TYPE_BYTE_GRAY), format, ImageType.GRAYSCALE);
        assertType(new BufferedImage(4, 4, BufferedImage.TYPE_BYTE_GRAY), format, ImageType.BINARY);
    }

    @Test
    void checksUsedGrayscalePaletteEntriesAndKeepsGrayLevelsDistinctFromBinary() throws Exception {
        byte[] values = {0, (byte) 127, (byte) 255};
        var palette = new IndexColorModel(8, 3, values, values, values);
        var image = new BufferedImage(4, 4, BufferedImage.TYPE_BYTE_INDEXED, palette);
        image.getRaster().setSample(0, 0, 0, 2);
        assertType(image, "png", ImageType.BINARY);
        image.getRaster().setSample(3, 3, 0, 1);
        assertType(image, "png", ImageType.GRAYSCALE);
    }

    private BufferedImage grayscaleWithIntermediateSample(int type) {
        var image = new BufferedImage(4, 4, type);
        image.getRaster().setSample(3, 3, 0, 127);
        return image;
    }

    private void assertType(BufferedImage image, String format, ImageType expected) throws Exception {
        Path path = directory.resolve(java.util.UUID.randomUUID() + "." + format);
        assertThat(ImageIO.write(image, format, path.toFile())).isTrue();
        assertThat(ImageTypeDetector.detect(path)).as("%s / %s", format, image.getType()).isEqualTo(expected);
    }
}

package de.uniwue.zpd.dachs.larex.backend.service.image;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.zip.CRC32;
import static org.assertj.core.api.Assertions.*;

class NativeImagePresentationTest {
    @TempDir Path root;
    private byte[] exif() {
        return new byte[]{'I','I',42,0,8,0,0,0,1,0,0x12,1,3,0,1,0,0,0,6,0,0,0,0,0,0,0};
    }
    @Test void stripsOnlyPngExifAndPreservesCompressedPixels() throws Exception {
        var image = new BufferedImage(12, 8, BufferedImage.TYPE_INT_RGB);
        image.setRGB(2, 3, 0xff0000);
        var base = new ByteArrayOutputStream(); ImageIO.write(image, "png", base);
        byte[] original = base.toByteArray();
        var input = new ByteArrayOutputStream(); input.write(original, 0, 33);
        var chunk = new DataOutputStream(input); byte[] type = {'e','X','I','f'}, exif = exif();
        chunk.writeInt(exif.length); chunk.write(type); chunk.write(exif);
        var crc = new CRC32(); crc.update(type); crc.update(exif); chunk.writeInt((int) crc.getValue());
        input.write(original, 33, original.length - 33);
        Path file = root.resolve("oriented.png"); Files.write(file, input.toByteArray());
        var display = NativeImagePresentation.open(file); var output = new ByteArrayOutputStream(); display.writeTo(output);
        assertThat(display.width()).isEqualTo(12); assertThat(display.height()).isEqualTo(8);
        assertThat(output.toByteArray()).isEqualTo(original);
        assertThat(Files.readAllBytes(file)).isEqualTo(input.toByteArray());
    }
    @ParameterizedTest @ValueSource(strings = {"lossy", "lossless"})
    void preservesWebpPixelsAndOtherChunksWhileRemovingPaddedExif(String kind) throws Exception {
        byte[] original;
        try (var in = getClass().getResourceAsStream("/images/preview-" + kind + ".webp")) { original = in.readAllBytes(); }
        byte[] extended = {0x0c,0,0,0,2,0,0,1,0,0}; // EXIF + XMP, canvas 3 x 2
        var chunks = new ByteArrayOutputStream(); chunk(chunks, "VP8X", extended);
        chunks.write(original, 12, original.length - 12);
        chunk(chunks, "EXIF", java.util.Arrays.copyOf(exif(), 27)); // odd length exercises RIFF padding
        chunk(chunks, "XMP ", new byte[]{1,2,3});
        var fileBytes = new ByteArrayOutputStream(); fileBytes.write(new byte[]{'R','I','F','F'});
        fileBytes.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(chunks.size() + 4).array());
        fileBytes.write(new byte[]{'W','E','B','P'}); chunks.writeTo(fileBytes);
        Path source = root.resolve("source.webp"); Files.write(source, fileBytes.toByteArray());
        var display = NativeImagePresentation.open(source); var output = new ByteArrayOutputStream(); display.writeTo(output);
        byte[] bytes = output.toByteArray();
        assertThat(display.width()).isEqualTo(3); assertThat(display.height()).isEqualTo(2);
        assertThat(bytes[20]).isEqualTo((byte) 0x04); // XMP flag remains, EXIF flag is cleared.
        assertThat(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(4)).isEqualTo(bytes.length - 8);
        assertThat(new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("EXIF").contains("XMP ");
        var before = ImageIO.read(new ByteArrayInputStream(original)); var after = ImageIO.read(new ByteArrayInputStream(bytes));
        assertThat(after.getRGB(0,0,3,2,null,0,3)).containsExactly(before.getRGB(0,0,3,2,null,0,3));
        assertThat(Files.readAllBytes(source)).isEqualTo(fileBytes.toByteArray());
    }
    private void chunk(OutputStream output, String type, byte[] data) throws IOException {
        output.write(type.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        output.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(data.length).array());
        output.write(data); if ((data.length & 1) != 0) output.write(0);
    }
}

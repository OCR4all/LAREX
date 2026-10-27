package de.uniwue.zpd.dachs.larex.backend.service.image;

import javax.imageio.ImageIO;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Streams native compressed pixels without allocating a raster or changing the source file. */
public record NativeImagePresentation(Path source, int width, int height, String mediaType, long webpExifBytes) {
    public static NativeImagePresentation open(Path source) throws IOException {
        Files.size(source); // Preserve missing-file handling before ImageIO wraps the error.
        try (var input = ImageIO.createImageInputStream(source.toFile())) {
            if (input == null) throw new IOException("Cannot read image");
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported image format");
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                String type = "image/" + (format.equals("jpg") ? "jpeg" : format);
                return new NativeImagePresentation(source, reader.getWidth(0), reader.getHeight(0), type,
                        type.equals("image/webp") ? webpExifBytes(source) : 0);
            } finally { reader.dispose(); }
        }
    }

    public boolean browserNative() {
        return switch (mediaType) {
            case "image/jpeg", "image/png", "image/webp", "image/gif", "image/bmp" -> true;
            default -> false;
        };
    }

    public void writeTo(OutputStream output) throws IOException {
        try (var input = new DataInputStream(new BufferedInputStream(Files.newInputStream(source)))) {
            switch (mediaType) {
                case "image/jpeg" -> jpeg(input, output);
                case "image/png" -> png(input, output);
                case "image/webp" -> webp(input, output);
                default -> input.transferTo(output);
            }
        }
    }

    // Removing EXIF only in the response keeps browser orientation in stored-pixel
    // coordinates, matching previews and PAGE XML. Pixel data and ICC profiles survive.
    private static void jpeg(DataInputStream input, OutputStream output) throws IOException {
        output.write(input.readNBytes(2));
        while (true) {
            if (input.readUnsignedByte() != 0xff) throw new IOException("Invalid JPEG marker");
            int marker;
            do { marker = input.readUnsignedByte(); } while (marker == 0xff);
            if (marker == 0xd9 || marker == 0x01 || (marker >= 0xd0 && marker <= 0xd7)) {
                output.write(0xff); output.write(marker);
                if (marker == 0xd9) return;
                continue;
            }
            int length = input.readUnsignedShort();
            if (length < 2) throw new IOException("Invalid JPEG segment");
            byte[] prefix = input.readNBytes(Math.min(6, length - 2));
            boolean exif = marker == 0xe1 && java.util.Arrays.equals(prefix, new byte[]{'E', 'x', 'i', 'f', 0, 0});
            if (exif) input.skipNBytes(length - 2 - prefix.length);
            else {
                output.write(0xff); output.write(marker); output.write(length >>> 8); output.write(length);
                output.write(prefix); copy(input, output, length - 2 - prefix.length);
            }
            if (marker == 0xda) { input.transferTo(output); return; }
        }
    }

    private static void png(DataInputStream input, OutputStream output) throws IOException {
        output.write(input.readNBytes(8));
        while (true) {
            byte[] header = input.readNBytes(8);
            if (header.length != 8) throw new EOFException("Incomplete PNG chunk");
            long length = Integer.toUnsignedLong(ByteBuffer.wrap(header).getInt());
            String type = new String(header, 4, 4, java.nio.charset.StandardCharsets.US_ASCII);
            if (type.equals("eXIf")) input.skipNBytes(length + 4);
            else { output.write(header); copy(input, output, length + 4); }
            if (type.equals("IEND")) return;
        }
    }

    private static long webpExifBytes(Path source) throws IOException {
        long removed = 0;
        try (var file = new RandomAccessFile(source.toFile(), "r")) {
            file.seek(12);
            while (file.getFilePointer() < file.length()) {
                byte[] header = new byte[8]; file.readFully(header);
                long length = Integer.toUnsignedLong(ByteBuffer.wrap(header, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
                long next = file.getFilePointer() + length + (length & 1);
                if (next > file.length()) throw new IOException("Invalid WebP chunk");
                if (new String(header, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("EXIF")) removed += 8 + length + (length & 1);
                file.seek(next);
            }
        }
        return removed;
    }

    private void webp(DataInputStream input, OutputStream output) throws IOException {
        byte[] riff = input.readNBytes(12);
        if (riff.length != 12) throw new EOFException("Incomplete WebP header");
        var size = ByteBuffer.wrap(riff).order(ByteOrder.LITTLE_ENDIAN);
        size.putInt(4, (int) (Integer.toUnsignedLong(size.getInt(4)) - webpExifBytes));
        output.write(riff);
        while (true) {
            byte[] header = input.readNBytes(8);
            if (header.length == 0) return;
            if (header.length != 8) throw new EOFException("Incomplete WebP chunk");
            long length = Integer.toUnsignedLong(ByteBuffer.wrap(header, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
            String type = new String(header, 0, 4, java.nio.charset.StandardCharsets.US_ASCII);
            long padded = length + (length & 1);
            if (type.equals("EXIF")) input.skipNBytes(padded);
            else {
                output.write(header);
                if (type.equals("VP8X") && padded > 0) {
                    output.write(input.readUnsignedByte() & ~0x08); padded--;
                }
                copy(input, output, padded);
            }
        }
    }

    private static void copy(InputStream input, OutputStream output, long remaining) throws IOException {
        byte[] buffer = new byte[16 * 1024];
        while (remaining > 0) {
            int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read < 0) throw new EOFException("Incomplete image data");
            output.write(buffer, 0, read); remaining -= read;
        }
    }
}

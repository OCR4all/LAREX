package de.uniwue.zpd.dachs.larex.backend.service.image;

import app.photofox.vipsffm.VImage;
import app.photofox.vipsffm.VSource;
import app.photofox.vipsffm.Vips;
import app.photofox.vipsffm.VipsError;
import app.photofox.vipsffm.VipsHelper;
import app.photofox.vipsffm.VipsOption;
import app.photofox.vipsffm.enums.VipsAccess;
import app.photofox.vipsffm.enums.VipsSize;
import de.uniwue.zpd.dachs.larex.backend.config.ImagePresentationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class ImageDecoder {
    private static final Logger log = LoggerFactory.getLogger(ImageDecoder.class);
    private static volatile boolean configured;

    public ImagePresentationService.Presentation decode(Path source, int edge, ImagePresentationProperties limits) throws IOException {
        var result = new AtomicReference<ImagePresentationService.Presentation>();
        try {
            configure();
            Vips.run(arena -> {
                var header = VImage.newFromSource(arena, VSource.newFromFile(arena, source.toString()),
                        VipsOption.Enum("access", VipsAccess.ACCESS_SEQUENTIAL));
                int width = header.getWidth(), height = header.getHeight();
                if (width > limits.maxSourceDimension() || height > limits.maxSourceDimension()
                        || (long) width * height > limits.maxSourcePixels()) {
                    throw new UncheckedIOException(new ImagePresentationException(413, "Image exceeds the configured source pixel limit"));
                }
                var image = edge == 0 ? header : VImage.thumbnailSource(arena, VSource.newFromFile(arena, source.toString()), edge,
                        VipsOption.Int("height", edge),
                        VipsOption.Enum("size", VipsSize.SIZE_DOWN),
                        VipsOption.Boolean("no-rotate", true));
                if (image.hasAlpha()) image = image.flatten(VipsOption.ArrayDouble("background", List.of(255.0)));
                var encoded = edge == 0
                        ? image.pngsaveBuffer(VipsOption.Boolean("strip", true))
                        : image.jpegsaveBuffer(VipsOption.Int("Q", 88), VipsOption.Boolean("strip", true));
                result.set(new ImagePresentationService.Presentation(encoded.getBytes(), width, height,
                        edge == 0 ? "image/png" : "image/jpeg"));
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        } catch (VipsError e) {
            if (e.getMessage() != null && e.getMessage().contains("not in a known format"))
                throw new ImagePresentationException(415, "Unsupported image format");
            throw new ImagePresentationException(422, "Image cannot be decoded for display");
        } catch (LinkageError e) {
            log.error("libvips is not available; install libvips to display converted images", e);
            throw new ImagePresentationException(503, "Image conversion is unavailable on this server");
        }
        return result.get();
    }

    private static void configure() {
        if (configured) return;
        synchronized (ImageDecoder.class) {
            if (configured) return;
            Vips.init();
            Vips.disableOperationCache();
            VipsHelper.block_untrusted_set(true);
            configured = true;
        }
    }
}

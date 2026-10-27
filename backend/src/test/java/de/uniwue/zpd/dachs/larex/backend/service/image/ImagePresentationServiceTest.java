package de.uniwue.zpd.dachs.larex.backend.service.image;

import de.uniwue.zpd.dachs.larex.backend.config.UploadDirectoryProperties;
import de.uniwue.zpd.dachs.larex.backend.service.upload.UploadPathService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class ImagePresentationServiceTest {
    @TempDir Path directory;
    private ImagePresentationService service;

    @BeforeEach
    void setUp() {
        var properties = new UploadDirectoryProperties();
        properties.setRootDirectory(directory);
        service = new ImagePresentationService(new UploadPathService(properties));
    }

    @AfterEach
    void tearDown() {
        service.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"png", "jpg", "tiff"})
    void boundsLargeImagesWithoutModifyingOriginals(String format) throws Exception {
        var source = new BufferedImage(6400, 4200, BufferedImage.TYPE_BYTE_GRAY);
        Path file = directory.resolve("scan." + format);
        assertThat(ImageIO.write(source, format, file.toFile())).isTrue();
        source.flush();
        byte[] original = Files.readAllBytes(file);
        var overview = service.get(file.getFileName().toString(), ImagePresentationService.Size.overview);
        var decoded = ImageIO.read(new ByteArrayInputStream(overview.bytes()));
        assertThat(decoded.getWidth()).isEqualTo(1600);
        assertThat(decoded.getHeight()).isEqualTo(1050);
        assertThat(overview.originalWidth()).isEqualTo(6400);
        assertThat(overview.originalHeight()).isEqualTo(4200);
        assertThat(overview.bytes().length).isLessThan(1_000_000);
        assertThat(Files.readAllBytes(file)).isEqualTo(original);
        assertThat(service.get(file.getFileName().toString(), ImagePresentationService.Size.overview)).isSameAs(overview);
        var detail = service.get(file.getFileName().toString(), ImagePresentationService.Size.detail);
        assertThat(ImageIO.read(new ByteArrayInputStream(detail.bytes())).getWidth()).isEqualTo(3200);
    }

    @Test
    void preservesSmallImageDimensionsAndCompositesTransparencyOnWhite() throws Exception {
        ImageIO.write(new BufferedImage(80, 120, BufferedImage.TYPE_INT_ARGB), "png", directory.resolve("small.png").toFile());
        var result = service.get("small.png", ImagePresentationService.Size.detail);
        var decoded = ImageIO.read(new ByteArrayInputStream(result.bytes()));
        assertThat(decoded.getWidth()).isEqualTo(80);
        assertThat(decoded.getHeight()).isEqualTo(120);
        assertThat(decoded.getRGB(10, 10) & 0xffffff).isEqualTo(0xffffff);
    }

    @Test
    void invalidatesCachedPresentationWhenSourceChanges() throws Exception {
        var file = directory.resolve("scan.png").toFile();
        ImageIO.write(new BufferedImage(80, 120, BufferedImage.TYPE_BYTE_BINARY), "png", file);
        var first = service.get("scan.png", ImagePresentationService.Size.overview);
        ImageIO.write(new BufferedImage(100, 200, BufferedImage.TYPE_BYTE_GRAY), "png", file);
        var second = service.get("scan.png", ImagePresentationService.Size.overview);
        assertThat(second).isNotSameAs(first);
        assertThat(second.originalWidth()).isEqualTo(100);
    }

    @Test
    void rejectsUnsupportedSourcesRatherThanReturningOriginalBytes() throws Exception {
        Files.writeString(directory.resolve("not-an-image.png"), "original content");
        assertThatIOException().isThrownBy(() -> service.get("not-an-image.png", ImagePresentationService.Size.overview));
        assertThatIllegalArgumentException().isThrownBy(() -> service.get("../escape.png", ImagePresentationService.Size.detail));
    }

    @Test
    void generatesBoundedThumbnailsFromSourcePixels() throws Exception {
        var scan = new BufferedImage(6400, 4200, BufferedImage.TYPE_BYTE_GRAY);
        ImageIO.write(scan, "png", directory.resolve("scan.png").toFile());
        scan.flush();
        var result = service.get("scan.png", ImagePresentationService.Size.thumbnail);
        var decoded = ImageIO.read(new ByteArrayInputStream(result.bytes()));
        assertThat(result.originalWidth()).isEqualTo(6400);
        assertThat(result.originalHeight()).isEqualTo(4200);
        assertThat(decoded.getWidth()).isEqualTo(320);
        assertThat(decoded.getRGB(10, 10) & 0xffffff).isZero();
    }

}

package de.uniwue.zpd.dachs.larex.backend.service.image;

import de.uniwue.zpd.dachs.larex.backend.config.ImagePresentationProperties;
import de.uniwue.zpd.dachs.larex.backend.config.UploadDirectoryProperties;
import de.uniwue.zpd.dachs.larex.backend.service.upload.UploadPathService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImagePresentationHardeningTest {
    @TempDir Path root;
    private UploadPathService paths() {
        var p = new UploadDirectoryProperties(); p.setRootDirectory(root); return new UploadPathService(p);
    }
    private ImagePresentationProperties limits(int queue, long diskBytes, Duration decodeTimeout) {
        return new ImagePresentationProperties(queue, Duration.ofSeconds(10), decodeTimeout, 128,
                500_000_000, 100_000, 536_870_912, diskBytes, Duration.ofDays(7), null);
    }
    private Path image(String name) throws Exception {
        var image = new BufferedImage(12, 8, BufferedImage.TYPE_INT_RGB);
        image.setRGB(1, 1, 0xff0000); image.setRGB(10, 1, 0x00ff00); image.setRGB(1, 6, 0x0000ff);
        var file = root.resolve(name); ImageIO.write(image, name.endsWith("jpg") ? "jpeg" : "png", file.toFile()); return file;
    }
    @ParameterizedTest @ValueSource(ints = {1,2,3,4,5,6,7,8})
    void previewAndNativeDisplayUseStoredPixelsForEveryExifOrientation(int orientation) throws Exception {
        Path file = image("source.jpg"); byte[] jpeg = Files.readAllBytes(file);
        byte[] exif = {'E','x','i','f',0,0,'I','I',42,0,8,0,0,0,1,0,0x12,1,3,0,1,0,0,0,(byte)orientation,0,0,0,0,0,0,0};
        var out = new ByteArrayOutputStream(); out.write(jpeg, 0, 2); out.write(new byte[]{(byte)255,(byte)225,0,(byte)(exif.length+2)}); out.write(exif); out.write(jpeg,2,jpeg.length-2);
        Files.write(file, out.toByteArray()); byte[] original = Files.readAllBytes(file);
        try (var service = new ClosingService(new ImagePresentationService(paths()))) {
            var preview = service.value.get("source.jpg", ImagePresentationService.Size.detail);
            var nativeImage = service.value.nativeImage("source.jpg");
            assertThat(nativeImage.mediaType()).isEqualTo("image/jpeg");
            var nativeBytes = new ByteArrayOutputStream(); nativeImage.writeTo(nativeBytes);
            assertThat(nativeBytes.toByteArray()).isEqualTo(jpeg);
            var decoded = ImageIO.read(new ByteArrayInputStream(nativeBytes.toByteArray()));
            assertThat(decoded.getWidth()).isEqualTo(12); assertThat(decoded.getHeight()).isEqualTo(8);
            assertThat(preview.originalWidth()).isEqualTo(nativeImage.width());
            assertThat(preview.originalHeight()).isEqualTo(nativeImage.height());
            var stored = ImageIO.read(file.toFile());
            for (int y=0;y<8;y++) for(int x=0;x<12;x++) assertThat(decoded.getRGB(x,y)).isEqualTo(stored.getRGB(x,y));
            assertThat(Files.readAllBytes(file)).isEqualTo(original);
        }
    }
    @ParameterizedTest @ValueSource(strings = {"lossy", "lossless"})
    void decodesAcceptedWebpImages(String kind) throws Exception {
        try(var stream=getClass().getResourceAsStream("/images/preview-"+kind+".webp")) { Files.copy(stream,root.resolve("image.webp")); }
        try(var service=new ClosingService(new ImagePresentationService(paths()))) {
            var result=service.value.get("image.webp",ImagePresentationService.Size.detail);
            assertThat(result.originalWidth()).isEqualTo(3); assertThat(result.originalHeight()).isEqualTo(2);
            assertThat(ImageIO.read(new ByteArrayInputStream(result.bytes()))).isNotNull();
        }
    }
    @Test void streamsExplicitOriginalsWithoutApplyingPreviewLimitsOrCallingTheDecoder() throws Exception {
        Path source = image("source.png");
        var config = new ImagePresentationProperties(1, Duration.ofSeconds(10), Duration.ofSeconds(5), 128,
                1, 1, 1, 10000, Duration.ofDays(7), null);
        var decoder = mock(ImageDecoderProcess.class);
        try (var service = new ClosingService(new ImagePresentationService(paths(), config, decoder))) {
            var original = service.value.nativeImage("source.png");
            var output = new ByteArrayOutputStream(); original.writeTo(output);
            assertThat(original.width()).isEqualTo(12); assertThat(original.height()).isEqualTo(8);
            assertThat(output.toByteArray()).isEqualTo(Files.readAllBytes(source));
            verifyNoInteractions(decoder);
        }
    }
    @Test void persistsRevisionsAndRemovesDeletedOrOversizedCacheEntries() throws Exception {
        Path file=image("source.png");
        var decoder=mock(ImageDecoderProcess.class);
        when(decoder.decode(any(),any(),anyInt(),any())).thenReturn(new ImagePresentationService.Presentation(new byte[16],12,8));
        var config=limits(1,20,Duration.ofSeconds(5));
        try(var first=new ClosingService(new ImagePresentationService(paths(),config,decoder))) { first.value.get("source.png",ImagePresentationService.Size.detail); }
        try(var second=new ClosingService(new ImagePresentationService(paths(),config,decoder))) {
            second.value.get("source.png",ImagePresentationService.Size.detail); verify(decoder,times(1)).decode(any(),any(),anyInt(),any());
            second.value.get("source.png",ImagePresentationService.Size.overview);
            try(var files=Files.list(root.resolve(".presentations"))) { assertThat(files.filter(p->p.toString().endsWith(".data")).count()).isEqualTo(1); }
            Files.delete(file); second.value.pruneDisk();
            try(var files=Files.list(root.resolve(".presentations"))) { assertThat(files.filter(p->p.toString().endsWith(".data")).count()).isZero(); }
        }
    }
    @Test void rejectsExcessQueuedWorkInsteadOfBlockingUnboundedRequests() throws Exception {
        image("a.png"); image("b.png"); image("c.png");
        var decoder=mock(ImageDecoderProcess.class); var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
        when(decoder.decode(any(),any(),anyInt(),any())).thenAnswer(call->{ entered.countDown(); release.await(5,TimeUnit.SECONDS); return new ImagePresentationService.Presentation(new byte[]{1},12,8); });
        var callers=Executors.newFixedThreadPool(2);
        try(var service=new ClosingService(new ImagePresentationService(paths(),limits(1,10000,Duration.ofSeconds(5)),decoder))) {
            var first=callers.submit(()->service.value.get("a.png",ImagePresentationService.Size.detail)); assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
            var second=callers.submit(()->service.value.get("b.png",ImagePresentationService.Size.detail));
            // Wait only for the second call to reach the service's pending registry.
            var field=ImagePresentationService.class.getDeclaredField("pending"); field.setAccessible(true);
            var pending=(java.util.Map<?,?>)field.get(service.value); long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
            while(System.nanoTime()<until) { synchronized(pending){if(pending.size()==2)break;} Thread.sleep(5); }
            assertThatThrownBy(()->service.value.get("c.png",ImagePresentationService.Size.detail))
                    .isInstanceOfSatisfying(ImagePresentationException.class,e->assertThat(e.status()).isEqualTo(503));
            release.countDown(); first.get(3,TimeUnit.SECONDS); second.get(3,TimeUnit.SECONDS);
        } finally { release.countDown(); callers.shutdownNow(); }
    }
    @Test void killsAWorkerThatExceedsItsExecutionDeadline() throws Exception {
        Path source=image("source.png"),scratch=Files.createDirectory(root.resolve("work"));
        assertThatThrownBy(()->new ImageDecoderProcess().decode(source,scratch,3200,limits(1,10000,Duration.ofMillis(1))))
                .isInstanceOfSatisfying(ImagePresentationException.class,e->assertThat(e.status()).isEqualTo(504));
    }
    @Test void invalidatesChangedSourcesAndRecoversFromCorruptDiskMetadata() throws Exception {
        Path file = image("source.png");
        var decoder = mock(ImageDecoderProcess.class);
        when(decoder.decode(any(), any(), anyInt(), any())).thenReturn(new ImagePresentationService.Presentation(new byte[16], 12, 8));
        var config = limits(1,  10000, Duration.ofSeconds(5));
        try (var service = new ClosingService(new ImagePresentationService(paths(), config, decoder))) {
            service.value.get("source.png", ImagePresentationService.Size.detail);
            Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(Files.getLastModifiedTime(file).toMillis() + 2000));
            service.value.get("source.png", ImagePresentationService.Size.detail);
            verify(decoder, times(2)).decode(any(), any(), anyInt(), any());
        }
        try (var entries = Files.list(root.resolve(".presentations"))) {
            for (Path meta : entries.filter(p -> p.toString().endsWith(".meta")).toList()) Files.writeString(meta, "width=invalid\n");
        }
        try (var service = new ClosingService(new ImagePresentationService(paths(), config, decoder))) {
            service.value.get("source.png", ImagePresentationService.Size.detail);
            verify(decoder, times(3)).decode(any(), any(), anyInt(), any());
            service.value.pruneDisk();
            try (var entries = Files.list(root.resolve(".presentations"))) {
                assertThat(entries.filter(p -> p.toString().endsWith(".data")).count()).isEqualTo(1);
            }
        }
    }
    @Test void regeneratesCorruptedDerivativeBytes() throws Exception {
        image("source.png");
        var decoder = mock(ImageDecoderProcess.class);
        when(decoder.decode(any(), any(), anyInt(), any())).thenReturn(new ImagePresentationService.Presentation(new byte[16], 12, 8));
        var config = limits(1,  10000, Duration.ofSeconds(5));
        try (var service = new ClosingService(new ImagePresentationService(paths(), config, decoder))) {
            service.value.get("source.png", ImagePresentationService.Size.detail);
        }
        try (var entries = Files.list(root.resolve(".presentations"))) {
            Files.write(entries.filter(p -> p.toString().endsWith(".data")).findFirst().orElseThrow(), new byte[]{1, 2});
        }
        try (var service = new ClosingService(new ImagePresentationService(paths(), config, decoder))) {
            assertThat(service.value.get("source.png", ImagePresentationService.Size.detail).bytes()).hasSize(16);
            verify(decoder, times(2)).decode(any(), any(), anyInt(), any());
        }
    }
    @Test void rejectsSourceDimensionsBeforeRasterAllocation() throws Exception {
        image("source.png");
        var config = new ImagePresentationProperties(1, Duration.ofSeconds(10), Duration.ofSeconds(5), 128,
                50, 100, 10000, 10000, Duration.ofDays(7), null);
        try (var service = new ClosingService(new ImagePresentationService(paths(), config, new ImageDecoderProcess()))) {
            assertThatThrownBy(() -> service.value.get("source.png", ImagePresentationService.Size.detail))
                    .isInstanceOfSatisfying(ImagePresentationException.class, e -> assertThat(e.status()).isEqualTo(413));
        }
    }
    private record ClosingService(ImagePresentationService value) implements AutoCloseable { public void close(){value.close();} }
}

package de.uniwue.zpd.dachs.larex.backend.controller.page;
import de.uniwue.zpd.dachs.larex.backend.service.image.ImagePresentationService;
import org.springframework.http.*;
import java.time.Duration;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import de.uniwue.zpd.dachs.larex.backend.service.image.NativeImagePresentation;
public final class PresentationResponses {
    private PresentationResponses() {}
    public static ResponseEntity<StreamingResponseBody> nativeImage(NativeImagePresentation image) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.mediaType()))
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePrivate())
                .header("X-Original-Width", String.valueOf(image.width()))
                .header("X-Original-Height", String.valueOf(image.height()))
                .body(image::writeTo);
    }
    public static ResponseEntity<StreamingResponseBody> ok(ImagePresentationService.Presentation image) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.mediaType()))
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePrivate())
                .header("X-Original-Width", String.valueOf(image.originalWidth()))
                .header("X-Original-Height", String.valueOf(image.originalHeight()))
                .contentLength(image.bytes().length).body(output -> output.write(image.bytes()));
    }
}

package de.uniwue.zpd.dachs.larex.backend.controller.page;

import de.uniwue.zpd.dachs.larex.backend.service.image.ImagePresentationService;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.NoSuchFileException;

@RestController
@RequestMapping("/projects/{projectId}/pages/images")
public class ImagePresentationController {
    private final PageService pages;
    private final ImagePresentationService presentations;

    public ImagePresentationController(PageService pages, ImagePresentationService presentations) {
        this.pages = pages;
        this.presentations = presentations;
    }

    @GetMapping("/{imageId}/presentation")
    public ResponseEntity<StreamingResponseBody> getPresentation(
            @PathVariable String projectId, @PathVariable String imageId,
            @RequestParam(defaultValue = "overview") ImagePresentationService.Size size,
            @AuthenticationPrincipal(expression = "subject") String userId) throws IOException {
        // Check authorization before consulting the shared derivative cache.
        var image = pages.getImageById(imageId, userId);
        if (image == null || !projectId.equals(image.getPage().getProject().getId())) {
            return ResponseEntity.notFound().build();
        }
        try {
            if (size == ImagePresentationService.Size.original) {
                var nativeImage = presentations.nativeImage(image.getFilePath());
                if (nativeImage.browserNative()) return PresentationResponses.nativeImage(nativeImage);
            }
            var presentation = presentations.get(image.getFilePath(), size);
            return PresentationResponses.ok(presentation);
        } catch (NoSuchFileException e) {
            return ResponseEntity.notFound().build();
        }
    }
}

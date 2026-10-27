package de.uniwue.zpd.dachs.larex.backend.controller.dataset;

import de.uniwue.zpd.dachs.larex.backend.service.dataset.DatasetCopyAnnotationService;
import de.uniwue.zpd.dachs.larex.backend.service.image.ImagePresentationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import de.uniwue.zpd.dachs.larex.backend.controller.page.PresentationResponses;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.NoSuchFileException;

@RestController
@RequestMapping("/workspaces/{workspaceId}/datasets/{datasetId}/items/{itemId}/images")
public class DatasetImagePresentationController {
    private final DatasetCopyAnnotationService access;
    private final ImagePresentationService presentations;

    public DatasetImagePresentationController(DatasetCopyAnnotationService access, ImagePresentationService presentations) {
        this.access = access;
        this.presentations = presentations;
    }

    @GetMapping("/{imageId}/presentation")
    public ResponseEntity<StreamingResponseBody> getPresentation(
            @PathVariable String workspaceId, @PathVariable String datasetId,
            @PathVariable String itemId, @PathVariable String imageId,
            @RequestParam(defaultValue = "detail") ImagePresentationService.Size size,
            @AuthenticationPrincipal(expression = "subject") String userId) throws IOException {
        try {
            var context = access.resolveImageAccessContext(workspaceId, datasetId, itemId, imageId, userId);
            if (size == ImagePresentationService.Size.original) {
                var nativeImage = presentations.nativeImage(context.copyImage().getFilePath());
                if (nativeImage.browserNative()) return PresentationResponses.nativeImage(nativeImage);
            }
            var presentation = presentations.get(context.copyImage().getFilePath(), size);
            return PresentationResponses.ok(presentation);
        } catch (IllegalArgumentException | NoSuchFileException e) {
            return ResponseEntity.notFound().build();
        }
    }
}

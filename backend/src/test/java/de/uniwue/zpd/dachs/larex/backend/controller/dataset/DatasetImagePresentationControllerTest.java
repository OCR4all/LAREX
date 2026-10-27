package de.uniwue.zpd.dachs.larex.backend.controller.dataset;

import de.uniwue.zpd.dachs.larex.backend.entity.DatasetItemCopyFile;
import de.uniwue.zpd.dachs.larex.backend.service.dataset.DatasetCopyAnnotationService;
import de.uniwue.zpd.dachs.larex.backend.service.image.ImagePresentationService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DatasetImagePresentationControllerTest {
    private final DatasetCopyAnnotationService access = mock(DatasetCopyAnnotationService.class);
    private final ImagePresentationService presentations = mock(ImagePresentationService.class);
    private final DatasetImagePresentationController controller = new DatasetImagePresentationController(access, presentations);

    @Test
    void checksDatasetItemAccessBeforeGeneratingOrReadingCachedPreviews() throws Exception {
        when(access.resolveImageAccessContext("ws", "ds", "item", "image", "user"))
                .thenThrow(new IllegalArgumentException("No access"));
        assertThat(controller.getPresentation("ws", "ds", "item", "image", ImagePresentationService.Size.detail, "user")
                .getStatusCode().value()).isEqualTo(404);
        verifyNoInteractions(presentations);
    }

    @Test
    void servesTheAuthorizedCopyWithItsOriginalDimensions() throws Exception {
        var file = new DatasetItemCopyFile();
        file.setFilePath("copy.png");
        when(access.resolveImageAccessContext("ws", "ds", "item", "image", "user"))
                .thenReturn(new DatasetCopyAnnotationService.DatasetCopyImageAccessContext(null, null, file, null));
        when(presentations.get("copy.png", ImagePresentationService.Size.detail))
                .thenReturn(new ImagePresentationService.Presentation(new byte[]{1}, 19218, 12623));
        var response = controller.getPresentation("ws", "ds", "item", "image", ImagePresentationService.Size.detail, "user");
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getFirst("X-Original-Width")).isEqualTo("19218");
        assertThat(response.getHeaders().getCacheControl()).contains("private");
    }
}

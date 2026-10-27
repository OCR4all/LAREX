package de.uniwue.zpd.dachs.larex.backend.controller.page;

import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.service.image.ImagePresentationService;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ImagePresentationControllerTest {
    private final PageService pages = mock(PageService.class);
    private final ImagePresentationService presentations = mock(ImagePresentationService.class);
    private final ImagePresentationController controller = new ImagePresentationController(pages, presentations);

    @Test
    void authorizesImageAndProjectBeforeAccessingCache() throws Exception {
        assertThat(controller.getPresentation("project", "image", ImagePresentationService.Size.overview, "user").getStatusCode().value()).isEqualTo(404);
        var project = new Project();
        project.setId("different-project");
        var page = new Page();
        page.setProject(project);
        var image = new PageImage();
        image.setPage(page);
        when(pages.getImageById("image", "user")).thenReturn(image);
        assertThat(controller.getPresentation("project", "image", ImagePresentationService.Size.overview, "user").getStatusCode().value()).isEqualTo(404);
        verifyNoInteractions(presentations);
    }

    @Test
    void servesJpegWithPrivateCachingAndOriginalDimensions() throws Exception {
        var project = new Project();
        project.setId("project");
        var page = new Page();
        page.setProject(project);
        var image = new PageImage();
        image.setPage(page);
        image.setFilePath("scan.png");
        when(pages.getImageById("image", "user")).thenReturn(image);
        when(presentations.get("scan.png", ImagePresentationService.Size.detail))
                .thenReturn(new ImagePresentationService.Presentation(new byte[]{1, 2, 3}, 19218, 12623));
        var response = controller.getPresentation("project", "image", ImagePresentationService.Size.detail, "user");
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_JPEG);
        assertThat(response.getHeaders().getCacheControl()).contains("private");
        assertThat(response.getHeaders().getFirst("X-Original-Width")).isEqualTo("19218");
        var output = new java.io.ByteArrayOutputStream();
        response.getBody().writeTo(output);
        assertThat(output.toByteArray()).containsExactly(1, 2, 3);
    }
}

package de.uniwue.zpd.dachs.larex.backend.controller.page;

import de.uniwue.zpd.dachs.larex.backend.dto.PageMoveDto;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageCopyService;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageMoveService;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/workspaces/{workspaceId}/projects/{sourceProjectId}/pages/copy")
public class PageCopyController {
    private final PageMoveService previews;
    private final PageCopyService copies;

    public PageCopyController(PageMoveService previews, PageCopyService copies) {
        this.previews = previews;
        this.copies = copies;
    }

    @PostMapping("/preview")
    public PageMoveDto.Preview preview(@PathVariable String workspaceId, @PathVariable String sourceProjectId,
                                      @Valid @RequestBody PageMoveDto.Request request,
                                      @AuthenticationPrincipal(expression = "subject") String userId) {
        return previews.previewCopy(workspaceId, sourceProjectId, request, userId);
    }

    @PostMapping
    public PageMoveDto.Preview copy(@PathVariable String workspaceId, @PathVariable String sourceProjectId,
                                   @Valid @RequestBody PageMoveDto.Request request,
                                   @AuthenticationPrincipal(expression = "subject") String userId) throws IOException {
        return copies.copy(workspaceId, sourceProjectId, request, userId);
    }
}

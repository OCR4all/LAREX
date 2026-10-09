package de.uniwue.zpd.dachs.larex.backend.controller.page;

import de.uniwue.zpd.dachs.larex.backend.dto.PageMoveDto;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageMoveService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/workspaces/{workspaceId}/projects/{sourceProjectId}/pages/move")
public class PageMoveController {
    private final PageMoveService pageMoveService;

    public PageMoveController(PageMoveService pageMoveService) {
        this.pageMoveService = pageMoveService;
    }

    @PostMapping("/preview")
    public PageMoveDto.Preview preview(@PathVariable String workspaceId, @PathVariable String sourceProjectId,
                                      @Valid @RequestBody PageMoveDto.Request request,
                                      @AuthenticationPrincipal(expression = "subject") String userId) {
        return pageMoveService.preview(workspaceId, sourceProjectId, request, userId);
    }

    @PostMapping
    public PageMoveDto.Preview move(@PathVariable String workspaceId, @PathVariable String sourceProjectId,
                                   @Valid @RequestBody PageMoveDto.Request request,
                                   @AuthenticationPrincipal(expression = "subject") String userId) throws java.io.IOException {
        return pageMoveService.move(workspaceId, sourceProjectId, request, userId);
    }
}

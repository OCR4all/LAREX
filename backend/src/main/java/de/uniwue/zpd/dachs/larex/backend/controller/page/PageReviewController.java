package de.uniwue.zpd.dachs.larex.backend.controller.page;

import de.uniwue.zpd.dachs.larex.backend.dto.ReviewQueueDto;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageReviewQueueService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/workspaces/{workspaceId}/review-queue")
public class PageReviewController {

    private final PageReviewQueueService pageReviewQueueService;

    public PageReviewController(PageReviewQueueService pageReviewQueueService) {
        this.pageReviewQueueService = pageReviewQueueService;
    }

    @GetMapping
    public ResponseEntity<ReviewQueueDto.Response> getReviewQueue(
            @PathVariable String workspaceId,
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false) String submittedBy,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(pageReviewQueueService.getQueue(workspaceId, projectId, taskId, submittedBy, userId));
    }
}

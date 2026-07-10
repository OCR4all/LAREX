package de.uniwue.zpd.dachs.larex.backend.controller.project;

import de.uniwue.zpd.dachs.larex.backend.dto.IiifImportDto;
import de.uniwue.zpd.dachs.larex.backend.service.importer.IiifImportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/workspaces/{workspaceId}/iiif-import/jobs")
public class IiifImportJobController {

    private final IiifImportService iiifImportService;

    public IiifImportJobController(IiifImportService iiifImportService) {
        this.iiifImportService = iiifImportService;
    }

    @GetMapping
    public ResponseEntity<List<IiifImportDto.JobResponse>> listJobs(
            @PathVariable String workspaceId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(iiifImportService.listImportJobs(workspaceId, userId));
    }

    @PostMapping("/{jobId}/dismiss")
    public ResponseEntity<Void> dismissJob(
            @PathVariable String workspaceId,
            @PathVariable String jobId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        iiifImportService.dismissImportJob(workspaceId, userId, jobId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/history/dismiss")
    public ResponseEntity<IiifImportDto.DismissResponse> dismissHistory(
            @PathVariable String workspaceId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(iiifImportService.dismissImportJobHistory(workspaceId, userId));
    }
}

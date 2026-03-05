package de.uniwue.zpd.dachs.larex.backend.controller.admin;

import de.uniwue.zpd.dachs.larex.backend.dto.ImportJobDto;
import de.uniwue.zpd.dachs.larex.backend.service.importer.ServerImportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/admin/import")
@PreAuthorize("hasRole('GLOBAL_ADMIN')")
public class AdminImportController {

    private final ServerImportService serverImportService;

    public AdminImportController(ServerImportService serverImportService) {
        this.serverImportService = serverImportService;
    }

    /**
     * Validate that a path exists and is within allowed directories.
     */
    @PostMapping("/validate-path")
    public ResponseEntity<ImportJobDto.ValidatePathResponse> validatePath(
            @Valid @RequestBody ImportJobDto.ValidatePathRequest request) {

        ImportJobDto.ValidatePathResponse response = serverImportService.validatePath(request.path());
        return ResponseEntity.ok(response);
    }

    /**
     * Scan a directory and return file counts, potential conflicts, and quota check.
     */
    @PostMapping("/scan")
    public ResponseEntity<ImportJobDto.ScanResponse> scanDirectory(
            @Valid @RequestBody ImportJobDto.ScanRequest request,
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String workspaceId) throws IOException {

        ImportJobDto.ScanResponse response = serverImportService.scanDirectory(
                request.path(), projectId, workspaceId);
        return ResponseEntity.ok(response);
    }

    /**
     * Create and start an import job.
     */
    @PostMapping("/jobs")
    public ResponseEntity<ImportJobDto.JobResponse> createImportJob(
            @RequestParam String workspaceId,
            @Valid @RequestBody ImportJobDto.CreateJobRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        ImportJobDto.JobResponse job = serverImportService.createImportJob(userId, workspaceId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(job);
    }

    /**
     * List all import jobs.
     */
    @GetMapping("/jobs")
    public ResponseEntity<List<ImportJobDto.JobSummaryResponse>> getAllJobs() {
        List<ImportJobDto.JobSummaryResponse> jobs = serverImportService.getAllJobs();
        return ResponseEntity.ok(jobs);
    }

    /**
     * Get details of a specific import job.
     */
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<ImportJobDto.JobResponse> getJob(@PathVariable String jobId) {
        ImportJobDto.JobResponse job = serverImportService.getJob(jobId);
        return ResponseEntity.ok(job);
    }

    /**
     * Cancel a running import job.
     */
    @DeleteMapping("/jobs/{jobId}")
    public ResponseEntity<Void> cancelJob(@PathVariable String jobId) {
        serverImportService.cancelJob(jobId);
        return ResponseEntity.noContent().build();
    }
}

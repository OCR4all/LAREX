package de.uniwue.zpd.dachs.larex.backend.controller;

import de.uniwue.zpd.dachs.larex.backend.dto.BackupJobDto;
import de.uniwue.zpd.dachs.larex.backend.service.BackupJobService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/backup")
@PreAuthorize("hasRole('GLOBAL_ADMIN')")
public class AdminBackupController {

    private final BackupJobService backupJobService;

    public AdminBackupController(BackupJobService backupJobService) {
        this.backupJobService = backupJobService;
    }

    @PostMapping("/validate-path")
    public ResponseEntity<BackupJobDto.ValidatePathResponse> validatePath(
            @Valid @RequestBody BackupJobDto.ValidatePathRequest request) {

        return ResponseEntity.ok(backupJobService.validatePath(request));
    }

    @PostMapping("/jobs")
    public ResponseEntity<BackupJobDto.JobResponse> createJob(
            @Valid @RequestBody BackupJobDto.CreateJobRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        BackupJobDto.JobResponse response = backupJobService.createJob(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/jobs")
    public ResponseEntity<List<BackupJobDto.JobSummary>> listJobs() {
        return ResponseEntity.ok(backupJobService.listJobs());
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<BackupJobDto.JobResponse> getJob(@PathVariable String jobId) {
        return ResponseEntity.ok(backupJobService.getJob(jobId));
    }

    @DeleteMapping("/jobs/{jobId}")
    public ResponseEntity<Void> cancelJob(@PathVariable String jobId) {
        backupJobService.cancelJob(jobId);
        return ResponseEntity.noContent().build();
    }
}

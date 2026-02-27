package de.uniwue.zpd.dachs.larex.backend.controller;

import de.uniwue.zpd.dachs.larex.backend.dto.StorageCleanupDto;
import de.uniwue.zpd.dachs.larex.backend.service.StorageCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/storage")
@PreAuthorize("hasRole('GLOBAL_ADMIN')")
public class AdminStorageController {

    private static final Logger log = LoggerFactory.getLogger(AdminStorageController.class);

    private final StorageCleanupService storageCleanupService;

    public AdminStorageController(StorageCleanupService storageCleanupService) {
        this.storageCleanupService = storageCleanupService;
    }

    /**
     * Get storage overview including orphaned file statistics.
     */
    @GetMapping("/overview")
    public ResponseEntity<StorageCleanupDto.StorageOverview> getStorageOverview() {
        log.info("Admin requested storage overview");
        return ResponseEntity.ok(storageCleanupService.getStorageOverview());
    }

    /**
     * Get list of all orphaned files.
     */
    @GetMapping("/orphaned")
    public ResponseEntity<StorageCleanupDto.OrphanedFilesResponse> getOrphanedFiles(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search) {
        log.info("Admin requested orphaned files list (page={}, size={}, type={}, search={})",
                page, size, type, search);
        return ResponseEntity.ok(storageCleanupService.getOrphanedFiles(page, size, type, search));
    }

    /**
     * Delete specific orphaned files.
     */
    @PostMapping("/cleanup")
    public ResponseEntity<StorageCleanupDto.CleanupResponse> deleteOrphanedFiles(
            @Valid @RequestBody StorageCleanupDto.CleanupRequest request) {
        log.info("Admin requested cleanup of {} files", request.paths().size());
        return ResponseEntity.ok(storageCleanupService.deleteOrphanedFiles(request.paths()));
    }

    /**
     * Delete all orphaned files.
     */
    @PostMapping("/cleanup/all")
    public ResponseEntity<StorageCleanupDto.CleanupResponse> deleteAllOrphanedFiles() {
        log.info("Admin requested cleanup of all orphaned files");
        return ResponseEntity.ok(storageCleanupService.deleteAllOrphanedFiles());
    }
}

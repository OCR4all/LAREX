package de.uniwue.zpd.dachs.larex.backend.controller;

import de.uniwue.zpd.dachs.larex.backend.dto.AdminCreateUserRequest;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminWorkspaceDto;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserDto;
import de.uniwue.zpd.dachs.larex.backend.service.AdminService;
import de.uniwue.zpd.dachs.larex.backend.service.PageFilterIndexService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('GLOBAL_ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final PageFilterIndexService pageFilterIndexService;

    public AdminController(AdminService adminService,
                          PageFilterIndexService pageFilterIndexService) {
        this.adminService = adminService;
        this.pageFilterIndexService = pageFilterIndexService;
    }

    @GetMapping("/verify")
    public ResponseEntity<Map<String, Boolean>> verifyAdmin() {
        return ResponseEntity.ok(Map.of("isAdmin", true));
    }

    @GetMapping("/workspaces")
    public ResponseEntity<List<AdminWorkspaceDto>> getAllWorkspaces() {
        return ResponseEntity.ok(adminService.getAllWorkspacesForAdmin());
    }

    @GetMapping("/users")
    public ResponseEntity<List<AdminUserDto>> getAllUsers(
            @RequestParam(value = "includeServiceAccounts", defaultValue = "false") boolean includeServiceAccounts) {
        List<AdminUserDto> users = adminService.getAllUsersForAdmin(includeServiceAccounts);
        return ResponseEntity.ok(users);
    }

    @PostMapping("/users")
    public ResponseEntity<AdminUserDto> createUser(@Valid @RequestBody AdminCreateUserRequest request) {
        AdminUserDto createdUser = adminService.createUserForAdmin(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
    }

    // ============================================================================
    // Search Index Management
    // ============================================================================

    /**
     * Rebuild the global search index for all pages across all projects.
     * This is an async operation that runs in the background.
     */
    @PostMapping("/rebuild-search-index")
    public ResponseEntity<Map<String, String>> rebuildGlobalSearchIndex() {
        pageFilterIndexService.rebuildGlobalIndex();
        return ResponseEntity.accepted().body(Map.of(
                "message", "Global search index rebuild started. This operation runs in the background."
        ));
    }
}

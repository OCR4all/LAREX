package de.uniwue.zpd.dachs.larex.backend.controller.admin;

import de.uniwue.zpd.dachs.larex.backend.dto.AdminCreateUserRequest;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminErrorEventDetailDto;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminErrorEventPageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminErrorSummaryDto;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminGlobalCuratorRoleRequest;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminGlobalRolesDto;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserAuditEventDto;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminWorkspaceDto;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserDto;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserPageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.AdminUserStatusFilter;
import de.uniwue.zpd.dachs.larex.backend.service.admin.AdminService;
import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageFilterIndexService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@Validated
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

    @GetMapping("/errors/summary")
    public ResponseEntity<AdminErrorSummaryDto> getErrorSummary(
            @RequestParam(value = "days", defaultValue = "7") @Min(1) @Max(365) int days) {
        return ResponseEntity.ok(adminService.getErrorSummaryForAdmin(days));
    }

    @GetMapping("/errors")
    public ResponseEntity<AdminErrorEventPageDto> getErrors(
            @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(value = "size", defaultValue = "25") @Min(1) @Max(100) int size,
            @RequestParam(value = "days", defaultValue = "7") @Min(1) @Max(365) int days,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "userId", required = false) @Size(max = 255) String userId,
            @RequestParam(value = "workspaceId", required = false) @Size(max = 255) String workspaceId,
            @RequestParam(value = "query", required = false) @Size(max = 200) String query) {
        return ResponseEntity.ok(adminService.getErrorsForAdmin(page, size, days, status, userId, workspaceId, query));
    }

    @GetMapping("/errors/{errorId}")
    public ResponseEntity<AdminErrorEventDetailDto> getError(@PathVariable String errorId) {
        return ResponseEntity.ok(adminService.getErrorForAdmin(errorId));
    }

    @GetMapping("/users")
    public ResponseEntity<AdminUserPageDto> getAllUsers(
            @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(value = "size", defaultValue = "25") @Min(1) @Max(100) int size,
            @RequestParam(value = "search", required = false) @Size(max = 100) String search,
            @RequestParam(value = "status", defaultValue = "ALL") AdminUserStatusFilter status,
            @RequestParam(value = "includeServiceAccounts", defaultValue = "false") boolean includeServiceAccounts) {
        return ResponseEntity.ok(adminService.getUserPageForAdmin(page, size, search, includeServiceAccounts, status));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<AdminUserDto> getUser(@PathVariable String userId) {
        return ResponseEntity.ok(adminService.getUserForAdmin(userId));
    }

    @GetMapping("/users/{userId}/global-roles")
    public ResponseEntity<AdminGlobalRolesDto> getGlobalRoles(@PathVariable String userId) {
        return ResponseEntity.ok(adminService.getGlobalRolesForAdmin(userId));
    }

    @PostMapping("/users/{userId}/global-curator/grant")
    public ResponseEntity<AdminGlobalRolesDto> grantGlobalCurator(
            Authentication authentication,
            @PathVariable String userId,
            @Valid @RequestBody AdminGlobalCuratorRoleRequest request) {
        return ResponseEntity.ok(adminService.grantGlobalCuratorForAdmin(
                resolveActorUserId(authentication),
                resolveActorUsername(authentication),
                userId,
                request.reason()
        ));
    }

    @PostMapping("/users/{userId}/global-curator/revoke")
    public ResponseEntity<AdminGlobalRolesDto> revokeGlobalCurator(
            Authentication authentication,
            @PathVariable String userId,
            @Valid @RequestBody AdminGlobalCuratorRoleRequest request) {
        return ResponseEntity.ok(adminService.revokeGlobalCuratorForAdmin(
                resolveActorUserId(authentication),
                resolveActorUsername(authentication),
                userId,
                request.reason()
        ));
    }

    @PostMapping("/users")
    public ResponseEntity<AdminUserDto> createUser(
            Authentication authentication,
            @Valid @RequestBody AdminCreateUserRequest request) {
        AdminUserDto createdUser = adminService.createUserForAdmin(
                resolveActorUserId(authentication),
                resolveActorUsername(authentication),
                request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
    }

    @PostMapping("/users/{userId}/disable")
    public ResponseEntity<AdminUserDto> disableUser(
            Authentication authentication,
            @PathVariable String userId) {
        return ResponseEntity.ok(adminService.disableUserForAdmin(
                resolveActorUserId(authentication),
                resolveActorUsername(authentication),
                userId
        ));
    }

    @PostMapping("/users/{userId}/enable")
    public ResponseEntity<AdminUserDto> enableUser(
            Authentication authentication,
            @PathVariable String userId) {
        return ResponseEntity.ok(adminService.enableUserForAdmin(
                resolveActorUserId(authentication),
                resolveActorUsername(authentication),
                userId
        ));
    }

    @PostMapping("/users/{userId}/resend-setup")
    public ResponseEntity<AdminUserDto> resendSetupEmail(
            Authentication authentication,
            @PathVariable String userId) {
        return ResponseEntity.ok(adminService.resendSetupEmailForAdmin(
                resolveActorUserId(authentication),
                resolveActorUsername(authentication),
                userId
        ));
    }

    @GetMapping("/users/{userId}/audit-events")
    public ResponseEntity<List<AdminUserAuditEventDto>> getUserAuditEvents(
            @PathVariable String userId,
            @RequestParam(value = "limit", defaultValue = "50") @Min(1) @Max(200) int limit) {
        return ResponseEntity.ok(adminService.getUserAuditEventsForAdmin(userId, limit));
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

    private String resolveActorUsername(Authentication authentication) {
        if (authentication == null) {
            return "unknown";
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            String preferredUsername = jwt.getClaimAsString("preferred_username");
            if (preferredUsername != null && !preferredUsername.isBlank()) {
                return preferredUsername;
            }
        }

        return authentication.getName();
    }

    private String resolveActorUserId(Authentication authentication) {
        if (authentication == null) {
            return "unknown";
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            String subject = jwt.getSubject();
            if (subject != null && !subject.isBlank()) {
                return subject;
            }
        }

        return authentication.getName();
    }
}

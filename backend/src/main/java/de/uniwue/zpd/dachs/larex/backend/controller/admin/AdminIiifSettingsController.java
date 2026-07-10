package de.uniwue.zpd.dachs.larex.backend.controller.admin;

import de.uniwue.zpd.dachs.larex.backend.dto.IiifSettingsDto;
import de.uniwue.zpd.dachs.larex.backend.service.admin.IiifSettingsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/settings/iiif")
@PreAuthorize("hasRole('GLOBAL_ADMIN')")
public class AdminIiifSettingsController {

    private final IiifSettingsService settingsService;

    public AdminIiifSettingsController(IiifSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public ResponseEntity<IiifSettingsDto.Response> getSettings() {
        return ResponseEntity.ok(settingsService.getSettings());
    }

    @PutMapping
    public ResponseEntity<IiifSettingsDto.Response> updateSettings(
            Authentication authentication,
            @Valid @RequestBody IiifSettingsDto.UpdateRequest request) {
        return ResponseEntity.ok(settingsService.updateSettings(
                request.downloadMinIntervalMs(),
                resolveActorUserId(authentication)
        ));
    }

    private String resolveActorUserId(Authentication authentication) {
        if (authentication == null) {
            return "unknown";
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt && jwt.getSubject() != null && !jwt.getSubject().isBlank()) {
            return jwt.getSubject();
        }
        return authentication.getName();
    }
}

package de.uniwue.zpd.dachs.larex.backend.controller.admin;

import de.uniwue.zpd.dachs.larex.backend.dto.AvatarSettingsDto;
import de.uniwue.zpd.dachs.larex.backend.service.admin.AvatarSettingsService;
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
@RequestMapping("/admin/settings/avatar")
@PreAuthorize("hasRole('GLOBAL_ADMIN')")
public class AdminAvatarSettingsController {

    private final AvatarSettingsService avatarSettingsService;

    public AdminAvatarSettingsController(AvatarSettingsService avatarSettingsService) {
        this.avatarSettingsService = avatarSettingsService;
    }

    @GetMapping
    public ResponseEntity<AvatarSettingsDto.AdminResponse> getSettings() {
        return ResponseEntity.ok(avatarSettingsService.getAdminSettings());
    }

    @PutMapping
    public ResponseEntity<AvatarSettingsDto.AdminResponse> updateSettings(
            Authentication authentication,
            @Valid @RequestBody AvatarSettingsDto.UpdateRequest request) {
        return ResponseEntity.ok(avatarSettingsService.updateSettings(
                request.defaultStyle(),
                resolveActorUserId(authentication)
        ));
    }

    private String resolveActorUserId(Authentication authentication) {
        if (authentication == null) {
            return "unknown";
        }
        if (authentication.getPrincipal() instanceof Jwt jwt
                && jwt.getSubject() != null
                && !jwt.getSubject().isBlank()) {
            return jwt.getSubject();
        }
        return authentication.getName();
    }
}

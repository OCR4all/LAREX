package de.uniwue.zpd.dachs.larex.backend.controller;

import de.uniwue.zpd.dachs.larex.backend.dto.AvatarSettingsDto;
import de.uniwue.zpd.dachs.larex.backend.service.admin.AvatarSettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/avatar-settings")
public class AvatarSettingsController {

    private final AvatarSettingsService avatarSettingsService;

    public AvatarSettingsController(AvatarSettingsService avatarSettingsService) {
        this.avatarSettingsService = avatarSettingsService;
    }

    @GetMapping
    public ResponseEntity<AvatarSettingsDto.PublicResponse> getSettings() {
        return ResponseEntity.ok(avatarSettingsService.getPublicSettings());
    }
}

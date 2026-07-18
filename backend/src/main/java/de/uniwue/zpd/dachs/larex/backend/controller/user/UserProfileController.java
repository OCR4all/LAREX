package de.uniwue.zpd.dachs.larex.backend.controller.user;

import de.uniwue.zpd.dachs.larex.backend.dto.UserProfileDto;
import de.uniwue.zpd.dachs.larex.backend.service.user.ProfileImageService;
import de.uniwue.zpd.dachs.larex.backend.service.user.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/profile")
public class UserProfileController {

    private static final Logger logger = LoggerFactory.getLogger(UserProfileController.class);

    private final UserService userService;
    private final ProfileImageService profileImageService;

    public UserProfileController(UserService userService, ProfileImageService profileImageService) {
        this.userService = userService;
        this.profileImageService = profileImageService;
    }

    @GetMapping
    public ResponseEntity<UserProfileDto> getCurrentUserProfile(
            @AuthenticationPrincipal(expression = "subject") String userId) {
        Optional<UserProfileDto> profile = userService.getUserProfile(userId);
        return profile.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping
    public ResponseEntity<UserProfileDto> updateCurrentUserProfile(
            @Valid @RequestBody UserProfileDto.UpdateRequest updateRequest,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        if (!userService.updateUserProfile(userId, updateRequest)) {
            return ResponseEntity.badRequest().build();
        }
        return userService.getUserProfile(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.ok().build());
    }

    @PostMapping("/image")
    public ResponseEntity<Map<String, String>> uploadProfileImage(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        try {
            String imageUrl = profileImageService.uploadProfileImage(userId, file);
            return ResponseEntity.ok(Map.of("avatarUrl", imageUrl));
        } catch (IllegalArgumentException exception) {
            logger.warn("Validation error during image upload for user {}: {}", userId, exception.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        } catch (Exception exception) {
            logger.error("Unexpected error during image upload for user {}", userId, exception);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to upload image"));
        }
    }

    @DeleteMapping("/image")
    public ResponseEntity<Void> deleteProfileImage(
            @AuthenticationPrincipal(expression = "subject") String userId) {
        try {
            profileImageService.deleteProfileImageForUser(userId);
            return ResponseEntity.noContent().build();
        } catch (Exception exception) {
            logger.error("Failed to delete profile image for user {}", userId, exception);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/images/{storageKey}")
    public ResponseEntity<Resource> getProfileImage(@PathVariable String storageKey) {
        return profileImageService.loadProfileImage(storageKey)
                .map(resource -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable())
                        .body(resource))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}

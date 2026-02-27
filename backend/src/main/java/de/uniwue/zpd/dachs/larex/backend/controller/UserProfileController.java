package de.uniwue.zpd.dachs.larex.backend.controller;

import de.uniwue.zpd.dachs.larex.backend.dto.UserProfileDto;
import de.uniwue.zpd.dachs.larex.backend.service.ProfileImageService;
import de.uniwue.zpd.dachs.larex.backend.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Optional;

/**
 * REST controller for user profile management
 */
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

    /**
     * Get current user's profile
     */
    @GetMapping
    public ResponseEntity<UserProfileDto> getCurrentUserProfile(
            @AuthenticationPrincipal(expression = "subject") String userId) {

        Optional<UserProfileDto> profile = userService.getUserProfile(userId);

        return profile.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Update current user's profile (non-security fields only)
     */
    @PutMapping
    public ResponseEntity<UserProfileDto> updateCurrentUserProfile(
            @Valid @RequestBody UserProfileDto.UpdateRequest updateRequest,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        boolean updated = userService.updateUserProfile(userId, updateRequest);

        if (updated) {
            Optional<UserProfileDto> updatedProfile = userService.getUserProfile(userId);
            return updatedProfile.map(ResponseEntity::ok)
                    .orElse(ResponseEntity.ok().build());
        } else {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Upload profile image
     */
    @PostMapping("/image")
    public ResponseEntity<Map<String, String>> uploadProfileImage(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        try {
            // Get current profile to potentially delete old image
            Optional<UserProfileDto> currentProfile = userService.getUserProfile(userId);
            String oldAvatarUrl = currentProfile.map(UserProfileDto::avatar).orElse(null);

            // Upload new image
            String imageUrl = profileImageService.uploadProfileImage(userId, file);

            // Update user profile with new avatar URL
            UserProfileDto.UpdateRequest updateRequest = new UserProfileDto.UpdateRequest(null, null, imageUrl);
            boolean updated = userService.updateUserProfile(userId, updateRequest);

            if (updated) {
                // Delete old image if it was managed by us
                if (oldAvatarUrl != null) {
                    profileImageService.deleteProfileImage(oldAvatarUrl);
                }

                return ResponseEntity.ok(Map.of("avatarUrl", imageUrl));
            } else {
                // If profile update failed, clean up uploaded file
                profileImageService.deleteProfileImage(imageUrl);
                return ResponseEntity.badRequest().body(Map.of("error", "Failed to update profile"));
            }

        } catch (IllegalArgumentException e) {
            logger.warn("Validation error during image upload for user {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error during image upload for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to upload image: " + e.getMessage()));
        }
    }

    /**
     * Delete profile image
     */
    @DeleteMapping("/image")
    public ResponseEntity<Void> deleteProfileImage(
            @AuthenticationPrincipal(expression = "subject") String userId) {

        try {
            // Get current profile
            Optional<UserProfileDto> currentProfile = userService.getUserProfile(userId);
            if (currentProfile.isPresent() && currentProfile.get().avatar() != null) {
                String avatarUrl = currentProfile.get().avatar();

                // Remove avatar from profile
                UserProfileDto.UpdateRequest updateRequest = new UserProfileDto.UpdateRequest(null, null, "");
                boolean updated = userService.updateUserProfile(userId, updateRequest);

                if (updated) {
                    // Delete the file
                    profileImageService.deleteProfileImage(avatarUrl);
                    return ResponseEntity.ok().build();
                }
            }

            return ResponseEntity.badRequest().build();

        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}

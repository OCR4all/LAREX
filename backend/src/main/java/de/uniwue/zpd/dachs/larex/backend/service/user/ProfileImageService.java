package de.uniwue.zpd.dachs.larex.backend.service.user;

import de.uniwue.zpd.dachs.larex.backend.config.ProfileImageProperties;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
public class ProfileImageService {

    private static final Logger logger = LoggerFactory.getLogger(ProfileImageService.class);

    private static final List<String> ALLOWED_TYPES = List.of("image/jpeg", "image/png", "image/webp");
    private static final int MAX_SIZE_MB = 5;
    private static final int MAX_SIZE_BYTES = MAX_SIZE_MB * 1024 * 1024;
    private static final int TARGET_SIZE = 400; // 400x400 pixels

    private final ProfileImageProperties profileImageProperties;

    public ProfileImageService(ProfileImageProperties profileImageProperties) {
        this.profileImageProperties = profileImageProperties;
    }

    /**
     * Upload and process a profile image
     */
    public String uploadProfileImage(String userId, MultipartFile file) throws IOException {
        try {
            logger.debug("Starting image upload for user: {}", userId);
            logger.debug("File info - Name: {}, Size: {}, Type: {}", file.getOriginalFilename(), file.getSize(), file.getContentType());

            validateFile(file);

            // Create upload directory if it doesn't exist
            Path uploadDir = profileImageProperties.getPath().toAbsolutePath().normalize();
            logger.debug("Upload directory: {}", uploadDir.toAbsolutePath());
            Files.createDirectories(uploadDir);

            // Generate unique filename (sanitize to avoid issues with spaces/special chars)
            String originalFilename = file.getOriginalFilename();
            String extension = getFileExtension(originalFilename);
            String sanitizedUserId = userId.replaceAll("[^a-zA-Z0-9-]", "");
            String filename = sanitizedUserId + "_" + UUID.randomUUID().toString() + ".jpg"; // Always save as JPG
            Path filePath = uploadDir.resolve(filename);
            logger.debug("Target file path: {}", filePath.toAbsolutePath());

            // Process and resize image using Thumbnailator
            logger.debug("Processing image with Thumbnailator...");
            Thumbnails.of(file.getInputStream())
                    .size(TARGET_SIZE, TARGET_SIZE)
                    .crop(Positions.CENTER)
                    .outputFormat("jpg")
                    .outputQuality(0.9)
                    .toFile(filePath.toFile());

            // Return URL path
            String resultUrl = profileImageProperties.getBaseUrl() + "/" + filename;
            logger.debug("Upload successful, returning URL: {}", resultUrl);
            return resultUrl;

        } catch (Exception e) {
            logger.error("Error uploading profile image for user {}: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Delete existing profile image
     */
    public boolean deleteProfileImage(String imageUrl) {
        try {
            String baseUrl = profileImageProperties.getBaseUrl();
            if (imageUrl != null && imageUrl.startsWith(baseUrl)) {
                String filename = imageUrl.substring(baseUrl.length() + 1);
                Path filePath = profileImageProperties.getPath().toAbsolutePath().normalize().resolve(filename);
                return Files.deleteIfExists(filePath);
            }
        } catch (Exception e) {
            // Log error but don't fail the operation
            logger.warn("Failed to delete profile image: {}", e.getMessage());
        }
        return false;
    }

    private void validateFile(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("File size exceeds " + MAX_SIZE_MB + "MB limit");
        }

        String contentType = file.getContentType();
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("File type not supported. Allowed types: JPEG, PNG, WebP");
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "jpg";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
}

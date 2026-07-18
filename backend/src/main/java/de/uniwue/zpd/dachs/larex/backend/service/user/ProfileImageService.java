package de.uniwue.zpd.dachs.larex.backend.service.user;

import de.uniwue.zpd.dachs.larex.backend.config.ProfileImageProperties;
import de.uniwue.zpd.dachs.larex.backend.entity.UserAvatar;
import de.uniwue.zpd.dachs.larex.backend.repository.user.UserAvatarRepository;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProfileImageService {

    private static final Logger logger = LoggerFactory.getLogger(ProfileImageService.class);
    private static final List<String> ALLOWED_TYPES = List.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;
    private static final int TARGET_SIZE = 400;

    private final ProfileImageProperties properties;
    private final UserAvatarRepository repository;

    public ProfileImageService(ProfileImageProperties properties, UserAvatarRepository repository) {
        this.properties = properties;
        this.repository = repository;
    }

    public String uploadProfileImage(String userId, MultipartFile file) throws IOException {
        validateFile(file);

        Files.createDirectories(uploadDirectory());
        String storageKey = UUID.randomUUID() + ".jpg";
        Path newFile = resolveStorageKey(storageKey);

        try {
            Thumbnails.of(file.getInputStream())
                    .size(TARGET_SIZE, TARGET_SIZE)
                    .crop(Positions.CENTER)
                    .outputFormat("jpg")
                    .outputQuality(0.9)
                    .toFile(newFile.toFile());
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(newFile);
            throw new IllegalArgumentException("The uploaded file is not a valid image", exception);
        }

        Optional<UserAvatar> existingAvatar = repository.findById(userId);
        String previousStorageKey = existingAvatar.map(UserAvatar::getStorageKey).orElse(null);
        LocalDateTime now = LocalDateTime.now();
        UserAvatar avatar = existingAvatar
                .map(existing -> {
                    existing.setStorageKey(storageKey);
                    existing.setUpdatedAt(now);
                    return existing;
                })
                .orElseGet(() -> new UserAvatar(userId, storageKey, now, now));

        try {
            repository.saveAndFlush(avatar);
        } catch (RuntimeException exception) {
            Files.deleteIfExists(newFile);
            throw exception;
        }

        if (previousStorageKey != null && !previousStorageKey.equals(storageKey)) {
            deleteStorageKeyQuietly(previousStorageKey);
        }
        return toAvatarUrl(storageKey);
    }

    public boolean deleteProfileImageForUser(String userId) {
        Optional<UserAvatar> avatar = repository.findById(userId);
        if (avatar.isEmpty()) {
            return false;
        }

        String storageKey = avatar.get().getStorageKey();
        repository.delete(avatar.get());
        repository.flush();
        deleteStorageKeyQuietly(storageKey);
        return true;
    }

    public Optional<Resource> loadProfileImage(String storageKey) {
        if (!isValidStorageKey(storageKey) || repository.findByStorageKey(storageKey).isEmpty()) {
            return Optional.empty();
        }

        Path path = resolveStorageKey(storageKey);
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            return Optional.empty();
        }
        return Optional.of(new FileSystemResource(path));
    }

    public Optional<String> getAvatarUrl(String userId) {
        return repository.findById(userId)
                .map(UserAvatar::getStorageKey)
                .map(this::toAvatarUrl);
    }

    public Map<String, String> getAvatarUrls(Collection<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return repository.findAllById(userIds).stream()
                .collect(Collectors.toMap(
                        UserAvatar::getUserId,
                        avatar -> toAvatarUrl(avatar.getStorageKey()),
                        (first, second) -> first
                ));
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("File size exceeds 5MB limit");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("File type not supported. Allowed types: JPEG, PNG, WebP");
        }
    }

    private Path uploadDirectory() {
        return properties.getPath().toAbsolutePath().normalize();
    }

    private Path resolveStorageKey(String storageKey) {
        if (!isValidStorageKey(storageKey)) {
            throw new IllegalArgumentException("Invalid profile image key");
        }
        Path directory = uploadDirectory();
        Path resolved = directory.resolve(storageKey).normalize();
        if (!resolved.startsWith(directory)) {
            throw new IllegalArgumentException("Invalid profile image key");
        }
        return resolved;
    }

    private boolean isValidStorageKey(String storageKey) {
        if (storageKey == null || !storageKey.endsWith(".jpg")) {
            return false;
        }
        try {
            UUID.fromString(storageKey.substring(0, storageKey.length() - 4));
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String toAvatarUrl(String storageKey) {
        return properties.getBaseUrl() + "/" + storageKey;
    }

    private void deleteStorageKeyQuietly(String storageKey) {
        try {
            Files.deleteIfExists(resolveStorageKey(storageKey));
        } catch (Exception exception) {
            logger.warn("Failed to delete profile image {}: {}", storageKey, exception.getMessage());
        }
    }
}

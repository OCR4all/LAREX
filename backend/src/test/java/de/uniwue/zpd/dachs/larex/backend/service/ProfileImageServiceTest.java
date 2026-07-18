package de.uniwue.zpd.dachs.larex.backend.service;

import de.uniwue.zpd.dachs.larex.backend.config.ProfileImageProperties;
import de.uniwue.zpd.dachs.larex.backend.entity.UserAvatar;
import de.uniwue.zpd.dachs.larex.backend.repository.user.UserAvatarRepository;
import de.uniwue.zpd.dachs.larex.backend.service.user.ProfileImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileImageServiceTest {

    @TempDir
    Path uploadDirectory;

    private UserAvatarRepository repository;
    private ProfileImageService service;

    @BeforeEach
    void setUp() {
        ProfileImageProperties properties = new ProfileImageProperties();
        properties.setPath(uploadDirectory);
        properties.setBaseUrl("/api/profile/images");
        repository = mock(UserAvatarRepository.class);
        service = new ProfileImageService(properties, repository);
    }

    @Test
    void uploadsAProcessedJpegAndPersistsAnOpaqueStorageKey() throws Exception {
        when(repository.findById("user-1")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(UserAvatar.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String avatarUrl = service.uploadProfileImage("user-1", imageFile());

        assertThat(avatarUrl).matches("/api/profile/images/[0-9a-f-]{36}\\.jpg");
        String storageKey = avatarUrl.substring(avatarUrl.lastIndexOf('/') + 1);
        assertThat(Files.readAllBytes(uploadDirectory.resolve(storageKey))).isNotEmpty();

        var output = ImageIO.read(uploadDirectory.resolve(storageKey).toFile());
        assertThat(output.getWidth()).isEqualTo(400);
        assertThat(output.getHeight()).isEqualTo(400);
        verify(repository).saveAndFlush(any(UserAvatar.class));
    }

    @Test
    void rejectsUnsupportedAndCorruptFiles() {
        var unsupported = new MockMultipartFile("file", "avatar.gif", "image/gif", new byte[]{1});
        assertThatThrownBy(() -> service.uploadProfileImage("user-1", unsupported))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File type not supported");

        var corrupt = new MockMultipartFile("file", "avatar.png", "image/png", new byte[]{1, 2, 3});
        assertThatThrownBy(() -> service.uploadProfileImage("user-1", corrupt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid image");
    }

    @Test
    void replacementDeletesThePreviousManagedFile() throws Exception {
        String oldStorageKey = UUID.randomUUID() + ".jpg";
        Files.write(uploadDirectory.resolve(oldStorageKey), new byte[]{1});
        UserAvatar existing = new UserAvatar(
                "user-1",
                oldStorageKey,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().minusDays(1)
        );
        when(repository.findById("user-1")).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(UserAvatar.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String avatarUrl = service.uploadProfileImage("user-1", imageFile());

        assertThat(Files.exists(uploadDirectory.resolve(oldStorageKey))).isFalse();
        assertThat(Files.exists(uploadDirectory.resolve(avatarUrl.substring(avatarUrl.lastIndexOf('/') + 1)))).isTrue();
    }

    @Test
    void imageLoadingRequiresAValidReferencedStorageKey() throws Exception {
        String storageKey = UUID.randomUUID() + ".jpg";
        Files.write(uploadDirectory.resolve(storageKey), new byte[]{1, 2, 3});
        UserAvatar avatar = new UserAvatar("user-1", storageKey, LocalDateTime.now(), LocalDateTime.now());
        when(repository.findByStorageKey(storageKey)).thenReturn(Optional.of(avatar));

        assertThat(service.loadProfileImage(storageKey)).isPresent();
        assertThat(service.loadProfileImage("../secret.jpg")).isEmpty();
    }

    private MockMultipartFile imageFile() throws Exception {
        BufferedImage image = new BufferedImage(640, 320, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return new MockMultipartFile("file", "avatar.png", "image/png", output.toByteArray());
    }
}

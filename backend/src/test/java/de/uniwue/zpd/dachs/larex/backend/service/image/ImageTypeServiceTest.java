package de.uniwue.zpd.dachs.larex.backend.service.image;

import de.uniwue.zpd.dachs.larex.backend.entity.*;
import de.uniwue.zpd.dachs.larex.backend.config.UploadDirectoryProperties;
import de.uniwue.zpd.dachs.larex.backend.service.upload.UploadPathService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ImageTypeServiceTest {
    @TempDir Path root;

    @Test
    void resolvesUninspectedBytesOnceAndDoesNotChangeContentTimestamp() throws Exception {
        var properties = new UploadDirectoryProperties(); properties.setRootDirectory(root);
        var jdbc = mock(JdbcTemplate.class);
        var service = new ImageTypeService(new UploadPathService(properties), jdbc);
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_BYTE_BINARY), "png", root.resolve("image.png").toFile());
        var image = new PageImage(); image.setId("image"); image.setFilePath("image.png");
        var originalTimestamp = LocalDateTime.of(2020, 1, 1, 0, 0);
        org.springframework.test.util.ReflectionTestUtils.setField(image, "updated", originalTimestamp);
        assertThat(service.typeOf(image)).isEqualTo(ImageType.BINARY);
        assertThat(service.typeOf(image)).isEqualTo(ImageType.BINARY);
        assertThat(image.getUpdated()).isEqualTo(originalTimestamp);
        verify(jdbc, times(1)).update("UPDATE page_images SET image_type = ? WHERE id = ? AND file_path = ? AND image_type IS NULL",
                "BINARY", "image", "image.png");
    }

    @Test
    void readOnlyDetectionLeavesPersistenceToBackfill() throws Exception {
        var properties = new UploadDirectoryProperties(); properties.setRootDirectory(root);
        var jdbc = mock(JdbcTemplate.class);
        var service = new ImageTypeService(new UploadPathService(properties), jdbc);
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_BYTE_BINARY), "png", root.resolve("image.png").toFile());
        var image = new PageImage(); image.setId("image"); image.setFilePath("image.png");
        org.springframework.transaction.support.TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        try {
            assertThat(service.typeOf(image)).isEqualTo(ImageType.BINARY);
            verifyNoInteractions(jdbc);
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        }
    }

    @Test
    void missingHistoricalSnapshotsBecomeUnknownAndXmlHasNoImageType() {
        var properties = new UploadDirectoryProperties(); properties.setRootDirectory(root);
        var jdbc = mock(JdbcTemplate.class);
        var service = new ImageTypeService(new UploadPathService(properties), jdbc);
        var image = new ActionInputSnapshot(); image.setFileType("images"); image.setSnapshotPath("deleted.png"); image.setSourceFileId("source");
        assertThat(service.typeOf(image)).isEqualTo(ImageType.UNKNOWN);
        var xml = new ActionInputSnapshot(); xml.setFileType("xml");
        assertThat(service.typeOf(xml)).isNull();
        var copyXml = new DatasetItemCopyFile(); copyXml.setKind(DatasetItemCopyFile.Kind.XML);
        assertThat(service.typeOf(copyXml)).isNull();
    }
}

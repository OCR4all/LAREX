package de.uniwue.zpd.dachs.larex.backend.service.storage;

import de.uniwue.zpd.dachs.larex.backend.service.upload.UploadPathService;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class ThumbnailService {

    private static final Logger logger = LoggerFactory.getLogger(ThumbnailService.class);
    private static final int THUMBNAIL_WIDTH = 200;
    private static final int THUMBNAIL_HEIGHT = 283; // A4 aspect ratio
    private static final double THUMBNAIL_QUALITY = 0.85;

    private final UploadPathService uploadPathService;

    public ThumbnailService(UploadPathService uploadPathService) {
        this.uploadPathService = uploadPathService;
    }

    public String generateThumbnail(String imagePath) {
        try {
            Path sourceFile = uploadPathService.resolve(imagePath);
            if (!Files.exists(sourceFile)) {
                logger.warn("Source image not found: {}", sourceFile);
                return null;
            }

            // Create thumbnails directory
            Path thumbnailDir = uploadPathService.resolve("thumbnails");
            Files.createDirectories(thumbnailDir);

            // Generate thumbnail filename
            String originalFileName = sourceFile.getFileName().toString();
            String thumbnailFileName = "thumb_" + originalFileName;
            Path thumbnailFile = thumbnailDir.resolve(thumbnailFileName);

            // Generate thumbnail
            Thumbnails.of(sourceFile.toFile())
                    .size(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)
                    .outputQuality(THUMBNAIL_QUALITY)
                    .toFile(thumbnailFile.toFile());

            return "thumbnails/" + thumbnailFileName;

        } catch (IOException | IllegalArgumentException e) {
            logger.error("Failed to generate thumbnail for {}: {}", imagePath, e.getMessage());
            return null;
        }
    }
}

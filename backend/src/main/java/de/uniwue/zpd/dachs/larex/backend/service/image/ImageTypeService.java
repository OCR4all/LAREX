package de.uniwue.zpd.dachs.larex.backend.service.image;

import de.uniwue.zpd.dachs.larex.backend.entity.*;
import de.uniwue.zpd.dachs.larex.backend.service.upload.UploadPathService;
import de.uniwue.zpd.dachs.larex.backend.util.ImageTypeDetector;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Metadata updates intentionally bypass auditing: inspecting bytes does not change image content. */
@Service
public class ImageTypeService {
    private final UploadPathService paths;
    private final JdbcTemplate jdbc;

    public ImageTypeService(UploadPathService paths, JdbcTemplate jdbc) {
        this.paths = paths;
        this.jdbc = jdbc;
    }

    public ImageType typeOf(PageImage image) {
        if (image.getImageType() == null) image.setImageType(resolve("page_images", "file_path", image.getId(), image.getFilePath()));
        return image.getImageType();
    }

    public ImageType typeOf(DatasetItemCopyFile image) {
        if (image.getKind() != DatasetItemCopyFile.Kind.IMAGE) return null;
        if (image.getImageType() == null) image.setImageType(resolve("dataset_item_copy_files", "file_path", image.getId(), image.getFilePath()));
        return image.getImageType();
    }

    public ImageType typeOf(ActionTrainingInput image) {
        if (image.getImageType() == null) image.setImageType(resolve("action_training_inputs", "image_snapshot_path", image.getId(), image.getImageSnapshotPath()));
        return image.getImageType();
    }

    public ImageType typeOf(ActionInputSnapshot image) {
        if (!"images".equals(image.getFileType())) return null;
        if (image.getImageType() == null) image.setImageType(resolve("action_input_snapshots", "snapshot_path", image.getId(), image.getSnapshotPath()));
        return image.getImageType();
    }

    ImageType resolve(String table, String pathColumn, String id, String path) {
        ImageType type;
        try {
            type = path == null ? ImageType.UNKNOWN : ImageTypeDetector.detect(paths.resolve(path));
        } catch (IllegalArgumentException error) {
            type = ImageType.UNKNOWN;
        }
        // Reuse the caller's connection. Read-only callers still get the detected type;
        // the existing backfill worker persists it later without a nested transaction.
        if (!TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            // Table/column names come exclusively from the fixed internal call sites.
            jdbc.update("UPDATE " + table + " SET image_type = ? WHERE id = ? AND "
                    + pathColumn + " = ? AND image_type IS NULL", type.name(), id, path);
        }
        return type;
    }

    public int backfillBatch() {
        int count = backfill("page_images", "file_path", "");
        count += backfill("dataset_item_copy_files", "file_path", " AND kind = 'IMAGE'");
        count += backfill("action_training_inputs", "image_snapshot_path", "");
        return count + backfill("action_input_snapshots", "snapshot_path", " AND file_type = 'images'");
    }

    private int backfill(String table, String pathColumn, String condition) {
        var rows = jdbc.query("SELECT id, " + pathColumn + " FROM " + table
                        + " WHERE image_type IS NULL" + condition + " ORDER BY id LIMIT 25",
                (row, index) -> new String[]{row.getString(1), row.getString(2)});
        rows.forEach(row -> resolve(table, pathColumn, row[0], row[1]));
        return rows.size();
    }
}

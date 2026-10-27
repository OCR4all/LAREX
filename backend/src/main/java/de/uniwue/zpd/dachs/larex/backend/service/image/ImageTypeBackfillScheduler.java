package de.uniwue.zpd.dachs.larex.backend.service.image;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "larex.images.backfill-enabled", havingValue = "true", matchIfMissing = true)
public class ImageTypeBackfillScheduler {
    private final ImageTypeService images;

    public ImageTypeBackfillScheduler(ImageTypeService images) { this.images = images; }

    @Scheduled(initialDelayString = "${larex.images.backfill-delay-ms:10000}",
               fixedDelayString = "${larex.images.backfill-delay-ms:10000}")
    public void backfill() { images.backfillBatch(); }
}

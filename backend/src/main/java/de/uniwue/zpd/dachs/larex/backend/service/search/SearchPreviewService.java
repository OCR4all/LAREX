package de.uniwue.zpd.dachs.larex.backend.service.search;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.geometry.PolygonDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.region.RegionDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.TextLineDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.application.AnnotationProcessingService;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageService;
import de.uniwue.zpd.dachs.larex.backend.util.CoordinateUtils;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;

@Service
public class SearchPreviewService {

    private static final Logger log = LoggerFactory.getLogger(SearchPreviewService.class);

    private final PageService pageService;
    private final AnnotationProcessingService annotationProcessingService;
    private final Cache<String, PreviewImage> previewCache = Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();

    @Value("${file.upload-dir}")
    private String uploadDir;

    public SearchPreviewService(PageService pageService, AnnotationProcessingService annotationProcessingService) {
        this.pageService = pageService;
        this.annotationProcessingService = annotationProcessingService;
    }

    public PreviewImage getTextPreview(String projectId,
                                       String pageId,
                                       String textLineId,
                                       String regionId,
                                       String userId) {
        if ((textLineId == null || textLineId.isBlank()) && (regionId == null || regionId.isBlank())) {
            return null;
        }

        Page page = pageService.getPageById(pageId, userId).orElse(null);
        if (page == null || page.getProject() == null || !page.getProject().getId().equals(projectId)) {
            return null;
        }

        String cacheKey = projectId + "|" + pageId + "|" + safe(textLineId) + "|" + safe(regionId);
        return previewCache.get(cacheKey, ignored -> renderPreview(pageId, textLineId, regionId, userId));
    }

    private PreviewImage renderPreview(String pageId, String textLineId, String regionId, String userId) {
        try {
            List<PageImage> images = pageService.getPageImages(pageId, userId);
            PageImage image = images.stream()
                    .min(Comparator.comparing(PageImage::getVariant, Comparator.nullsLast(String::compareToIgnoreCase)))
                    .orElse(null);
            if (image == null) {
                return null;
            }

            Path imagePath = Paths.get(uploadDir).resolve(image.getFilePath());
            if (!Files.exists(imagePath)) {
                return null;
            }

            BufferedImage source = ImageIO.read(imagePath.toFile());
            if (source == null) {
                return null;
            }

            PageDto pageDto = annotationProcessingService.parseMultipleXmlToAnnotation(pageId);
            PolygonDto polygon = textLineId != null && !textLineId.isBlank()
                    ? findTextLinePolygon(pageDto.regions(), textLineId)
                    : findRegionPolygon(pageDto.regions(), regionId);
            if (polygon == null || polygon.points() == null || polygon.points().isEmpty()) {
                return null;
            }

            PolygonDto.BoundingBoxDto box = polygon.getBoundingBox();
            int minX = CoordinateUtils.worldToPixelX(box.x(), source.getWidth());
            int minY = CoordinateUtils.worldToPixelY(box.y() + box.height(), source.getHeight());
            int maxX = CoordinateUtils.worldToPixelX(box.x() + box.width(), source.getWidth());
            int maxY = CoordinateUtils.worldToPixelY(box.y(), source.getHeight());

            int paddingX = Math.max(24, (int) Math.round((maxX - minX) * 0.15));
            int paddingY = Math.max(18, (int) Math.round((maxY - minY) * 0.25));

            int cropX = Math.max(0, minX - paddingX);
            int cropY = Math.max(0, minY - paddingY);
            int cropWidth = Math.min(source.getWidth() - cropX, Math.max(1, (maxX - minX) + (paddingX * 2)));
            int cropHeight = Math.min(source.getHeight() - cropY, Math.max(1, (maxY - minY) + (paddingY * 2)));
            if (cropWidth <= 0 || cropHeight <= 0) {
                return null;
            }

            BufferedImage preview = renderAnnotatedPreview(source, polygon, cropX, cropY, cropWidth, cropHeight);
            BufferedImage scaled = Thumbnails.of(preview)
                    .size(640, 220)
                    .keepAspectRatio(true)
                    .asBufferedImage();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(scaled, "png", out);
            return new PreviewImage(out.toByteArray(), MediaType.IMAGE_PNG_VALUE);
        } catch (Exception e) {
            log.debug("Failed to render search preview for page {}: {}", pageId, e.getMessage());
            return null;
        }
    }

    private BufferedImage renderAnnotatedPreview(BufferedImage source,
                                                 PolygonDto polygon,
                                                 int cropX,
                                                 int cropY,
                                                 int cropWidth,
                                                 int cropHeight) {
        BufferedImage preview = new BufferedImage(cropWidth, cropHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = preview.createGraphics();

        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(
                    source,
                    0,
                    0,
                    cropWidth,
                    cropHeight,
                    cropX,
                    cropY,
                    cropX + cropWidth,
                    cropY + cropHeight,
                    null
            );

            Polygon overlayPolygon = toPixelPolygon(polygon, source.getWidth(), source.getHeight(), cropX, cropY);
            if (overlayPolygon == null || overlayPolygon.npoints < 2) {
                return preview;
            }

            if (overlayPolygon.npoints >= 3) {
                Area shadedArea = new Area(new Rectangle2D.Double(0, 0, cropWidth, cropHeight));
                shadedArea.subtract(new Area(overlayPolygon));
                graphics.setComposite(AlphaComposite.SrcOver.derive(0.18f));
                graphics.setColor(Color.BLACK);
                graphics.fill(shadedArea);

                graphics.setComposite(AlphaComposite.SrcOver.derive(0.14f));
                graphics.setColor(new Color(0x2F, 0x80, 0xED));
                graphics.fillPolygon(overlayPolygon);
            }

            graphics.setComposite(AlphaComposite.SrcOver);
            graphics.setColor(new Color(0x1D, 0x72, 0xD8));
            graphics.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.drawPolygon(overlayPolygon);
        } finally {
            graphics.dispose();
        }

        return preview;
    }

    private Polygon toPixelPolygon(PolygonDto polygon,
                                   int imageWidth,
                                   int imageHeight,
                                   int cropX,
                                   int cropY) {
        if (polygon == null || polygon.points() == null || polygon.points().isEmpty()) {
            return null;
        }

        Polygon result = new Polygon();
        for (var point : polygon.points()) {
            int x = CoordinateUtils.worldToPixelX(point.x(), imageWidth) - cropX;
            int y = CoordinateUtils.worldToPixelY(point.y(), imageHeight) - cropY;
            result.addPoint(x, y);
        }
        return result;
    }

    private PolygonDto findTextLinePolygon(List<RegionDto> regions, String textLineId) {
        if (regions == null || textLineId == null || textLineId.isBlank()) {
            return null;
        }
        for (RegionDto region : regions) {
            if (region == null) {
                continue;
            }
            if (region.textLines() != null) {
                for (TextLineDto textLine : region.textLines()) {
                    if (textLine != null && textLineId.equals(textLine.id())) {
                        return textLine.coords();
                    }
                }
            }
            PolygonDto nested = findTextLinePolygon(region.nestedRegions(), textLineId);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private PolygonDto findRegionPolygon(List<RegionDto> regions, String regionId) {
        if (regions == null || regionId == null || regionId.isBlank()) {
            return null;
        }
        for (RegionDto region : regions) {
            if (region == null) {
                continue;
            }
            if (regionId.equals(region.id())) {
                return region.coords();
            }
            PolygonDto nested = findRegionPolygon(region.nestedRegions(), regionId);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private String safe(String value) {
        return value == null ? "-" : value.toLowerCase(Locale.ROOT);
    }

    public record PreviewImage(byte[] bytes, String mediaType) {
    }
}

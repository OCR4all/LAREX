package de.uniwue.zpd.dachs.larex.backend.dto.page;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO for a polygon using world coordinates for frontend rendering.
 * 
 * <p>Contains a list of points where each point serializes as [x, y] array.
 * Used for region coordinates, baselines, etc.
 * 
 * <p>Points are in world coordinate space [-1, 1] for direct WebGL consumption.
 * Conversion between pixel and world coordinates is done in the mappers.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PolygonDto(
    List<PointDto> points,
    Double confidence
) {
    public PolygonDto {
        if (points == null) {
            points = new ArrayList<>();
        }
    }

    public PolygonDto(List<PointDto> points) {
        this(points, null);
    }

    /**
     * Get bounding box of this polygon in world coordinates.
     * 
     * @return bounding box with world coordinate values
     */
    @JsonIgnore
    public BoundingBoxDto getBoundingBox() {
        if (points.isEmpty()) {
            return new BoundingBoxDto(0.0, 0.0, 0.0, 0.0);
        }

        double minX = points.get(0).x();
        double maxX = minX;
        double minY = points.get(0).y();
        double maxY = minY;

        for (PointDto p : points) {
            minX = Math.min(minX, p.x());
            maxX = Math.max(maxX, p.x());
            minY = Math.min(minY, p.y());
            maxY = Math.max(maxY, p.y());
        }

        return new BoundingBoxDto(minX, minY, maxX - minX, maxY - minY);
    }

    /**
     * DTO for bounding box using world coordinates.
     */
    public record BoundingBoxDto(double x, double y, double width, double height) {}
}

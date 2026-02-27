package de.uniwue.zpd.dachs.larex.backend.dto.page;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * DTO for a 2D point using world coordinates for frontend rendering.
 * 
 * <p>Serializes as a JSON array [x, y] for efficient frontend consumption.
 * Uses double coordinates in world space [-1, 1] for WebGL rendering.
 * 
 * <p>Note: This DTO represents world coordinates, not pixel coordinates.
 * Conversion between pixel and world coordinates is done in the mappers
 * using {@link de.uniwue.zpd.dachs.larex.backend.util.CoordinateUtils}.
 */
@JsonFormat(shape = JsonFormat.Shape.ARRAY)
public record PointDto(
    double x,
    double y
) {
    /**
     * Get the coordinates as a JSON array [x, y].
     */
    @JsonValue
    public double[] toArray() {
        return new double[] { x, y };
    }

    /**
     * Create a PointDto from a JSON array [x, y].
     */
    public static PointDto fromArray(double[] coords) {
        if (coords == null || coords.length < 2) {
            throw new IllegalArgumentException("Coordinates array must have at least 2 elements");
        }
        return new PointDto(coords[0], coords[1]);
    }

    /**
     * Create from PAGE XML coordinate string "x,y" (pixel coordinates).
     * Note: This parses pixel coordinates but stores them as-is without conversion.
     * Use the mappers for proper pixel-to-world conversion.
     * 
     * @deprecated Use mappers with CoordinateUtils for proper coordinate conversion.
     */
    @Deprecated
    public static PointDto fromPageXmlString(String coords) {
        String[] parts = coords.split(",");
        return new PointDto(
            Double.parseDouble(parts[0].trim()),
            Double.parseDouble(parts[1].trim())
        );
    }

    /**
     * Convert to PAGE XML coordinate string "x,y" (rounded to integers).
     * Note: This rounds world coordinates to integers, which loses precision.
     * Use the mappers for proper world-to-pixel conversion.
     * 
     * @deprecated Use mappers with CoordinateUtils for proper coordinate conversion.
     */
    @Deprecated
    public String toPageXmlString() {
        return Math.round(x) + "," + Math.round(y);
    }
}

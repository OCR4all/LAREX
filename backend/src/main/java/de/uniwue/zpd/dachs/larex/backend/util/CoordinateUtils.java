package de.uniwue.zpd.dachs.larex.backend.util;

/**
 * Converts coordinates between PAGE pixel space (origin top-left) and normalized world space
 * used by the editor renderer (origin center, y-axis up).
 */
public final class CoordinateUtils {

    private CoordinateUtils() {
    }

    public static double pixelToWorldX(int pixelX, int imageWidth) {
        if (imageWidth <= 0) {
            throw new IllegalArgumentException("Image width must be positive");
        }
        return (pixelX / (double) imageWidth) * 2 - 1;
    }

    public static double pixelToWorldY(int pixelY, int imageHeight) {
        if (imageHeight <= 0) {
            throw new IllegalArgumentException("Image height must be positive");
        }
        return 1 - (pixelY / (double) imageHeight) * 2;
    }

    public static int worldToPixelX(double worldX, int imageWidth) {
        if (imageWidth <= 0) {
            throw new IllegalArgumentException("Image width must be positive");
        }
        return (int) Math.round((worldX + 1) / 2 * imageWidth);
    }

    public static int worldToPixelY(double worldY, int imageHeight) {
        if (imageHeight <= 0) {
            throw new IllegalArgumentException("Image height must be positive");
        }
        return (int) Math.round((1 - worldY) / 2 * imageHeight);
    }

    public static double[] pixelToWorld(int pixelX, int pixelY, int imageWidth, int imageHeight) {
        return new double[] {
            pixelToWorldX(pixelX, imageWidth),
            pixelToWorldY(pixelY, imageHeight)
        };
    }

    public static int[] worldToPixel(double worldX, double worldY, int imageWidth, int imageHeight) {
        return new int[] {
            worldToPixelX(worldX, imageWidth),
            worldToPixelY(worldY, imageHeight)
        };
    }
}

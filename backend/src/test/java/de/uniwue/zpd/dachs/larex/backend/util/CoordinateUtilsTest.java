package de.uniwue.zpd.dachs.larex.backend.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CoordinateUtils coordinate conversion between pixel and world coordinates.
 */
class CoordinateUtilsTest {

    private static final double EPSILON = 0.0001; // Tolerance for floating point comparisons

    @Nested
    @DisplayName("Pixel to World Conversion")
    class PixelToWorldTests {

        @Test
        @DisplayName("Top-left corner (0,0) should map to (-1, 1)")
        void topLeftCorner() {
            assertEquals(-1.0, CoordinateUtils.pixelToWorldX(0, 1000), EPSILON);
            assertEquals(1.0, CoordinateUtils.pixelToWorldY(0, 1000), EPSILON);
        }

        @Test
        @DisplayName("Bottom-right corner (width, height) should map to (1, -1)")
        void bottomRightCorner() {
            assertEquals(1.0, CoordinateUtils.pixelToWorldX(1000, 1000), EPSILON);
            assertEquals(-1.0, CoordinateUtils.pixelToWorldY(1000, 1000), EPSILON);
        }

        @Test
        @DisplayName("Center (width/2, height/2) should map to (0, 0)")
        void center() {
            assertEquals(0.0, CoordinateUtils.pixelToWorldX(500, 1000), EPSILON);
            assertEquals(0.0, CoordinateUtils.pixelToWorldY(500, 1000), EPSILON);
        }

        @ParameterizedTest
        @DisplayName("Various pixel coordinates should convert correctly")
        @CsvSource({
            "250, 1000, -0.5",   // 25% from left
            "750, 1000, 0.5",    // 75% from left
            "100, 1000, -0.8",   // 10% from left
            "900, 1000, 0.8"     // 90% from left
        })
        void variousXCoordinates(int pixelX, int imageWidth, double expectedWorldX) {
            assertEquals(expectedWorldX, CoordinateUtils.pixelToWorldX(pixelX, imageWidth), EPSILON);
        }

        @ParameterizedTest
        @DisplayName("Y coordinates should be flipped correctly")
        @CsvSource({
            "250, 1000, 0.5",    // 25% from top -> 0.5 in world (upper half)
            "750, 1000, -0.5",   // 75% from top -> -0.5 in world (lower half)
            "100, 1000, 0.8",    // 10% from top
            "900, 1000, -0.8"    // 90% from top
        })
        void yCoordinatesFlipped(int pixelY, int imageHeight, double expectedWorldY) {
            assertEquals(expectedWorldY, CoordinateUtils.pixelToWorldY(pixelY, imageHeight), EPSILON);
        }

        @Test
        @DisplayName("Should throw exception for zero image width")
        void zeroWidthThrows() {
            assertThrows(IllegalArgumentException.class, 
                () -> CoordinateUtils.pixelToWorldX(100, 0));
        }

        @Test
        @DisplayName("Should throw exception for negative image height")
        void negativeHeightThrows() {
            assertThrows(IllegalArgumentException.class, 
                () -> CoordinateUtils.pixelToWorldY(100, -1));
        }
    }

    @Nested
    @DisplayName("World to Pixel Conversion")
    class WorldToPixelTests {

        @Test
        @DisplayName("(-1, 1) should map to top-left corner (0, 0)")
        void topLeftCorner() {
            assertEquals(0, CoordinateUtils.worldToPixelX(-1.0, 1000));
            assertEquals(0, CoordinateUtils.worldToPixelY(1.0, 1000));
        }

        @Test
        @DisplayName("(1, -1) should map to bottom-right corner (width, height)")
        void bottomRightCorner() {
            assertEquals(1000, CoordinateUtils.worldToPixelX(1.0, 1000));
            assertEquals(1000, CoordinateUtils.worldToPixelY(-1.0, 1000));
        }

        @Test
        @DisplayName("(0, 0) should map to center (width/2, height/2)")
        void center() {
            assertEquals(500, CoordinateUtils.worldToPixelX(0.0, 1000));
            assertEquals(500, CoordinateUtils.worldToPixelY(0.0, 1000));
        }

        @ParameterizedTest
        @DisplayName("Various world X coordinates should convert correctly")
        @CsvSource({
            "-0.5, 1000, 250",   // 25% from left
            "0.5, 1000, 750",    // 75% from left
            "-0.8, 1000, 100",   // 10% from left
            "0.8, 1000, 900"     // 90% from left
        })
        void variousXCoordinates(double worldX, int imageWidth, int expectedPixelX) {
            assertEquals(expectedPixelX, CoordinateUtils.worldToPixelX(worldX, imageWidth));
        }

        @ParameterizedTest
        @DisplayName("Y coordinates should be flipped correctly")
        @CsvSource({
            "0.5, 1000, 250",    // upper half of world -> 25% from top
            "-0.5, 1000, 750",   // lower half of world -> 75% from top
            "0.8, 1000, 100",    // near top of world
            "-0.8, 1000, 900"    // near bottom of world
        })
        void yCoordinatesFlipped(double worldY, int imageHeight, int expectedPixelY) {
            assertEquals(expectedPixelY, CoordinateUtils.worldToPixelY(worldY, imageHeight));
        }

        @Test
        @DisplayName("Should round to nearest integer")
        void shouldRound() {
            // 0.333... should round to 667 not 666
            assertEquals(667, CoordinateUtils.worldToPixelX(0.334, 1000));
            // 0.001 * 500 + 500 = 500.5 -> 501 (rounds up)
            assertEquals(501, CoordinateUtils.worldToPixelX(0.002, 1000));
        }

        @Test
        @DisplayName("Should throw exception for zero image width")
        void zeroWidthThrows() {
            assertThrows(IllegalArgumentException.class, 
                () -> CoordinateUtils.worldToPixelX(0.5, 0));
        }
    }

    @Nested
    @DisplayName("Round-trip Conversion")
    class RoundTripTests {

        @Test
        @DisplayName("Pixel -> World -> Pixel should preserve value (within rounding)")
        void pixelToWorldToPixel() {
            int originalX = 423;
            int originalY = 789;
            int imageWidth = 1000;
            int imageHeight = 1200;

            double worldX = CoordinateUtils.pixelToWorldX(originalX, imageWidth);
            double worldY = CoordinateUtils.pixelToWorldY(originalY, imageHeight);

            int roundTripX = CoordinateUtils.worldToPixelX(worldX, imageWidth);
            int roundTripY = CoordinateUtils.worldToPixelY(worldY, imageHeight);

            assertEquals(originalX, roundTripX);
            assertEquals(originalY, roundTripY);
        }

        @Test
        @DisplayName("World -> Pixel -> World should be close (floating point precision)")
        void worldToPixelToWorld() {
            double originalX = 0.423;
            double originalY = -0.321;
            int imageWidth = 1000;
            int imageHeight = 1000;

            int pixelX = CoordinateUtils.worldToPixelX(originalX, imageWidth);
            int pixelY = CoordinateUtils.worldToPixelY(originalY, imageHeight);

            double roundTripX = CoordinateUtils.pixelToWorldX(pixelX, imageWidth);
            double roundTripY = CoordinateUtils.pixelToWorldY(pixelY, imageHeight);

            // Due to integer rounding, precision is limited to 2/imageWidth (world range is 2)
            assertEquals(originalX, roundTripX, 2.0 / imageWidth);
            assertEquals(originalY, roundTripY, 2.0 / imageHeight);
        }

        @ParameterizedTest
        @DisplayName("All corners should round-trip correctly")
        @CsvSource({
            "0, 0",
            "1000, 0",
            "0, 800",
            "1000, 800",
            "500, 400"
        })
        void cornersRoundTrip(int pixelX, int pixelY) {
            int imageWidth = 1000;
            int imageHeight = 800;

            double[] world = CoordinateUtils.pixelToWorld(pixelX, pixelY, imageWidth, imageHeight);
            int[] pixel = CoordinateUtils.worldToPixel(world[0], world[1], imageWidth, imageHeight);

            assertEquals(pixelX, pixel[0]);
            assertEquals(pixelY, pixel[1]);
        }
    }

    @Nested
    @DisplayName("Array Convenience Methods")
    class ArrayMethodsTests {

        @Test
        @DisplayName("pixelToWorld should return [worldX, worldY] array")
        void pixelToWorldArray() {
            double[] result = CoordinateUtils.pixelToWorld(250, 750, 1000, 1000);
            
            assertEquals(2, result.length);
            assertEquals(-0.5, result[0], EPSILON); // X
            assertEquals(-0.5, result[1], EPSILON); // Y (flipped: 750/1000 = 0.75 -> 1 - 1.5 = -0.5)
        }

        @Test
        @DisplayName("worldToPixel should return [pixelX, pixelY] array")
        void worldToPixelArray() {
            int[] result = CoordinateUtils.worldToPixel(-0.5, 0.5, 1000, 1000);
            
            assertEquals(2, result.length);
            assertEquals(250, result[0]); // X
            assertEquals(250, result[1]); // Y (flipped: (1 - 0.5) / 2 = 0.25 -> 250)
        }
    }
}

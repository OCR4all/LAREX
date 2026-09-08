package de.uniwue.zpd.dachs.larex.backend.util;

import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImageFileUtilsTest {

    @Test
    void sortForDisplayUsesPrimaryOriginalThenStableAlphabeticalOrder() {
        PageImage variantB = image("b", "variant-b", "page.z.png");
        PageImage original = image("original", "original", "page.png");
        PageImage variantA = image("a", "variant-a", "page.a.png");
        PageImage primary = image("primary", "processed", "page.processed.png");
        PageImage duplicate = image("duplicate", "variant-a", "page.b.png");

        assertEquals(
                List.of(primary, original, variantA, duplicate, variantB),
                ImageFileUtils.sortForDisplay(List.of(variantB, duplicate, original, primary, variantA), "processed")
        );
    }

    private static PageImage image(String id, String variant, String fileName) {
        PageImage image = new PageImage(fileName, "images/" + id, "image/png", 1L, variant, "page", null);
        image.setId(id);
        return image;
    }
}

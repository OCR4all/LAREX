package de.uniwue.web.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.primaresearch.dla.page.layout.physical.text.impl.Glyph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A representation of a Glyph that is parsed to the gui. Contains positional
 * points and text content in UTF-8.
 */
public class GlyphContainer  extends Element {
    /**
     * Text content of the Glyph. (UTF-8)
     */
    @JsonProperty("glyphVariants")
    protected List<de.uniwue.web.model.Glyph> glyphVariants;

    /**
     * Base constructor for the parsing from a JSON object, with all included data.
     *
     * @param primaGlyph         PrimaLibs Glyph Container
     * @param coords             Polygon which represents the coordinates in which the textline is enclosed
     */
    //@JsonCreator
    public GlyphContainer(@JsonProperty("glyphVariants")Glyph primaGlyph,
                          @JsonProperty("coords") Polygon coords) {
        super(primaGlyph.getId().toString(), coords);
        List<de.uniwue.web.model.Glyph> glyphVariants = new ArrayList<>();
        for(int i = 0; i < primaGlyph.getTextContentVariantCount(); i++) {
            String textContent = primaGlyph.getTextContentVariant(i).getText();
            double confidence = primaGlyph.getTextContentVariant(i).getConfidence();
            glyphVariants.add(new de.uniwue.web.model.Glyph(textContent,confidence));
        }
        //sort descending from highest confidence
        glyphVariants.sort(Comparator.comparing(de.uniwue.web.model.Glyph::getConf).reversed());
        this.glyphVariants = glyphVariants;
    }
    @JsonCreator
    public GlyphContainer(@JsonProperty("id") String id,
                          @JsonProperty("glyphVariants")List<de.uniwue.web.model.Glyph> glyphVariants,
                          @JsonProperty("coords") Polygon coords) {
        super(id, coords);
        //sort descending from highest confidence
        glyphVariants.sort(Comparator.comparing(de.uniwue.web.model.Glyph::getConf).reversed());
        this.glyphVariants = glyphVariants;
    }
    /**
     * Text content of the Glyph.(UTF-8)
     *
     * @return
     */
    public List<de.uniwue.web.model.Glyph> getGlyphVariants() {
        return glyphVariants;
    }
}

package de.uniwue.web.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A representation of a Glyph that is parsed to the gui. Contains positional
 * points and text content in UTF-8.
 */
public class Glyph  extends Element {
    /**
     * Text content of the Glyph. (UTF-8)
     */
    @JsonProperty("text")
    protected String text;
    @JsonProperty("conf")
    protected double conf;

    /**
     * Base constructor for the parsing from a JSON object, with all included data.
     *
     * @param id         Unique identifier of the text line
     * @param text       Text content inside the text line
     * @param coords Polygon which represents the coordinates in which the textline is enclosed
     */
    @JsonCreator
    public Glyph(@JsonProperty("id") String id,
                 @JsonProperty("coords") Polygon coords,
                 @JsonProperty("text") String text,
                 @JsonProperty("conf") double conf) {
        super(id, coords);
        this.text = text;
        this.conf = conf;
    }

    /**
     * Text content of the Glyph.(UTF-8)
     *
     * @return
     */
    public String getGlyph() {
        return text;
    }
}

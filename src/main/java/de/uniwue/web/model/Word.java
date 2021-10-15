package de.uniwue.web.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class Word extends Element{
    /**
     * Text content of the Word. (UTF-8)
     */
    @JsonProperty("text")
    protected String text;
    @JsonProperty("conf")
    protected double conf;
    @JsonProperty("glyphs")
    protected List<Glyph> glyphs;

    /**
     * Base constructor for the parsing from a JSON object, with all included data.
     *
     * @param id         Unique identifier of the word
     * @param text       Text content of word
     * @param coords Polygon which represents the coordinates in which the word is enclosed
     */
    @JsonCreator
    public Word(@JsonProperty("id") String id,
                @JsonProperty("coords") Polygon coords,
                @JsonProperty("text") String text,
                @JsonProperty("conf") double conf,
                @JsonProperty("glyphs") List<Glyph> glyphs) {
        super(id, coords);
        this.text = text;
        this.conf = conf;
        this.glyphs = glyphs;
    }

    /**
     * Text content of the Word.(UTF-8)
     *
     * @return
     */
    public String getWord() {
        return text;
    }
}

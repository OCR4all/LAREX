package de.uniwue.web.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A representation of a Glyph that is parsed to the gui. Contains text content in UTF-8
 * and its corresponding confidence.
 */
public class Glyph{
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
     * @param text       Text content inside the Glyph
     * @param conf       Confidence of Glyph
     */
    @JsonCreator
    public Glyph(@JsonProperty("text") String text,
                 @JsonProperty("conf") double conf) {
        this.text = text;
        this.conf = conf;
    }

    /**
     * Text content of the Glyph.(UTF-8)
     *
     * @return
     */
    public String getText() {
        return text;
    }
    /**
     * Confidence of the Glyph.
     *
     * @return
     */
    public double getConf() {
        return conf;
    }
}

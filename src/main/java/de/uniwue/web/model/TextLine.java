package de.uniwue.web.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A representation of a TextLine that is parsed to the gui. Contains positional
 * points and text content in UTF-8.
 */
public class TextLine extends Element {

	/**
	 * Text content of the TextLine. Different text layers each representing the
	 * TextLine. (UTF-8)
	 */
	@JsonProperty("text")
	protected Map<Integer, String> text;
	@JsonProperty("baseline")
	protected Polygon baseline;
	@JsonProperty("words")
	protected List<Word> words;

	@JsonProperty("comments")
	protected String comments;

	/**
	 * Base constructor for the parsing from a JSON object, with all included data.
	 *
	 * @param id         Unique identifier of the text line
	 * @param text       Text content inside the text line
	 * @param coords Polygon which represents the coordinates in which the textline is enclosed
	 * @param baseline Polygon which represents the coordinates of the textlines baseline
	 * @param comments Comments regarding the element
	 */
	@JsonCreator
	public TextLine(@JsonProperty("id") String id,
					@JsonProperty("coords") Polygon coords,
					@JsonProperty("text") Map<Integer, String> text,
					@JsonProperty("baseline") Polygon baseline,
					@JsonProperty("words") List<Word> words,
					@JsonProperty("comments") String comments) {
		super(id, coords);
		this.text = text;
		this.baseline = baseline;
		this.words = words;
		this.comments = comments;
	}

	public TextLine(String id, Polygon coords, Map<Integer, String> text) {
		super(id, coords);
		this.text = text;
		this.baseline = null;
		this.comments = null;
	}

	/**
	 * Text content of the TextLine. Different text layers each representing the
	 * TextLine. (UTF-8)
	 *
	 * @return
	 */
	public Map<Integer, String> getText() {
		return new HashMap<>(text);
	}

	public String getComments(){
		return this.comments;
	}
}

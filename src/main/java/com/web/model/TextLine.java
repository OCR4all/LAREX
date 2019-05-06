package com.web.model;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.opencv.core.MatOfPoint;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A representation of a TextLine that is parsed to the gui. Contains positional
 * points and text content in UTF-8.
 */
public class TextLine extends Polygon {

	/**
	 * Text content of the TextLine. Different text layers each representing the
	 * TextLine. (UTF-8)
	 */
	@JsonProperty("text")
	protected Map<String, String> text;

	/**
	 * Base constructor for the parsing from a JSON object, with all included data.
	 * 
	 * @param id         Unique identifier of the text line
	 * @param text       Text content inside the text line
	 * @param points
	 * @param isRelative
	 */
	@JsonCreator
	public TextLine(@JsonProperty("id") String id, @JsonProperty("points") LinkedList<Point> points,
			@JsonProperty("text") Map<String, String> text, @JsonProperty("isRelative") boolean isRelative) {
		super(id, points, isRelative);
		this.text = text;
	}

	/**
	 * Short hand constructor to create a basic TextLine
	 * 
	 * @param id     Unique identifier of the text line
	 * @param points
	 * @param text   Text content inside the text line
	 */
	public TextLine(String id, LinkedList<Point> points, Map<String, String> text) {
		this(id, points, text, false);
	}

	/**
	 * Constructor to parse a MatOfPoint OpenCV object to a TextLine
	 * 
	 * @param mat
	 * @param id
	 * @param text
	 */
	public TextLine(MatOfPoint mat, String id, Map<String, String> text) {
		super(mat, id);
		this.text = text;
	}

	/**
	 * Text content of the TextLine. Different text layers each representing the
	 * TextLine. (UTF-8)
	 * 
	 * @return
	 */
	public Map<String, String> getText() {
		return new HashMap<>(text);
	}
}

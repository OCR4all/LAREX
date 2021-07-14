package de.uniwue.web.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Representation of a Page with an id and an image. (Page specific Settings and
 * Segmentation are handled in Settings and BookSegmentation respectively)
 *
 */
public class Page {

	@JsonProperty("id")
	private int id;
	/**
	 * Name of the page (does not include image extensions)
	 * Does not include sub extensions if imageSubFilter is active
	 */
	@JsonProperty("name")
	private String name;
	@JsonProperty("xmlName")
	private String xmlName;
	/**
	 * (Multiple) image(s) representing this page
	 */
	@JsonProperty("images")
	private List<String> images;
	@JsonProperty("width")
	private int width;
	@JsonProperty("height")
	private int height;
	@JsonProperty("orientation")
	private double orientation;

	@JsonCreator
	public Page(@JsonProperty("id") int id,
				@JsonProperty("name") String name,
				@JsonProperty("xmlName") String xmlName,
				@JsonProperty("images") List<String> images,
				@JsonProperty("width") int width,
				@JsonProperty("height") int height,
				@JsonProperty("orientation") double orientation) {
		this.id = id;
		this.name = name;
		this.xmlName = xmlName;
		this.images = images;
		this.height = height;
		this.width = width;
		this.orientation = orientation;
	}

	public Page(int id, String name, String xmlName, List<String> images, int width, int height){
		this.id = id;
		this.name = name;
		this.xmlName = xmlName;
		this.images = images;
		this.width = width;
		this.height = height;
		this.orientation = 0.0;
	}

	public int getId() {
		return id;
	}

	public List<String> getImages() {
		return images;
	}

	public int getHeight() {
		return height;
	}

	public int getWidth() {
		return width;
	}

	public String getName() {
		return name;
	}

	public String getXmlName() {
		return xmlName;
	}

	public double getOrientation() { return orientation;}

	public void setOrientation(double orientation) { this.orientation = orientation;}
}

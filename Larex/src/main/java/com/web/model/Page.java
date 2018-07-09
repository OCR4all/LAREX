package com.web.model;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonProperty;


/**
 * Representation of a Page with an id and an image. (Page specific Settings and
 * Segmentation are handled in Settings and BookSegmentation respectively)
 * 
 */
public class Page {

	@JsonProperty("id")
	private int id;
	@JsonProperty("fileName")
	private String fileName;
	@JsonProperty("image")
	private String image;
	@JsonProperty("width")
	private int width;
	@JsonProperty("height")
	private int height;

	@JsonCreator
	public Page(@JsonProperty("id") int id, @JsonProperty("fileName") String fileName, @JsonProperty("image") String image,
			@JsonProperty("width") int width, @JsonProperty("height") int height) {
		this.id = id;
		this.fileName = fileName;
		this.image = image;
		this.height = height;
		this.width = width;
	}

	public int getId() {
		return id;
	}

	public String getImage() {
		return image;
	}

	public void setImage(String image) {
		this.image = image;
	}

	public int getHeight() {
		return height;
	}

	public int getWidth() {
		return width;
	}
	
	@JsonIgnore
	public String getName() {
		return fileName.substring(0, fileName.lastIndexOf("."));
	}
	
	
	public String getFileName() {
		return fileName;
	}
}

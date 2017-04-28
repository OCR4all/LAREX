package com.web.model;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

/**
 * Representation of a Page with an id and an image.
 * (Page specific Settings and Segmentation are handled 
 * in Settings and BookSegmentation respectively)
 * 
 */
public class Page {

	@JsonProperty("id")
	private int id;
	@JsonProperty("image")
	private String image;

	@JsonCreator
	public Page(@JsonProperty("id") int id, @JsonProperty("image") String image) {
		this.id = id;
		this.image = image;
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
}

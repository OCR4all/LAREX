package com.web.model;

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
	@JsonProperty("name")
	private String name;
	@JsonProperty("images")
	private List<String> images;
	@JsonProperty("width")
	private int width;
	@JsonProperty("height")
	private int height;

	@JsonCreator
	public Page(@JsonProperty("id") int id, @JsonProperty("name") String name, @JsonProperty("images") List<String> images,
			@JsonProperty("width") int width, @JsonProperty("height") int height) {
		this.id = id;
		this.name = name;
		this.images = images;
		this.height = height;
		this.width = width;
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
}

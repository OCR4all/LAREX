package com.web.communication;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.web.model.Point;

/**
 * Communication object for the gui to request a merge of multiple contours into one.
 * Requires page and book ids to determine the page size.
 */
public class ContourCombineRequest {

	@JsonProperty("page_width")
	private Integer pageWidth;
	@JsonProperty("page_height")
	private Integer pageHeight;
	@JsonProperty("contours")
	private List<List<Point>> contours;
	@JsonProperty("accuracy")
	private Integer accuracy;

	@JsonCreator
	public ContourCombineRequest(@JsonProperty("page_width") Integer pageWidth, @JsonProperty("page_height") Integer pageHeight,
			@JsonProperty("contours") List<List<Point>>  contours, @JsonProperty("accuracy") Integer accuracy) {
		this.pageWidth = pageWidth;
		this.pageHeight = pageHeight;
		this.contours = contours;
		this.accuracy = accuracy;
	}

	public List<List<Point>> getContours() {
		return contours;
	}

	public Integer getPageHeight() {
		return pageHeight;
	}

	public Integer getPageWidth() {
		return pageWidth;
	}
	
	public Integer getAccuracy() {
		return accuracy;
	}
}

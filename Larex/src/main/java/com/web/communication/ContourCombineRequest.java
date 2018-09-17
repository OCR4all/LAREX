package com.web.communication;

import java.util.List;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

import com.web.model.Point;

/**
 * Communication object for the gui to request a merge between segments.
 * 
 */
public class ContourCombineRequest {

	@JsonProperty("bookid")
	private Integer bookid;
	@JsonProperty("pageid")
	private Integer page;
	@JsonProperty("contours")
	private List<List<Point>> contours;

	@JsonCreator
	public ContourCombineRequest(@JsonProperty("bookid") Integer bookid, @JsonProperty("pageid") Integer page,
			@JsonProperty("contours") List<List<Point>>  contours) {
		this.bookid = bookid;
		this.page = page;
		this.contours = contours;
	}

	public Integer getPage() {
		return page;
	}

	public List<List<Point>> getContours() {
		return contours;
	}

	public Integer getBookid() {
		return bookid;
}
}

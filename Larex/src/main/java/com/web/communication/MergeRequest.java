package com.web.communication;

import java.util.List;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

import com.web.model.Polygon;

/**
 * Communication object for the gui to request a merge between segments.
 * 
 */
public class MergeRequest {

	@JsonProperty("bookid")
	private Integer bookid;
	@JsonProperty("pageid")
	private Integer page;
	@JsonProperty("segments")
	private List<Polygon> segments;

	@JsonCreator
	public MergeRequest(@JsonProperty("bookid") Integer bookid, @JsonProperty("pageid") Integer page,
			@JsonProperty("segments") List<Polygon> segments) {
		this.bookid = bookid;
		this.page = page;
		this.segments = segments;
	}

	public Integer getPage() {
		return page;
	}

	public List<Polygon> getSegments() {
		return segments;
	}

	public Integer getBookid() {
		return bookid;
}
}

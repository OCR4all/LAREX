package com.web.communication;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.web.model.Region;

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
	private List<Region> segments;

	@JsonCreator
	public MergeRequest(@JsonProperty("bookid") Integer bookid, @JsonProperty("pageid") Integer page,
			@JsonProperty("segments") List<Region> segments) {
		this.bookid = bookid;
		this.page = page;
		this.segments = segments;
	}

	public Integer getPage() {
		return page;
	}

	public List<Region> getSegments() {
		return segments;
	}

	public Integer getBookid() {
		return bookid;
}
}

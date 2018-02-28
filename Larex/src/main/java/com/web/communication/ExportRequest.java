package com.web.communication;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

import com.web.model.PageSegmentation;

/**
 * Communication object for the gui to request a export of a segmentation
 * 
 */
public class ExportRequest {

	@JsonProperty("bookid")
	private Integer bookid;
	@JsonProperty("segmentation")
	private PageSegmentation segmentation;

	@JsonCreator
	public ExportRequest(@JsonProperty("bookid") Integer bookid,
			@JsonProperty("segmentation") PageSegmentation segmentation) {
		this.bookid = bookid;
		this.segmentation = segmentation;
	}

	public Integer getBookid() {
		return bookid;
	}

	public PageSegmentation getSegmentation() {
		return segmentation;
	}
}

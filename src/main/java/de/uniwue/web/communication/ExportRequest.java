package de.uniwue.web.communication;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.uniwue.web.model.PageAnnotations;

/**
 * Communication object for the gui to request a export of a segmentation
 * 
 */
public class ExportRequest {

	@JsonProperty("bookid")
	private Integer bookid;
	@JsonProperty("segmentation")
	private PageAnnotations segmentation;
	@JsonProperty("version")
	private String version;

	@JsonCreator
	public ExportRequest(@JsonProperty("bookid") Integer bookid,
			@JsonProperty("segmentation") PageAnnotations segmentation, @JsonProperty("version") String version) {
		this.bookid = bookid;
		this.segmentation = segmentation;
		this.version = version;
	}

	public Integer getBookid() {
		return bookid;
	}

	public PageAnnotations getSegmentation() {
		return segmentation;
	}

	public String getVersion() {
		return version;
	}
}
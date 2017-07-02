package com.web.communication;

import java.util.List;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

import com.web.model.BookSettings;

/**
 * Communication object for the gui to request an export of a specifit page.
 * Contains the export settings and the page number to export.
 * 
 */
public class ExportRequest {

	@JsonProperty("page")
	private int page;
	@JsonProperty("segmentsToIgnore")
	private List<String> segmentsToIgnore;

	@JsonCreator
	public ExportRequest(@JsonProperty("page") int page, @JsonProperty("segmentsToIgnore") List<String> segmentsToIgnore) {
		this.page = page;
		this.segmentsToIgnore = segmentsToIgnore;
	}

	public int getPage() {
		return page;
	}

	public List<String> getSegmentsToIgnore() {
		return segmentsToIgnore;
	}
}

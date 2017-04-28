package com.web.communication;

import java.util.List;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

import com.web.model.BookSettings;

/**
 * Communication object for the gui to request the segmentation of different pages.
 * Contains the segmentation settings and a list of page numbers to segment.
 * 
 */
public class SegmentationRequest {

	@JsonProperty("settings")
	private BookSettings settings;
	@JsonProperty("pages")
	private List<Integer> pages;

	@JsonCreator
	public SegmentationRequest(@JsonProperty("settings") BookSettings settings,
			@JsonProperty("pages") List<Integer> pages) {
		this.pages = pages;
		this.settings = settings;
	}

	public List<Integer> getPages() {
		return pages;
	}

	public BookSettings getSettings() {
		return settings;
	}
}

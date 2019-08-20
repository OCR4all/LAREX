package com.web.communication;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.web.facade.segmentation.SegmentationSettings;

/**
 * Communication object for the gui to request the segmentation of different
 * pages. Contains the segmentation settings and a list of page numbers to
 * segment.
 * 
 */
public class SegmentationRequest {

	@JsonProperty("settings")
	private SegmentationSettings settings;
	@JsonProperty("page")
	private Integer page;

	@JsonCreator
	public SegmentationRequest(@JsonProperty("settings") SegmentationSettings settings, @JsonProperty("page") Integer page) {
		this.page = page;
		this.settings = settings;
	}

	public Integer getPage() {
		return page;
	}

	public SegmentationSettings getSettings() {
		return settings;
	}
}

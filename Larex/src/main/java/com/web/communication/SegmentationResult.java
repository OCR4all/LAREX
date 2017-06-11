package com.web.communication;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

import com.web.model.BookSegmentation;

/**
 * Communication object for the gui as result of a segmentation request.
 * Contains the segmentation result and a status.
 * 
 */
public class SegmentationResult {

	@JsonProperty("result")
	private BookSegmentation result;
	@JsonProperty("status")
	private SegmentationStatus status;

	@JsonCreator
	public SegmentationResult(@JsonProperty("result") BookSegmentation result,
			@JsonProperty("status") SegmentationStatus status) {
		this.status = status;
		this.result = result;
	}

	public BookSegmentation getResult() {
		return result;
	}

	public SegmentationStatus getStatus() {
		return status;
	}
}

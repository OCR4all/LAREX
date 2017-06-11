package com.web.model;

import java.util.HashMap;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

import com.web.communication.SegmentationStatus;

/**
 * Segmentation result of a specific page. Contains a pageNr and the resulting
 * segment polygons.
 * 
 */
public class PageSegmentation {

	@JsonProperty("page")
	private int pageNr;
	@JsonProperty("segments")
	private Map<String, Polygon> segments;
	@JsonProperty("status")
	private SegmentationStatus status;

	public PageSegmentation(int pageNr) {
		this(pageNr, new HashMap<String, Polygon>(), SegmentationStatus.UNSEGMENTED);
	}
	@JsonCreator
	public PageSegmentation(int pageNr, Map<String, Polygon> segments) {
		this(pageNr,segments,SegmentationStatus.SUCCESS);
	}
	
	@JsonCreator
	public PageSegmentation(@JsonProperty("page") int pageNr, @JsonProperty("segments") Map<String, Polygon> segments,
			@JsonProperty("status") SegmentationStatus status) {
		this.pageNr = pageNr;
		this.segments = segments;
		this.status = status;
	}

	public void addSegment(Polygon segment) {
		segments.put(segment.getId(), segment);
	}

	public int getPage() {
		return pageNr;
	}

	public Map<String, Polygon> getSegments() {
		return new HashMap<String, Polygon>(segments);
	}
	
	public SegmentationStatus getStatus() {
		return status;
	}
}

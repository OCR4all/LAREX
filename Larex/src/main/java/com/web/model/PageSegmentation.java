package com.web.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
	@JsonProperty("readingOrder")
	private List<String> readingOrder;
	@JsonProperty("status")
	private SegmentationStatus status;

	public PageSegmentation(int pageNr) {
		this(pageNr, new HashMap<String, Polygon>(), SegmentationStatus.UNSEGMENTED,new ArrayList<String>());
	}

	public PageSegmentation(int pageNr, Map<String, Polygon> segments) {
		this(pageNr,segments,SegmentationStatus.SUCCESS,new ArrayList<String>());
	}
	
	@JsonCreator
	public PageSegmentation(@JsonProperty("page") int pageNr, @JsonProperty("segments") Map<String, Polygon> segments,
			@JsonProperty("status") SegmentationStatus status, @JsonProperty("readingOrder") List<String> readingOrder) {
		this.pageNr = pageNr;
		this.segments = segments;
		this.status = status;
		this.readingOrder = readingOrder;
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
	
	public List<String> getReadingOrder() {
		return new ArrayList<String>(readingOrder);
	}
	public void setReadingOrder(List<String> readingOrder) {
		this.readingOrder = readingOrder;
	}
}

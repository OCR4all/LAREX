package com.web.model;

import java.util.HashMap;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

/**
 * Segmentation result of a specific page. 
 * Contains a pageNr and the resulting segment polygons.
 * 
 */
public class PageSegmentation {

	@JsonProperty("page")
	private int pageNr;
	@JsonProperty("segments")
	private Map<String, Polygon> segments;

	public PageSegmentation(int pageNr) {
		this(pageNr, new HashMap<String, Polygon>());
	}

	@JsonCreator
	public PageSegmentation(@JsonProperty("page") int pageNr, @JsonProperty("segments") Map<String, Polygon> segments) {
		this.pageNr = pageNr;
		this.segments = segments;
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
}

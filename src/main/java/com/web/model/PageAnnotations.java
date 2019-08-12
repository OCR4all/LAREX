package com.web.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.web.communication.SegmentationStatus;

import larex.geometry.regions.RegionSegment;

/**
 * Segmentation result of a specific page. Contains a pageNr and the resulting
 * segment polygons.
 * 
 */
public class PageAnnotations {
	@JsonProperty("name")
	private String name;
	@JsonProperty("width")
	private int width;
	@JsonProperty("height")
	private int height;
	@JsonProperty("page")
	private int pageNr;
	@JsonProperty("segments")
	private Map<String, Region> segments;
	@JsonProperty("readingOrder")
	private List<String> readingOrder;
	@JsonProperty("status")
	private SegmentationStatus status;

	public PageAnnotations(String name, int width, int height, int pageNr, Map<String, Region> segments) {
		this(name, width, height, pageNr, segments, SegmentationStatus.SUCCESS, new ArrayList<String>());
	}

	@JsonCreator
	public PageAnnotations(@JsonProperty("name") String name, @JsonProperty("width") int width,
			@JsonProperty("height") int height, @JsonProperty("page") int pageNr,
			@JsonProperty("segments") Map<String, Region> segments, @JsonProperty("status") SegmentationStatus status,
			@JsonProperty("readingOrder") List<String> readingOrder) {
		this.pageNr = pageNr;
		this.segments = segments;
		this.status = status;
		this.readingOrder = readingOrder;
		this.name = name;
		this.width = width;
		this.height = height;
	}

	public PageAnnotations(String name, int width, int height, Collection<RegionSegment> regions, int pageNr) {
		Map<String, Region> segments = new HashMap<String, Region>();

		for (RegionSegment region : regions) {
			LinkedList<Point> points = new LinkedList<Point>();
			for (org.opencv.core.Point regionPoint : region.getPoints().toList()) {
				points.add(new Point(regionPoint.x, regionPoint.y));
			}

			Region segment = new Region(points, region.getId(), region.getType().toString());
			segments.put(segment.getId(), segment);
		}

		this.pageNr = pageNr;
		this.segments = segments;
		this.status = SegmentationStatus.SUCCESS;
		this.readingOrder = new ArrayList<String>();
		this.name = name;
		this.width = width;
		this.height = height;
	}
	
	public int getPage() {
		return pageNr;
	}

	public Map<String, Region> getSegments() {
		return new HashMap<String, Region>(segments);
	}

	public SegmentationStatus getStatus() {
		return status;
	}

	public void setStatus(SegmentationStatus status) {
		this.status = status;
	}

	public List<String> getReadingOrder() {
		return new ArrayList<String>(readingOrder);
	}

	public void setReadingOrder(List<String> readingOrder) {
		this.readingOrder = readingOrder;
	}

	public String getName() {
		return name;
	}

	public int getHeight() {
		return height;
	}

	public int getWidth() {
		return width;
	}
}

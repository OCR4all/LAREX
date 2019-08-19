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
 */
public class PageAnnotations {
	@JsonProperty("name")
	private final String name;
	@JsonProperty("width")
	private final int width;
	@JsonProperty("height")
	private final int height;
	@JsonProperty("segments")
	private final Map<String, Region> segments;
	@JsonProperty("readingOrder")
	private final List<String> readingOrder;
	@JsonProperty("status")
	private final SegmentationStatus status;

	public PageAnnotations(String name, int width, int height, Map<String, Region> segments) {
		this(name, width, height, segments, SegmentationStatus.SUCCESS, new ArrayList<String>());
	}

	@JsonCreator
	public PageAnnotations(@JsonProperty("name") String name, @JsonProperty("width") int width,
			@JsonProperty("height") int height,
			@JsonProperty("segments") Map<String, Region> segments, @JsonProperty("status") SegmentationStatus status,
			@JsonProperty("readingOrder") List<String> readingOrder) {
		this.segments = segments;
		this.status = status;
		this.readingOrder = readingOrder;
		this.name = name;
		this.width = width;
		this.height = height;
	}

	public PageAnnotations(String name, int width, int height, int pageNr, 
			Collection<RegionSegment> regions,  SegmentationStatus status) {
		Map<String, Region> segments = new HashMap<String, Region>();

		for (RegionSegment region : regions) {
			LinkedList<Point> points = new LinkedList<Point>();
			for (org.opencv.core.Point regionPoint : region.getPoints().toList()) {
				points.add(new Point(regionPoint.x, regionPoint.y));
			}

			Region segment = new Region(points, region.getId(), region.getType().toString());
			segments.put(segment.getId(), segment);
		}

		this.segments = segments;
		this.status = status;
		this.readingOrder = new ArrayList<String>();
		this.name = name;
		this.width = width;
		this.height = height;
	}
	
	public Map<String, Region> getSegments() {
		return new HashMap<String, Region>(segments);
	}

	public SegmentationStatus getStatus() {
		return status;
	}

	public List<String> getReadingOrder() {
		return new ArrayList<String>(readingOrder);
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

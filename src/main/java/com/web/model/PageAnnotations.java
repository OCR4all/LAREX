package com.web.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.web.communication.SegmentationStatus;

import larex.geometry.regions.RegionSegment;
import larex.segmentation.SegmentationResult;

/**
 * Segmentation result of a specific page. Contains a pageNr and the resulting
 * segment polygons.
 * 
 */
public class PageAnnotations {
	@JsonProperty("fileName")
	private String fileName;
	@JsonProperty("width")
	private int width;
	@JsonProperty("height")
	private int height;
	@JsonProperty("page")
	private int pageNr;
	@JsonProperty("segments")
	private Map<String, Polygon> segments;
	@JsonProperty("readingOrder")
	private List<String> readingOrder;
	@JsonProperty("status")
	private SegmentationStatus status;

	public PageAnnotations(String fileName, int width, int height, int pageNr, Map<String, Polygon> segments) {
		this(fileName, width, height, pageNr, segments, SegmentationStatus.SUCCESS, new ArrayList<String>());
	}

	@JsonCreator
	public PageAnnotations(@JsonProperty("fileName") String fileName, @JsonProperty("width") int width,
			@JsonProperty("height") int height, @JsonProperty("page") int pageNr,
			@JsonProperty("segments") Map<String, Polygon> segments, @JsonProperty("status") SegmentationStatus status,
			@JsonProperty("readingOrder") List<String> readingOrder) {
		this.pageNr = pageNr;
		this.segments = segments;
		this.status = status;
		this.readingOrder = readingOrder;
		this.fileName = fileName;
		this.width = width;
		this.height = height;
	}

	public PageAnnotations(String fileName, int width, int height, ArrayList<RegionSegment> regions, int pageNr) {
		Map<String, Polygon> segments = new HashMap<String, Polygon>();

		for (RegionSegment region : regions) {
			Polygon segment = new Polygon(region);
			segments.put(segment.getId(), segment);
		}

		this.pageNr = pageNr;
		this.segments = segments;
		this.status = SegmentationStatus.SUCCESS;
		this.readingOrder = new ArrayList<String>();
		this.fileName = fileName;
		this.width = width;
		this.height = height;
	}
	
	public SegmentationResult toSegmentationResult() {
		ArrayList<RegionSegment> regions = new ArrayList<RegionSegment>();

		for (String poly : this.getSegments().keySet()) {
			Polygon polygon = this.getSegments().get(poly);
			regions.add(polygon.toRegionSegment());
		}
		SegmentationResult result = new SegmentationResult(regions);

		// Reading Order
		ArrayList<RegionSegment> readingOrder = new ArrayList<RegionSegment>();
		List<String> readingOrderStrings = this.getReadingOrder();
		for (String regionID : readingOrderStrings) {
			RegionSegment region = result.getRegionByID(regionID);
			if (region != null) {
				readingOrder.add(region);
			}
		}
		result.setReadingOrder(readingOrder);
		return result;
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

	public void setStatus(SegmentationStatus status) {
		this.status = status;
	}

	public List<String> getReadingOrder() {
		return new ArrayList<String>(readingOrder);
	}

	public void setReadingOrder(List<String> readingOrder) {
		this.readingOrder = readingOrder;
	}

	public String getFileName() {
		return fileName;
	}

	@JsonIgnore
	public String getName() {
		return fileName.substring(0, fileName.lastIndexOf("."));
	}

	public int getHeight() {
		return height;
	}

	public int getWidth() {
		return width;
	}
}

package com.web.facade.segmentation;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.web.model.Region;

import larex.geometry.positions.PriorityPosition;

/**
 * A collection of all polygons of one region type with different settings for
 * the Larex Segmentation algorithm
 */
public class SegmentationRegionArea {
	@JsonProperty("type")
	private String type;
	@JsonProperty("polygons")
	private Map<String, Region> polygons;
	@JsonProperty("minSize")
	private int minSize;
	@JsonProperty("maxOccurances")
	private int maxOccurances;
	@JsonProperty("priorityPosition")
	private PriorityPosition priorityPosition;

	@JsonCreator
	public SegmentationRegionArea(@JsonProperty("type") String type, @JsonProperty("polygons") Map<String, Region> polygons,
			@JsonProperty("minSize") int minSize, @JsonProperty("maxOccurances") int maxOccurances,
			@JsonProperty("priorityPosition") PriorityPosition priorityPosition) {
		this.type = type;
		this.polygons = polygons;
		this.minSize = minSize;
		this.maxOccurances = maxOccurances;
		this.priorityPosition = priorityPosition;
	}

	public SegmentationRegionArea(String type, int minSize, int maxOccurances, PriorityPosition priorityPosition) {
		this(type, new HashMap<String, Region>(), minSize, maxOccurances, priorityPosition);
	}

	public String getType() {
		return type;
	}

	public Map<String, Region> getPolygons() {
		return new HashMap<String, Region>(polygons);
	}

	public void addPolygon(Region polygon) {
		polygons.put(polygon.getId(), polygon);
	}

	public int getMaxOccurances() {
		return maxOccurances;
	}

	public int getMinSize() {
		return minSize;
	}

	public PriorityPosition getPriorityPosition() {
		return priorityPosition;
	}
}

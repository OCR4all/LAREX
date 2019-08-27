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
public class RegionSettings {
	@JsonProperty("type")
	private String type;
	@JsonProperty("areas")
	private Map<String, Region> areas;
	@JsonProperty("minSize")
	private int minSize;
	@JsonProperty("maxOccurances")
	private int maxOccurances;
	@JsonProperty("priorityPosition")
	private PriorityPosition priorityPosition;

	@JsonCreator
	public RegionSettings(@JsonProperty("type") String type, @JsonProperty("areas") Map<String, Region> areas,
			@JsonProperty("minSize") int minSize, @JsonProperty("maxOccurances") int maxOccurances,
			@JsonProperty("priorityPosition") PriorityPosition priorityPosition) {
		this.type = type;
		this.areas = areas;
		this.minSize = minSize;
		this.maxOccurances = maxOccurances;
		this.priorityPosition = priorityPosition;
	}

	public RegionSettings(String type, int minSize, int maxOccurances, PriorityPosition priorityPosition) {
		this(type, new HashMap<String, Region>(), minSize, maxOccurances, priorityPosition);
	}

	public String getType() {
		return type;
	}

	public Map<String, Region> getAreas() {
		return new HashMap<String, Region>(areas);
	}

	public void addArea(Region area) {
		areas.put(area.getId(), area);
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

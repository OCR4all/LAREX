package com.web.model;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import larex.geometry.positions.PriorityPosition;

/**
 * A collection of all polygons of one region type with different settings for
 * the Larex algorithm
 *
 */
public class Region {
	@JsonProperty("type")
	private String type;
	@JsonProperty("polygons")
	private Map<String, Polygon> polygons;
	@JsonProperty("minSize")
	private int minSize;
	@JsonProperty("maxOccurances")
	private int maxOccurances;
	@JsonProperty("priorityPosition")
	private PriorityPosition priorityPosition;

	@JsonCreator
	public Region(@JsonProperty("type") String type, @JsonProperty("polygons") Map<String, Polygon> polygons,
			@JsonProperty("minSize") int minSize, @JsonProperty("maxOccurances") int maxOccurances,
			@JsonProperty("priorityPosition") PriorityPosition priorityPosition) {
		this.type = type;
		this.polygons = polygons;
		this.minSize = minSize;
		this.maxOccurances = maxOccurances;
		this.priorityPosition = priorityPosition;
	}

	public Region(String type, int minSize, int maxOccurances, PriorityPosition priorityPosition) {
		this(type, new HashMap<String, Polygon>(), minSize, maxOccurances, priorityPosition);
	}

	public String getType() {
		return type;
	}

	public Map<String, Polygon> getPolygons() {
		return new HashMap<String, Polygon>(polygons);
	}

	public void addPolygon(Polygon polygon) {
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

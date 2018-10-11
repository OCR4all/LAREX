package com.web.model;

import java.util.LinkedList;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import larex.regions.type.RegionType;

/**
 * A representation of a Segment or Region as Polygon that is parsed to the gui.
 * Contains positional points and type.
 * Polygon can be created relative or absolute. 
 * - A absolut Polygon is positioned via pixel (e.g. 10:10 -> 10px:10px)
 * - A relative Polygon is positioned via percentage (e.g. 0.1:0.1 -> 10%:10%)
 * 
 */
public class Polygon {

	@JsonProperty("id")
	protected String id;
	@JsonProperty("type")
	protected RegionType type;
	@JsonProperty("points")
	protected LinkedList<Point> points;
	@JsonProperty("isRelative")
	protected boolean isRelative;
	

	@JsonCreator
	public Polygon(@JsonProperty("id") String id, 
			@JsonProperty("type") RegionType type, 
			@JsonProperty("points") LinkedList<Point> points, 
			@JsonProperty("isRelative") boolean isRelative) {
		this.id = id;
		this.type = type;
		this.points = points;
		this.isRelative = isRelative;
	}

	public String getId() {
		return id;
	}
	
	public LinkedList<Point> getPoints() {
		return new LinkedList<Point>(points);
	}

	public RegionType getType() {
		return type;
	}
}

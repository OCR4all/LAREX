package com.web.model;

import java.util.LinkedList;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A representation of a Polygon that is parsed to the gui. Contains positional
 * points. Polygon can be created relative or absolute. - A absolut Polygon is
 * positioned via pixel (e.g. 10:10 -> 10px:10px) - A relative Polygon is
 * positioned via percentage (e.g. 0.1:0.1 -> 10%:10%)
 * 
 */
public class Polygon {

	@JsonProperty("id")
	protected String id;
	@JsonProperty("points")
	protected LinkedList<Point> points;
	@JsonProperty("isRelative")
	protected boolean isRelative;

	@JsonCreator
	public Polygon(@JsonProperty("id") String id, @JsonProperty("points") LinkedList<Point> points,
			@JsonProperty("isRelative") boolean isRelative) {
		this.id = id;
		this.points = points;
		this.isRelative = isRelative;
	}

	public Polygon(LinkedList<Point> points, String id) {
		this.id = id;
		this.points = points;
		this.isRelative = false;
	}

	public String getId() {
		return id;
	}

	public LinkedList<Point> getPoints() {
		return new LinkedList<Point>(points);
	}
	
	public boolean isRelative() {
		return isRelative;
	}
}

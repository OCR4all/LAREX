package com.web.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A representation of a positional Point on a page scan.
 * Can be parsed to the gui.
 * Origin on the topleft corner
 * 
 */
public class Point {

	@JsonProperty("x")
	private double x;
	@JsonProperty("y")
	private double y;

	@JsonCreator
	public Point(@JsonProperty("x") double x, @JsonProperty("y") double y) {
		this.x = x;
		this.y = y;
	}

	public double getX() {
		return x;
	}

	public double getY() {
		return y;
	}
}

package de.uniwue.web.model;

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
public class Rectangle extends Polygon{

	final private double angle;
	final private double height;
	final private double width;
	
	@JsonCreator
	public Rectangle(@JsonProperty("id") String id, @JsonProperty("points") LinkedList<Point> points,
			@JsonProperty("height") double height, @JsonProperty("width") double width,
			@JsonProperty("angle") double angle, @JsonProperty("isRelative") boolean isRelative) {
		super(id,points,isRelative);
		this.angle = angle;
		this.height = height;
		this.width = width;
	}
	
	/**
	 * Return the angle by which the rectangle is rotated by
	 * 
	 * @return
	 */
	public double getAngle() {
		return angle;
	}
	
	/**
	 * Retrieve the height of a rotated rectangle (considering its angle)
	 * @return
	 */
	public double getHeight() {
		return height;
	}
	
	/**
	 * Retrieve the width of a rotated rectangle (considering its angle)
	 * @return
	 */
	public double getWidth() {
		return width;
	}

}

package com.web.model;

import java.util.LinkedList;

import org.opencv.core.MatOfPoint;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import larex.geometry.regions.RegionSegment;
import larex.geometry.regions.type.PAGERegionType;
import larex.geometry.regions.type.RegionType;
import larex.geometry.regions.type.TypeConverter;

/**
 * A representation of a Segment or Region as Polygon that is parsed to the gui.
 * Contains positional points and type. Polygon can be created relative or
 * absolute. - A absolut Polygon is positioned via pixel (e.g. 10:10 ->
 * 10px:10px) - A relative Polygon is positioned via percentage (e.g. 0.1:0.1 ->
 * 10%:10%)
 * 
 */
public class Polygon {

	@JsonProperty("id")
	protected String id;
	@JsonProperty("type")
	protected String type;
	@JsonProperty("points")
	protected LinkedList<Point> points;
	@JsonProperty("isRelative")
	protected boolean isRelative;

	@JsonCreator
	public Polygon(@JsonProperty("id") String id, @JsonProperty("type") String type,
			@JsonProperty("points") LinkedList<Point> points, @JsonProperty("isRelative") boolean isRelative) {
		this.id = id;
		this.type = type;
		this.points = points;
		this.isRelative = isRelative;
	}

	public Polygon(MatOfPoint mat, String id, String type) {
		LinkedList<Point> points = new LinkedList<Point>();
		for (org.opencv.core.Point regionPoint : mat.toList()) {
			points.add(new Point(regionPoint.x, regionPoint.y));
		}

		this.id = id;
		this.type = type;
		this.points = points;
		this.isRelative = false;
	}

	public Polygon(RegionSegment region) {
		this(region.getPoints(), region.getId(), region.getType().toString());
	}

	/**
	 * Create a Larex RegionSegment from this polygon
	 * 
	 * @return
	 */
	public RegionSegment toRegionSegment() {
		LinkedList<org.opencv.core.Point> points = new LinkedList<org.opencv.core.Point>();

		for (Point segmentPoint : this.getPoints()) {
			points.add(new org.opencv.core.Point(segmentPoint.getX(), segmentPoint.getY()));
		}

		MatOfPoint resultPoints = new MatOfPoint();
		resultPoints.fromList(points);

		return new RegionSegment(TypeConverter.stringToPAGEType(this.getType()), resultPoints, this.getId());
	}

	public String getId() {
		return id;
	}

	public LinkedList<Point> getPoints() {
		return new LinkedList<Point>(points);
	}

	public String getType() {
		return type;
	}
}

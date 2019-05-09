package com.web.model;

import java.util.LinkedList;
import java.util.Map;

import org.opencv.core.MatOfPoint;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import larex.geometry.regions.RegionSegment;
import larex.geometry.regions.type.TypeConverter;

/**
 * A representation of a Segment or Region as Polygon that is parsed to the gui.
 * Contains positional points and type. Polygon can be created relative or
 * absolute. - A absolut Polygon is positioned via pixel (e.g. 10:10 ->
 * 10px:10px) - A relative Polygon is positioned via percentage (e.g. 0.1:0.1 ->
 * 10%:10%)
 * 
 */
public class Region extends Polygon{

	@JsonProperty("type")
	protected String type;
	@JsonProperty("textlines")
	protected Map<String,TextLine> textlines;

	@JsonCreator
	public Region(@JsonProperty("id") String id, @JsonProperty("type") String type,
			@JsonProperty("points") LinkedList<Point> points, @JsonProperty("isRelative") boolean isRelative,
			@JsonProperty("textlines") Map<String,TextLine> textlines) {
		super(id,points,isRelative);
		this.type = type;
		this.textlines = textlines;
	}

	public Region(MatOfPoint mat, String id, String type) {
		super(mat,id);
		this.type = type;
	}

	public Region(RegionSegment region) {
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

	public String getType() {
		return type;
	}
	
	public Map<String,TextLine> getTextlines() {
		return textlines;
	}
}

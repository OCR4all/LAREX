package com.web.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
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
	protected final String type;
	@JsonProperty("orientation")
	protected final Double orientation;
	@JsonProperty("textlines")
	protected final Map<String,TextLine> textlines;
	@JsonProperty("readingOrder")
	protected final List<String> readingOrder;

	@JsonCreator
	public Region(@JsonProperty("id") String id, @JsonProperty("type") String type,
			@JsonProperty("points") LinkedList<Point> points, @JsonProperty("orientation") Double orientation, @JsonProperty("isRelative") boolean isRelative,
			@JsonProperty("textlines") Map<String,TextLine> textlines, @JsonProperty("readingOrder") List<String> readingOrder) {
		super(id,points,isRelative);
		this.type = type;
		this.orientation = orientation;
		this.textlines = textlines;
		this.readingOrder = readingOrder;
	}

	public Region(LinkedList<Point> points, String id, String type) {
		super(points,id);
		this.type = type;
		this.orientation = null;
		this.textlines = new HashMap<>();
		this.readingOrder = new ArrayList<>();
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

		final MatOfPoint resultPoints = new MatOfPoint();
		resultPoints.fromList(points);

		return new RegionSegment(TypeConverter.stringToPAGEType(this.getType()), resultPoints, this.getId());
	}

	public String getType() {
		return type;
	}
	
	public Map<String,TextLine> getTextlines() {
		return textlines;
	}
	
	public Double getOrientation() {
		return orientation;
	}
	
	/**
	 * Get the reading order of the contained textlines
	 * @return
	 */
	public List<String> getReadingOrder() {
		return readingOrder;
	}
}

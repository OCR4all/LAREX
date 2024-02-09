package de.uniwue.web.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.opencv.core.MatOfPoint;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.uniwue.algorithm.geometry.regions.RegionSegment;
import de.uniwue.algorithm.geometry.regions.type.TypeConverter;

/**
 * A representation of a Segment or Region as Polygon that is parsed to the gui.
 * Contains positional points and type. Polygon can be created relative or
 * absolute. - A absolut Polygon is positioned via pixel (e.g. 10:10 ->
 * 10px:10px) - A relative Polygon is positioned via percentage (e.g. 0.1:0.1 ->
 * 10%:10%)
 *
 */
public class Region extends Element{

	@JsonProperty("type")
	protected final String type;
	@JsonProperty("orientation")
	protected final Double orientation;
	@JsonProperty("textlines")
	protected final Map<String,TextLine> textlines;
	@JsonProperty("readingDirection")
	protected final String readingDirection;
	@JsonProperty("readingOrder")
	protected List<String> readingOrder;

	@JsonProperty("comments")
	protected String comments;

	@JsonCreator
	public Region(@JsonProperty("id") String id,
				  @JsonProperty("type") String type,
				  @JsonProperty("orientation") Double orientation,
				  @JsonProperty("coords") Polygon coords,
				  @JsonProperty("textlines") Map<String,TextLine> textlines,
				  @JsonProperty("readingDirection") String readingDirection,
				  @JsonProperty("readingOrder") List<String> readingOrder,
				  @JsonProperty("comments") String comments) {
		super(id, coords);
		this.type = type;
		this.orientation = orientation;
		this.textlines = textlines;
		this.readingDirection = readingDirection;
		this.readingOrder = readingOrder;
		this.comments = comments;
	}

	public Region(String id,
				  String type,
				   Double orientation,
				  Polygon coords,
				  Map<String,TextLine> textlines,
				  List<String> readingOrder,
				  String comments) {
		super(id, coords);
		this.type = type;
		this.orientation = orientation;
		this.textlines = textlines;
		this.readingDirection = null;
		this.readingOrder = readingOrder;
		this.comments = comments;
	}

	public Region(String id, Polygon coords, String type) {
		super(id, coords);
		this.type = type;
		this.orientation = null;
		this.textlines = new HashMap<>();
		this.readingDirection = null;
		this.readingOrder = new ArrayList<>();
		this.comments = null;
	}

	/**
	 * Create a Larex RegionSegment from this polygon
	 *
	 * @return
	 */
	public RegionSegment toRegionSegment() {
		LinkedList<org.opencv.core.Point> points = new LinkedList<org.opencv.core.Point>();

		for (Point segmentPoint : this.coords.getPoints()) {
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

	public String getReadingDirection() { return readingDirection; }

	/**
	 * Get the reading order of the contained textlines
	 * @return
	 */
	public List<String> getReadingOrder() {
		return readingOrder;
	}

	/**
	 * Update the reading order of the contained textlines
	 * @param readingOrder
	 */
	public void setReadingOrder(List<String> readingOrder) {
		this.readingOrder = readingOrder;
	}

	/**
	 * Return the comments of the TextRegion
	 * @return
	 */
	public String getComments() {
		return this.comments;
	}

	/**
	 * Update the comments of the textline
	 * @param comments
	 */
	public void setComments(String comments) {
		this.comments = comments;
	}
}

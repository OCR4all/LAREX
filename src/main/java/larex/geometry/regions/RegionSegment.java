package larex.geometry.regions;

import java.util.ArrayList;
import java.util.UUID;

import org.opencv.core.MatOfPoint;

import larex.geometry.PointList;
import larex.geometry.regions.type.RegionType;

public class RegionSegment extends PointList{

	private RegionType type;

	public RegionSegment(RegionType type, MatOfPoint points) {
		this(type, points, UUID.randomUUID().toString());
	}

	public RegionSegment(RegionType type, MatOfPoint points, String id) {
		super(points,id);
		this.type = type;
	}

	public RegionSegment(RegionType type, ArrayList<java.awt.Point> points, String id) {
		super(points,id);
		this.type = type;
	}

	public RegionSegment(ArrayList<java.awt.Point> points, String id) {
		this(null, points, id);
	}

	/**
	 * Returns the a copy of this region with resized points
	 * 
	 * @param scaleFactor Prefered_Image_Height/Original_Image_Height
	 * @return The converted and scaled clone.
	 */
	public RegionSegment getResized(double scaleFactor) {
		return new RegionSegment(type, getResizedPoints(scaleFactor));
	}

	public RegionType getType() {
		return type;
	}

	public void setType(RegionType type) {
		this.type = type;
	}
}
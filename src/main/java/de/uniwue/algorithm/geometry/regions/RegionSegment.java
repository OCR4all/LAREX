package de.uniwue.algorithm.geometry.regions;

import java.util.ArrayList;
import java.util.UUID;

import org.opencv.core.MatOfPoint;
import org.opencv.core.Size;

import de.uniwue.algorithm.geometry.PointList;
import de.uniwue.algorithm.geometry.regions.type.PAGERegionType;

public class RegionSegment extends PointList{

	private PAGERegionType type;
	private String id;

	public RegionSegment(PAGERegionType type, final MatOfPoint points) {
		this(type, points, UUID.randomUUID().toString());
	}

	public RegionSegment(PAGERegionType type, final MatOfPoint points, String id) {
		super(points);
		this.id = id;
		this.type = type;
	}

	public RegionSegment(PAGERegionType type, ArrayList<java.awt.Point> points, String id) {
		super(points);
		this.id = id;
		this.type = type;
	}

	public RegionSegment(ArrayList<java.awt.Point> points, String id) {
		this(null, points, id);
	}

	/**
	 * Returns the a copy of this region with resized points
	 *
	 * @param scaleFactor Prefered_Image_Height/Original_Image_Height
	 * @param origDimensions Original dimension of the image. Used to prevent overflow
	 * @return The converted and scaled clone.
	 */
	public RegionSegment getResized(double scaleFactor, Size origDimensions) {
		return new RegionSegment(type, getResizedPoints(scaleFactor, origDimensions));
	}

	public String getId(){
		return id;
	}

	public PAGERegionType getType() {
		return type;
	}

	public void setType(PAGERegionType type) {
		this.type = type;
	}
}
